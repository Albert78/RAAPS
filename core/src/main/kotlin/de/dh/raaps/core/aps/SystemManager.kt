package de.dh.raaps.core.aps

import android.app.Notification
import android.content.Context
import android.content.Intent
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculator
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TickPriority
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.CoreInsightRepository
import de.dh.raaps.core.repository.SettingsRepository
import de.dh.raaps.core.repository.TreatmentRepository
import de.dh.raaps.core.system.AndroidNotifications
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
        carbsInsulinCalculator: CarbsInsulinCalculator,
        coreInsightRepository: CoreInsightRepository,
        context: Context
    )

    /**
     * Creates a notification for the foreground service.
     */
    fun createForegroundServiceNotification(): Notification

    /**
     * Gracefully stops the system.
     */
    fun stop()

    /**
     * Returns the predicted blood glucose value for the given timestamp.
     */
    suspend fun getPredictedBg(timestamp: Timestamp): BgValue

    /**
     * Returns the bolus correction calculator.
     */
    fun getBolusCorrectionCalculator(): BolusCorrectionCalculator
}

/**
 * Implementation of [SystemManager].
 */
class SystemManagerImpl(
    private val glucoseSourceManager: GlucoseSourceManager,
    private val wakeService: SystemWakeService,
    private val settingsRepository: SettingsRepository,
    private val timeService: TimeService,
    private val androidNotifications: AndroidNotifications,
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

    private var therapyManager: TherapyManager? = null
    private var treatmentRepository: TreatmentRepository? = null
    private var carbsInsulinCalculator: CarbsInsulinCalculator? = null

    private inner class NotificationTickHandler : TickHandler {
        override suspend fun onTick(tick: Tick) {
            androidNotifications.updateMainAppNotification(glucoseSourceManager)
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
        carbsInsulinCalculator: CarbsInsulinCalculator,
        coreInsightRepository: CoreInsightRepository,
        context: Context
    ) {
        this.therapyManager = therapyManager
        this.treatmentRepository = treatmentRepository
        this.carbsInsulinCalculator = carbsInsulinCalculator

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

                    // Update notification immediately
                    androidNotifications.updateMainAppNotification(glucoseSourceManager)
                }
            }
        }

        androidNotifications.createNotificationChannels()
        timeService.registerTickHandler(TickPriority.UI, NotificationTickHandler())

        scope.launch {
            appPreferencesRepository.glucoseUnit.drop(1).collect {
                androidNotifications.updateMainAppNotification(glucoseSourceManager)
            }
        }

        scope.launch {
            therapyManager.recommendations.collect { recommendations ->
                if (recommendations.isEmpty()) {
                    androidNotifications.cancelRecommendationNotification()
                } else {
                    androidNotifications.showRecommendationNotification(recommendations.first())
                }
            }
        }

        scope.launch {
            coreState.collect { state ->
                if (state is CoreState.Active && state.issues.isNotEmpty()) {
                    androidNotifications.showCoreIssueNotification(state.issues.first())
                } else {
                    androidNotifications.cancelCoreIssueNotification()
                }
            }
        }

        core = Core.createProductiveCore(
            therapyManager = therapyManager,
            treatmentRepository = treatmentRepository,
            timeService = timeService,
            carbsInsulinCalculator = carbsInsulinCalculator,
            glucoseSourceManager = glucoseSourceManager,

            onCoreStateChanged = { handleCoreStateChanged() },
            onAcquireBusyState = { acquireBusyState() },
            onReleaseBusyState = { releaseBusyState() },

            onDeliverBolus = { treatmentLock, amount, handledDeferredBoluses ->
                therapyManager.issueBolus(treatmentLock, amount, handledDeferredBoluses)
            },
            onSetTempBasal = { treatmentLock, durationInHours, percent -> therapyManager.setTempBasal(treatmentLock, durationInHours, percent) },
            onClearTempBasal = { treatmentLock -> therapyManager.clearTempBasal(treatmentLock) },
            onCarbsHint = { treatmentLock, amountInGram -> therapyManager.recommendCarbs(treatmentLock, amountInGram) },
            onClearRecommendations = { treatmentLock -> therapyManager.clearRecommendations(treatmentLock) },
            onCancelInsulinJobs = { treatmentLock -> therapyManager.coreCancelInsulinJobs(treatmentLock) },
            onWaitForInsulinJobs = { treatmentLock -> therapyManager.waitForInsulinJobs(treatmentLock) },
            coreInsightRepository = coreInsightRepository,
            scope = scope
        )

        inCoreThread {
            core.initialize()

            launch {
                apsMode.collect { mode ->
                    when (mode) {
                        ApsMode.AutoCorrection -> core.activate(isReadOnly = false)
                        ApsMode.BasalOnly -> core.activate(isReadOnly = true)
                        ApsMode.Suspend -> core.suspend()
                    }
                    if (mode != ApsMode.Suspend) {
                        core.processCalculation()
                    }
                }
            }
            launch {
                therapyManager.currentTherapySettingsFlow.drop(1).collect { _ ->
                    core.onTherapySettingsChanged()
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
        wakeService.acquireBusyState(WAKE_TAG)
    }

    private fun releaseBusyState() {
        wakeService.releaseBusyState(WAKE_TAG)
    }

    private fun handleCoreStateChanged() {
        inExternalDispatcher {
            _coreState.emit(core.coreState)
        }
    }

    override fun createForegroundServiceNotification(): Notification {
        return androidNotifications.createMainAppNotification(glucoseSourceManager)
    }

    override fun stop() {
        coreScope.cancel()
        coreDispatcher.close()
    }

    override suspend fun getPredictedBg(timestamp: Timestamp): BgValue {
        return if (::core.isInitialized) {
            core.getPredictedBg(timestamp)
        } else {
            BgValue.INVALID
        }
    }

    override fun getBolusCorrectionCalculator(): BolusCorrectionCalculator {
        val tm = therapyManager
        val tr = treatmentRepository
        val cic = carbsInsulinCalculator

        return if (::core.isInitialized) {
            core.getBolusCorrectionCalculator()
        } else if (tm != null && tr != null && cic != null) {
            SimpleBolusCorrectionCalculator(tm, glucoseSourceManager)
        } else {
            NoopAlgorithm().getBolusCorrectionCalculator()
        }
    }

    override fun setApsMode(mode: ApsMode) {
        _apsMode.value = mode
        // Trigger a tick to update predictions immediately
        timeService.synchronize(Timestamp.now())

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
        const val WAKEUP_STALE_CHECK = 0u
    }
}