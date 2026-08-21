package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculator
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TickPriority
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.CoreInsightRepository
import de.dh.raaps.core.repository.TreatmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    data class Active(val issues: Set<CoreIssue> = emptySet()) : CoreState {
        constructor(vararg issues: CoreIssue) : this(issues.toSet())
    }

    /**
     * The APS Core is suspended by the user.
     */
    data object Suspended : CoreState

    /**
     * The system is being shut down. No more calculation will take place anymore.
     */
    data object Shutdown : CoreState
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
    private val carbsInsulinCalculator: CarbsInsulinCalculator,
    private val glucoseSourceManager: GlucoseSourceManager,

    private val onCoreStateChanged: () -> Unit,
    private val onAcquireBusyState: () -> Unit,
    private val onReleaseBusyState: () -> Unit,

    private val onDeliverBolus: suspend (treatmentLock: TreatmentLock, amount: InsulinAmount, handledDeferredBoluses: List<DeferredBolus>?) -> Unit,
    private val onSetTempBasal: (treatmentLock: TreatmentLock, durationInHours: Int, percent: Int) -> Unit,
    private val onClearTempBasal: (treatmentLock: TreatmentLock) -> Unit,
    private val onCarbsHint: (treatmentLock: TreatmentLock, Int) -> Unit,
    private val onClearRecommendations: (treatmentLock: TreatmentLock) -> Unit,

    private val onCancelInsulinJobs: (treatmentLock: TreatmentLock) -> Unit,
    private val onWaitForInsulinJobs: suspend (treatmentLock: TreatmentLock) -> Int,
    private val coreInsightRepository: CoreInsightRepository,
    private val scope: CoroutineScope
) : TickHandler {
    private var calculationAlgorithm: ApsAlgorithm = NoopAlgorithm()

    var coreState: CoreState = CoreState.Uninitialized
        private set

    private var isReadOnly: Boolean = true

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

    fun activate(isReadOnly: Boolean = false) {
        this.isReadOnly = isReadOnly
        setCoreState(CoreState.Active())
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
                    timeline = timeService.timeline,
                    carbsInsulinCalculator = carbsInsulinCalculator
                )

                timeService.registerTickHandler(TickPriority.APS, this@Core)

                Log.d(TAG, "Finished initialization...")
                setCoreState(CoreState.Suspended)
            }
        }
    }

    override suspend fun onTick(tick: Tick) {
        Log.d(TAG, "onTick: $tick")

        processCalculation()
    }

    internal suspend fun processCalculation() {
        if (coreState !is CoreState.Active) return

        busyWork {
            val now = Timestamp.now()
            val res = therapyManager.tryAcquire(TAG ?: "Core") { treatmentLock ->
                atomic {
                    try {
                        onClearRecommendations(treatmentLock)
                        val pendingCount = onWaitForInsulinJobs(treatmentLock)
                        if (pendingCount > 0) {
                            Log.w(
                                TAG,
                                "processCalculation: Insulin jobs were not executed! Cancelling $pendingCount pending jobs."
                            )
                            scope.launch {
                                coreInsightRepository.saveInsight(
                                    CoreInsight(
                                        timestamp = now,
                                        bgOriginal = BgValue.INVALID,
                                        bgFiltered = BgValue.INVALID,
                                        deviationPerTick = BgDelta.fromMgDl(0),
                                        iobAtPeak = InsulinAmount.ZERO,
                                        cobAtPeak = 0.0,
                                        predictedBgAtPeak = BgValue.INVALID,
                                        targetBg = BgValue.INVALID,
                                        isf = BgDelta.fromMgDl(0),
                                        cr = 0.0,
                                        reasoning = CoreReasoning.PENDING_PUMP_JOBS
                                    )
                                )
                            }
                            onCancelInsulinJobs(treatmentLock)
                        }

                        val result = calculationAlgorithm.recalculate()

                        result.metrics?.let { insight ->
                            scope.launch {
                                coreInsightRepository.saveInsight(insight)
                            }
                        }

                        if (!isReadOnly) {
                            if (result.carbsInGHint != null) {
                                onCarbsHint(treatmentLock, result.carbsInGHint)
                            }
                            if (result.tempBasal != null) {
                                onSetTempBasal(
                                    treatmentLock,
                                    result.tempBasal.durationInHours,
                                    result.tempBasal.percent
                                )
                            }
                            if (result.clearTempBasal) {
                                onClearTempBasal(treatmentLock)
                            }
                            if (result.bolus != null && result.bolus >= InsulinAmount.EPSILON) {
                                onDeliverBolus(
                                    treatmentLock,
                                    result.bolus,
                                    result.handledDeferredBoluses
                                )
                            }
                        }

                        // Core can be active and yet have issues. In this case, the user is notified
                        // about the issues (e.g. no BG values) but the algorithm will still be called.
                        // If the algorithm can recover from the issues, it will continue working
                        // and remove the issues.
                        setCoreState(CoreState.Active(result.coreIssues ?: emptySet()))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during core execution", e)
                        setCoreState(CoreState.Active(CoreIssue.InternalError(
                            formatErrorMessage("Error during core execution", e)
                        )))
                        scope.launch {
                            coreInsightRepository.saveInsight(
                                CoreInsight(
                                    timestamp = now,
                                    bgOriginal = BgValue.INVALID,
                                    bgFiltered = BgValue.INVALID,
                                    deviationPerTick = BgDelta.fromMgDl(0),
                                    iobAtPeak = InsulinAmount.ZERO,
                                    cobAtPeak = 0.0,
                                    predictedBgAtPeak = BgValue.INVALID,
                                    targetBg = BgValue.INVALID,
                                    isf = BgDelta.fromMgDl(0),
                                    cr = 0.0,
                                    reasoning = CoreReasoning.INTERNAL_ERROR
                                )
                            )
                        }
                    }
                }
            }
            if (res is LockResult.Busy) {
                Log.i(
                    TAG,
                    "Core skipping tick since therapy manager is busy (lock owner: ${res.owner})"
                )
                setCoreState(CoreState.Active(CoreIssue.TherapyLockBusy))

                scope.launch {
                    coreInsightRepository.saveInsight(
                        CoreInsight(
                            timestamp = now,
                            bgOriginal = BgValue.INVALID,
                            bgFiltered = BgValue.INVALID,
                            deviationPerTick = BgDelta.fromMgDl(0),
                            iobAtPeak = InsulinAmount.ZERO,
                            cobAtPeak = 0.0,
                            predictedBgAtPeak = BgValue.INVALID,
                            targetBg = BgValue.INVALID,
                            isf = BgDelta.fromMgDl(0),
                            cr = 0.0,
                            reasoning = CoreReasoning.THERAPY_LOCK_HELD
                        )
                    )
                }
            }
        }
    }

    /**
     * Triggered when the therapy settings (i.e. profile) has changed.
     */
    suspend fun onTherapySettingsChanged() {
        atomic {
            calculationAlgorithm.updateTherapySettings()
        }
    }

    /**
     * Triggered when the list of declared meals changes.
     */
    suspend fun onMealsChanged() {
        atomic {
            calculationAlgorithm.updateMeals()
        }
    }

    /**
     * Triggered when the list of insulin applications changes.
     */
    suspend fun onInsulinChanged() {
        atomic {
            calculationAlgorithm.updateInsulin()
        }
    }

    suspend fun getPredictedBg(timestamp: Timestamp): BgValue {
        return calculationAlgorithm.getPredictedBg(timestamp)
    }

    fun getBolusCorrectionCalculator(): BolusCorrectionCalculator {
        return calculationAlgorithm.getBolusCorrectionCalculator()
    }

    companion object {
        val TAG = Core::class.simpleName

        fun createProductiveCore(
            therapyManager: TherapyManager,
            treatmentRepository: TreatmentRepository,
            timeService: TimeService,
            carbsInsulinCalculator: CarbsInsulinCalculator,
            glucoseSourceManager: GlucoseSourceManager,

            onCoreStateChanged: () -> Unit,
            onAcquireBusyState: () -> Unit,
            onReleaseBusyState: () -> Unit,

            onDeliverBolus: suspend (treatmentLock: TreatmentLock, amount: InsulinAmount, handledDeferredBoluses: List<DeferredBolus>?) -> Unit,
            onSetTempBasal: (treatmentLock: TreatmentLock, durationInHours: Int, percent: Int) -> Unit,
            onClearTempBasal: (treatmentLock: TreatmentLock) -> Unit,
            onCarbsHint: (treatmentLock: TreatmentLock, amountInGram: Int) -> Unit,
            onClearRecommendations: (treatmentLock: TreatmentLock) -> Unit,
            onCancelInsulinJobs: (treatmentLock: TreatmentLock) -> Unit,
            onWaitForInsulinJobs: suspend (treatmentLock: TreatmentLock) -> Int,
            coreInsightRepository: CoreInsightRepository,
            scope: CoroutineScope
        ): Core {
            return Core(
                treatmentRepository = treatmentRepository,
                therapyManager = therapyManager,
                timeService = timeService,
                carbsInsulinCalculator = carbsInsulinCalculator,
                glucoseSourceManager = glucoseSourceManager,

                onCoreStateChanged = onCoreStateChanged,
                onAcquireBusyState = onAcquireBusyState,
                onReleaseBusyState = onReleaseBusyState,

                onDeliverBolus = onDeliverBolus,
                onSetTempBasal = onSetTempBasal,
                onClearTempBasal = onClearTempBasal,
                onCarbsHint = onCarbsHint,
                onClearRecommendations = onClearRecommendations,
                onCancelInsulinJobs = onCancelInsulinJobs,
                onWaitForInsulinJobs = onWaitForInsulinJobs,
                coreInsightRepository = coreInsightRepository,
                scope = scope
            )
        }
    }
}