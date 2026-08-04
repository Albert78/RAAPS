package de.dh.raaps.core.aps

import android.content.Context
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.core.repository.TreatmentRepository
import de.dh.raaps.core.system.SystemWakeService
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

/**
 * APS system facade for the access from outside (UI, ...).
 * Manages threading and plugin lifecycles, ensuring all calls to the core are serialized
 * on a single background thread.
 */
class APS(
    val glucoseSourceManager: GlucoseSourceManager,
    val treatmentRepository: TreatmentRepository,
    val therapyManager: TherapyManager,
    val systemManager: SystemManager,
    val wakeService: SystemWakeService,
    val timeService: TimeService,
    val appPreferencesRepository: AppPreferencesRepository,
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    val context: Context
) {
    // Threading: Single background thread to avoid race conditions in the core logic
    private val apsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val apsScope = CoroutineScope(apsDispatcher + SupervisorJob())

    // Computation Core: Pure logic and state, completely thread-agnostic
    private val core: Core = Core.createProductiveCore(
        therapyManager = therapyManager,
        treatmentRepository = treatmentRepository,
        timeService = timeService,
        carbsInsulinCalculationModel = carbsInsulinCalculationModel,
        glucoseSourceManager = glucoseSourceManager,

        onDataUpdated = { /* Now handled by GSM directly */ },
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

    private val _coreState = MutableStateFlow<CoreState>(CoreState.Initializing)
    /**
     * State of the core.
     * Watch the core state to be notified when it changes, e.g.:
     * ```
     * aps.coreState.first { it is CoreState.Active }
     * ```
     */
    val coreState: StateFlow<CoreState> = _coreState.asStateFlow()

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
     * Gracefully stops the APS system and releases all background resources.
     */
    fun stop() {
        apsScope.cancel()
        apsDispatcher.close()
    }

    companion object {
        private val TAG = APS::class.simpleName

        const val WAKE_TAG = "APS"
    }
}