package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TickPriority
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.util.PersistentLogger
import de.dh.raaps.core.repository.AlgorithmInsightRepository
import de.dh.raaps.core.repository.TreatmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface CoreState {
    /**
     * The APS core was created but not initialized yet. No data have been loaded from the DB
     * and the calculation modules have not been connected yet.
     */
    data object Uninitialized : CoreState

    /**
     * The APS core is being initialized. During this time, the data is not reliable yet.
     */
    data object Initializing : CoreState

    /**
     * The APS core is working and all data are valid.
     */
    data object Active : CoreState

    /**
     * The APS Core is suspended by the user.
     */
    data object Suspended : CoreState

    /**
     * The system is being shut down. No more calculation will take place anymore.
     */
    data object Shutdown : CoreState

    /**
     * The algorithm is blocked due to technical issues (e.g. missing values).
     */
    data class Blocked(val issue: AlgorithmIssue) : CoreState
}

/**
 * The computation core of the APS system.
 *
 * **Architecture**
 * This class is NOT thread-safe by itself and must be called from a controlled threading environment (like APS facade).
 *
 * **Android integration**
 * This class should remain as free as possible of workarounds for the Android system.
 * We only need to signal the internal calculation state by calling [onAcquireBusyState] and [onReleaseBusyState].
 * The surrounding app is responsible for acquiring a wake lock.
 */
