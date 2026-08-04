package de.dh.raaps.core.aps

import android.content.Context
import android.content.Intent
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.SettingsRepository
import de.dh.raaps.core.repository.TreatmentRepository
import de.dh.raaps.core.system.SystemWakeService
import de.dh.raaps.core.system.WakeupHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

enum class ApsIssue {
    /**
     * No recent glucose value available, the loop cannot calculate new treatments.
     */
    StaleBG,

    /**
     * Any other issue that prevents the core from working.
     */
    Other
}

/**
 * Manages the application mode, specifically the [ApsMode].
 * Handles persistence and provides a reactive state for other components to observe.
 */
interface SystemManager {
    /**
     * The current APS mode.
     */
    val apsMode: StateFlow<ApsMode>

    /**
     * Active issues of the APS.
     */
    val apsIssues: StateFlow<Set<ApsIssue>>

    /**
     * State of the core loop.
     */
    val coreState: StateFlow<CoreState>

    /**
     * Updates the APS mode and persists the change.
     */
    fun setApsMode(mode: ApsMode)

    /**
     * Starts the initialization of the system core.
     */
    fun startInitialization(
        treatmentRepository: TreatmentRepository,
        therapyManager: TherapyManager,
        appPreferencesRepository: AppPreferencesRepository,
        carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
        context: Context
    )

    /**
     * Gracefully stops the system.
     */
    fun stop()
}

/**
 * Implementation of [SystemManager].
 */
class SystemManagerImpl(
    private val glucoseSourceManager: GlucoseSourceManager,
    private val wakeService: SystemWakeService,
    private val settingsRepository: SettingsRepository,
    private val timeService: TimeService,
    private val scope: CoroutineScope
) : SystemManager, WakeupHandler {
    // Threading: Single background thread to avoid race conditions in the core logic
    private val coreDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val coreScope = CoroutineScope(coreDispatcher + SupervisorJob())

    private val _apsMode = MutableStateFlow(ApsMode.Suspend)
    override val apsMode: StateFlow<ApsMode> = _apsMode.asStateFlow()

    private val _apsIssues = MutableStateFlow<Set<ApsIssue>>(emptySet())
    override val apsIssues: StateFlow<Set<ApsIssue>> = _apsIssues.asStateFlow()

    private val _coreState = MutableStateFlow<CoreState>(CoreState.Initializing)
    override val coreState: StateFlow<CoreState> = _coreState.asStateFlow()

    // Computation Core: Pure logic and state, completely thread-agnostic
    private lateinit var core: Core

    init {
        wakeService.registerHandler(WAKE_TAG, this)

        scope.launch {
            settingsRepository.observeCurrentSettings().collect { settings ->
                if (settings != null) {
                    _apsMode.value = settings.apsMode
                }
            }
        }

        scope.launch {
            glucoseSourceManager.currentBg.drop(1).collect { bg ->
                if (bg != null) {
                    // Schedule stale check for the next window
                    val nextCheck = nextBgStaleCheckAt()
                    wakeService.scheduleWakeup(WAKE_TAG, WAKEUP_STALE_CHECK, nextCheck)

                    // Synchronize our internal ticking grid to fire 20s after the BG reading.
                    timeService.synchronize(Timestamp.now().plusSeconds(20))
                }
            }
        }
    }

    /**
     * Executes the given block on the internal core thread.
     */
    private fun inCoreThread(block: suspend CoroutineScope.() -> Unit): Job {
        return coreScope.launch {
            block()
        }
    }

    /**
     * Executes the given block in a thread of the default dispatcher for
     * async executions of outgoing events.
     */
    private fun inExternalDispatcher(block: suspend CoroutineScope.() -> Unit): Job {
        return coreScope.launch(Dispatchers.Default) {
            block()
        }
    }

    override fun startInitialization(
        treatmentRepository: TreatmentRepository,
        therapyManager: TherapyManager,
        appPreferencesRepository: AppPreferencesRepository,
        carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
        context: Context
    ) {
        core = Core.createProductiveCore(
            therapyManager = therapyManager,
            treatmentRepository = treatmentRepository,
            timeService = timeService,
            carbsInsulinCalculationModel = carbsInsulinCalculationModel,
            glucoseSourceManager = glucoseSourceManager,

            onCoreStateChanged = { emitCoreStateChangedEvent() },
            onAcquireBusyState = { acquireBusyState() },
            onReleaseBusyState = { releaseBusyState() },

            onCancelInsulinJobs = { therapyManager.coreCancelInsulinJobs() },
            onDeliverBolus = { amount -> therapyManager.issueBolus(amount) },
            onSetTempBasal = { durationInHours, unitsPerHour -> therapyManager.setTempBasal(durationInHours, unitsPerHour) },
            onCarbsHint = { amountInGram -> therapyManager.recommendCarbs(amountInGram) },
            onClearRecommendations = { therapyManager.clearRecommendations() },
            onWaitForAndResetInsulinJobs = { therapyManager.waitForAndResetInsulinJobs() }
        )

        inCoreThread {
            core.initialize()

            launch {
                apsMode.collect { mode ->
                    if (mode == ApsMode.AutoCorrection) {
                        core.activate()
                    } else {
                        core.suspend()
                    }
                }
            }
            launch {
                therapyManager.currentTherapySettingsFlow.drop(1).collect { settings ->
                    core.onTherapySettingsChanged(settings)
                }
            }
            launch {
                treatmentRepository.observeMeals().drop(1).collect { data ->
                    core.onMealsChanged()
                }
            }
            launch {
                treatmentRepository.observeInsulinApplications().drop(1).collect { data ->
                    core.onInsulinChanged()
                }
            }
        }
    }

    private fun acquireBusyState() {
        wakeService.acquireBusyState(APS_WAKE_TAG)
    }

    private fun releaseBusyState() {
        wakeService.releaseBusyState(APS_WAKE_TAG)
    }

    private fun emitCoreStateChangedEvent() = inExternalDispatcher {
        _coreState.emit(core.coreState)
    }

    override fun stop() {
        coreScope.cancel()
        coreDispatcher.close()
    }

    override fun setApsMode(mode: ApsMode) {
        _apsMode.value = mode
        scope.launch {
            val currentSettings = settingsRepository.getCurrentSettings()
            if (currentSettings != null) {
                settingsRepository.updateCurrentSettings(currentSettings.copy(apsMode = mode))
            }
        }
    }

    override fun onWakeup(wakeupId: UInt?, intent: Intent?) {
        if (wakeupId == WAKEUP_STALE_CHECK) {
            if (isBgStale()) {
                addIssue(ApsIssue.StaleBG)
            } else {
                removeIssue(ApsIssue.StaleBG)
            }
        }
    }

    private fun addIssue(issue: ApsIssue) {
        if (issue !in _apsIssues.value) {
            _apsIssues.value += issue
        }
    }

    private fun removeIssue(issue: ApsIssue) {
        if (issue in _apsIssues.value) {
            _apsIssues.value -= issue
        }
    }

    fun isBgStale(): Boolean {
        val lastDataTime = glucoseSourceManager.getLastDataTime()
        return lastDataTime == null || lastDataTime + STALE_BG_THRESHOLD < Timestamp.now()
    }

    private fun nextBgStaleCheckAt(): Timestamp {
        val lastDataTime = glucoseSourceManager.getLastDataTime()
        return (lastDataTime ?: Timestamp.now()) + STALE_BG_THRESHOLD
    }

    companion object {
        const val WAKE_TAG = "SystemManager"
        const val APS_WAKE_TAG = "APS"
        const val WAKEUP_STALE_CHECK = 0u
    }
}