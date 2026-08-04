package de.dh.raaps.core.aps

import android.content.Context
import android.content.Intent
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.TherapyRepository
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
 * APS system facade for the access from outside (UI, ...).
 * Manages threading and plugin lifecycles, ensuring all calls to the core are serialized
 * on a single background thread.
 */
class APS(
    val glucoseRepository: GlucoseRepository,
    val therapyRepository: TherapyRepository,
    val treatmentRepository: TreatmentRepository,
    val appPreferencesRepository: AppPreferencesRepository,
    val therapyManager: TherapyManager,
    val systemManager: SystemManager,
    val wakeService: SystemWakeService,
    val timeService: TimeService,
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    val context: Context,
    val glucoseSourceManager: GlucoseSourceManager
) : WakeupHandler {
    // Threading: Single background thread to avoid race conditions in the core logic
    private val apsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val apsScope = CoroutineScope(apsDispatcher + SupervisorJob())

    init {
        wakeService.registerHandler(WAKE_TAG, this)
        glucoseSourceManager.setThreading(scope = apsScope, inAPSThread = { block -> inAPSThread(block) })
        glucoseSourceManager.setOnNewBg { bg -> updateBg(bg) }
    }

    // Computation Core: Pure logic and state, completely thread-agnostic
    private val core: Core = Core.createProductiveCore(
        therapyManager = therapyManager,
        glucoseRepository = glucoseRepository,
        treatmentRepository = treatmentRepository,
        appPreferencesRepository = appPreferencesRepository,
        timeService = timeService,
        carbsInsulinCalculationModel = carbsInsulinCalculationModel,
        glucoseSourceManager = glucoseSourceManager,

        onDataUpdated = { /* Now handled by GSM directly */ },
        onCoreStateChanged = { emitCoreStateChangedEvent() },
        onAcquireBusyState = { acquireBusyState() },
        onReleaseBusyState = { releaseBusyState() },

        onCancelInsulinJobs = { therapyManager.coreCancelInsulinJobs() },
        onDeliverBolus = { amount -> therapyManager.issueBolus(amount) },
        onCheckSetTemp = { therapyManager.canSetTemp() },
        onSetTempBasal = { durationInHours, unitsPerHour -> therapyManager.setTempBasal(durationInHours, unitsPerHour) },
        onCarbsHint = { amountInGram -> therapyManager.recommendCarbs(amountInGram) },
        onWaitForAndResetInsulinJobs = { therapyManager.waitForAndResetInsulinJobs() }
    )

    private val _coreState = MutableStateFlow<CoreState>(CoreState.Initializing)
    /**
     * State of the core.
     * Watch the core state to be notified when it changes, e.g.:
     * ```
     * aps.coreState.first { it is CoreState.Active }
     * ```
     */
    val coreState: StateFlow<CoreState> = _coreState.asStateFlow()

    private val _apsIssues = MutableStateFlow<Set<ApsIssue>>(emptySet())
    /**
     * Active issues of the APS.
     */
    val apsIssues: StateFlow<Set<ApsIssue>> = _apsIssues.asStateFlow()

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

    /**
     * Executes the given block on the internal APS thread.
     */
    private fun inAPSThread(block: suspend CoroutineScope.() -> Unit): Job {
        return apsScope.launch {
            block()
        }
    }

    /**
     * Executes the given block in a thread of the default dispatcher for
     * async executions of outgoing events.
     */
    private fun inExternalDispatcher(block: suspend CoroutineScope.() -> Unit): Job {
        return apsScope.launch(Dispatchers.Default) {
            block()
        }
    }

    /**
     * Starts the initialization of the APS core asynchronously.
     * See [coreState].
     */
    fun startInitialization() {
        inAPSThread {
            glucoseSourceManager.initialize()
            core.initialize()

            launch {
                systemManager.apsMode.collect { mode ->
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
                    core.onMetabolicEventsChanged()
                }
            }
            launch {
                treatmentRepository.observeInsulinApplications().drop(1).collect { data ->
                    core.onMetabolicEventsChanged()
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

    private fun emitCoreStateChangedEvent() = inExternalDispatcher {
        _coreState.emit(core.coreState)
    }


    /**
     * Entry point for external BG updates.
     * Guaranteed to run on the internal APS thread.
     */
    fun updateBg(bg: BgReading) = inAPSThread {
        therapyManager.clearRecommendations()

        core.updateBg(bg)
        core.nextBgStaleCheckAt()?.let {
            wakeService.scheduleWakeup(WAKE_TAG, WAKEUP_STALE_CHECK, it)
        }
    }

    /**
     * Entry point for system wakeups.
     * Guaranteed to run on the internal APS thread.
     */
    override fun onWakeup(wakeupId: UInt?, intent: Intent?) {
        inAPSThread {
            if (wakeupId == WAKEUP_STALE_CHECK) {
                if (glucoseSourceManager.isBgStale()) {
                    addIssue(ApsIssue.StaleBG)
                } else {
                    removeIssue(ApsIssue.StaleBG)
                }
            }
        }
    }

    /**
     * Gracefully stops the APS system and releases all background resources.
     */
    fun stop() {
        apsScope.cancel()
        apsDispatcher.close()
    }

    companion object {
        private val TAG = APS::class.simpleName

        const val WAKE_TAG = "APS"

        val WAKEUP_STALE_CHECK = 0u
    }
}