class Core(
    val treatmentRepository: TreatmentRepository,
    val therapyManager: TherapyManager,
    private val timeService: TimeService,
    private val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    private val glucoseSourceManager: GlucoseSourceManager,

    private val onCoreStateChanged: () -> Unit,
    private val onAcquireBusyState: () -> Unit,
    private val onReleaseBusyState: () -> Unit,

    private val onCancelInsulinJobs: (treatmentLock: TreatmentLock) -> Unit,
    private val onDeliverBolus: suspend (treatmentLock: TreatmentLock, amount: InsulinAmount, handledDeferredBoluses: List<DeferredBolus>?) -> Unit,
    private val onSetTempBasal: (treatmentLock: TreatmentLock, durationInHours: Int, unitsPerHour: Double) -> Unit,
    private val onClearTempBasal: (treatmentLock: TreatmentLock) -> Unit,
    private val onCarbsHint: (treatmentLock: TreatmentLock, Int) -> Unit,
    private val onClearRecommendations: (treatmentLock: TreatmentLock) -> Unit,
    private val onWaitForInsulinJobs: suspend (treatmentLock: TreatmentLock) -> Boolean,
    private val algorithmInsightRepository: AlgorithmInsightRepository,
    private val scope: CoroutineScope
) : TickHandler {
    private var calculationAlgorithm: ApsAlgorithm = NoopAlgorithm()

    var coreState: CoreState = CoreState.Uninitialized
        private set

    /**
     * Lock which is acquired in situations where a suspend function
     * must be atomic. Other (non-suspend) functions don't need to be locked since
     * we're single-threaded inside this class by design.
     */
    private val atomicOperationLock = Mutex()
    private val atomicOperationOwner = Any()

    /**
     * Block marker for code blocks which need a wake lock in the system during their executions.
     * If blocks are not marked with this marker, the processor can go into sleep mode any time.
     */
    private suspend fun <T> busyWork(block: suspend () -> T): T {
        onAcquireBusyState()
        try {
            return block()
        } finally {
            onReleaseBusyState()
        }
    }

    /**
     * Executes the given block atomically, e.g. blocks our (single) thread from being reused
     * for other functions in case our block suspends.
     */
    private suspend fun <T> atomic(block: suspend () -> T): T {
        return if (atomicOperationLock.holdsLock(atomicOperationOwner)) {
            block()
        } else {
            atomicOperationLock.withLock(atomicOperationOwner) {
                block()
            }
        }
    }

    fun suspend() {
        setCoreState(CoreState.Suspended)
    }

    fun activate() {
        setCoreState(CoreState.Active)
    }

    private fun setCoreState(state: CoreState) {
        coreState = state
        onCoreStateChanged()
    }

    suspend fun initialize() {
        busyWork {
            atomic {
                Log.d(TAG, "Initializing...")
                setCoreState(CoreState.Initializing)

                calculationAlgorithm = ApsAlgorithmImpl.create(
                    treatmentRepository,
                    glucoseSourceManager.sampledBgReadings,
                    therapyManager,
                    onCancelInsulinJobs = onCancelInsulinJobs,
                    onDeliverBolus = { lock, amount, deferred ->
val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(System.currentTimeMillis()))
PersistentLogger.log("Core", "------------ onDeliverBolus Callback: Forwarding to create BOLUS at $time, amount=${amount.iu}")
                        onDeliverBolus(lock, amount, deferred)
                    },
                    onSetTempBasal = onSetTempBasal,
                    onClearTempBasal = onClearTempBasal,
                    onCarbsHint = onCarbsHint,
                    onAlgorithmInsight = { insight ->
                        scope.launch {
                            algorithmInsightRepository.saveInsight(insight)
                        }
                    },
                    tickInterval = timeService.tickInterval,
                    carbsInsulinCalculationModel = carbsInsulinCalculationModel
                )

PersistentLogger.log("Core", "------------ calling registerTickHandler: priority=${TickPriority.APS}, handler=Core")
                timeService.registerTickHandler(TickPriority.APS, this@Core)

                Log.d(TAG, "Finished initialization...")
                setCoreState(CoreState.Suspended)
            }
        }
    }

    override suspend fun onTick(tick: Tick) {
        Log.d(TAG, "onTick: $tick")

        if (coreState !is CoreState.Active) return

        busyWork {
            val res = therapyManager.tryAcquire(TAG ?: "Core") { treatmentLock ->
                onClearRecommendations(treatmentLock)
                atomic {
                    val alg = calculationAlgorithm
                    if (!onWaitForInsulinJobs(treatmentLock)) {
                        Log.i(TAG, "onTick: Recalculation skipped: Insulin jobs are still pending.")
                        return@atomic
                    }
val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(System.currentTimeMillis()))
PersistentLogger.log("Core", "------------ onTick: Calling ApsAlgorithmImpl#recalculate to create BOLUS at $time")
                    val issues = alg.recalculate(treatmentLock)
                    if (issues.isNotEmpty()) {
                        setCoreState(CoreState.Blocked(issues.first()))
                    } else if (coreState is CoreState.Blocked) {
                        setCoreState(CoreState.Active)
                    }
                }
            }
            if (res is LockResult.Busy) {
                // TODO: Show popup to the user about skipping algorithm calculation
                Log.i(TAG, "Core skipping tick since therapy manager is busy (lock owner: ${res.owner})")
            }
        }
    }

    /**
     * Triggered when the therapy settings (i.e. profile) has changed.
     */
    suspend fun onTherapySettingsChanged(newData: CurrentTherapySettings?) {
        atomic {
            calculationAlgorithm.updateTherapySettings()
        }
    }

    /**
     * Triggered on meal events.
     */
    suspend fun onMealsChanged() {
        atomic {
            calculationAlgorithm.updateMeals()
        }
    }

    /**
     * Triggered on insulin events.
     */
    suspend fun onInsulinChanged() {
        atomic {
            calculationAlgorithm.updateInsulin()
        }
    }

    companion object {
        val TAG = Core::class.simpleName

        const val METABOLIC_EVENTS_HISTORY_HOURS = 10

        fun createProductiveCore(
            therapyManager: TherapyManager,
            treatmentRepository: TreatmentRepository,
            timeService: TimeService,
            carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
            glucoseSourceManager: GlucoseSourceManager,

            onCoreStateChanged: () -> Unit,
            onAcquireBusyState: () -> Unit,
            onReleaseBusyState: () -> Unit,

            onCancelInsulinJobs: (treatmentLock: TreatmentLock) -> Unit,
            onDeliverBolus: suspend (treatmentLock: TreatmentLock, amount: InsulinAmount, handledDeferredBoluses: List<DeferredBolus>?) -> Unit,
            onSetTempBasal: (treatmentLock: TreatmentLock, durationInHours: Int, unitsPerHour: Double) -> Unit,
            onClearTempBasal: (treatmentLock: TreatmentLock) -> Unit,
            onCarbsHint: (treatmentLock: TreatmentLock, amountInGram: Int) -> Unit,
            onClearRecommendations: (treatmentLock: TreatmentLock) -> Unit,
            onWaitForInsulinJobs: suspend (treatmentLock: TreatmentLock) -> Boolean,
            algorithmInsightRepository: AlgorithmInsightRepository,
            scope: CoroutineScope
        ): Core {
            return Core(
                treatmentRepository = treatmentRepository,
                therapyManager = therapyManager,
                timeService = timeService,
                carbsInsulinCalculationModel = carbsInsulinCalculationModel,
                glucoseSourceManager = glucoseSourceManager,

                onCoreStateChanged = onCoreStateChanged,
                onAcquireBusyState = onAcquireBusyState,
                onReleaseBusyState = onReleaseBusyState,

                onCancelInsulinJobs = onCancelInsulinJobs,
                onDeliverBolus = onDeliverBolus,
                onSetTempBasal = onSetTempBasal,
                onClearTempBasal = onClearTempBasal,
                onCarbsHint = onCarbsHint,
                onClearRecommendations = onClearRecommendations,
                onWaitForInsulinJobs = onWaitForInsulinJobs,
                algorithmInsightRepository = algorithmInsightRepository,
                scope = scope
            )
        }
    }
}
