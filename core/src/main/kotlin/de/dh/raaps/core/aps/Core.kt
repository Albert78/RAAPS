package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TickPriority
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.TreatmentRepository
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
    data object Active : CoreState

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
    private val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    private val glucoseSourceManager: GlucoseSourceManager,

    private val onDataUpdated: () -> Unit,
    private val onCoreStateChanged: () -> Unit,
    private val onAcquireBusyState: () -> Unit,
    private val onReleaseBusyState: () -> Unit,

    private val onCancelInsulinJobs: () -> Unit,
    private val onDeliverBolus: (amount: InsulinAmount) -> Unit,
    private val onCheckSetTemp: () -> Boolean,
    private val onSetTempBasal: (durationInHours: Int, unitsPerHour: Double) -> Unit,
    private val onCarbsHint: (Int) -> Unit,
    private val onWaitForAndResetInsulinJobs: suspend () -> Unit,
) : TickHandler {
    private var calculationAlgorithm: ApsAlgorithm = NoopAlgorithm()

    // State
    var currentBg: BgReading? = null
        private set
    var lastBg: BgReading? = null
        private set
    var isPredictionsStale: Boolean = true

    var coreState: CoreState = CoreState.Uninitialized
        private set

    suspend fun nextBgStaleCheckAt(): Timestamp? = glucoseSourceManager.nextBgStaleCheckAt()
    suspend fun isStale(): Boolean = glucoseSourceManager.isStale()

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

    override suspend fun onTick(tick: Tick) {
        if (coreState !is CoreState.Active) return

        Log.d(TAG, "onTick: $tick")

        if (isStale()) {
            // This will be handled by APS facade currently,
            // but we could also emit an event here.
        }
    }

    suspend fun initialize() {
        busyWork {
            atomic {
                Log.d(TAG, "Initializing...")
                setCoreState(CoreState.Initializing)

                val readingsHistory = glucoseSourceManager.history.toList()

                currentBg = readingsHistory.lastOrNull()
                lastBg = if (readingsHistory.size >= 2) readingsHistory[readingsHistory.size - 2] else null

                calculationAlgorithm = ApsAlgorithmImpl.create(
                    treatmentRepository,
                    therapyManager,
                    onCancelInsulinJobs = onCancelInsulinJobs,
                    onDeliverBolus = onDeliverBolus,
                    onCheckSetTemp = onCheckSetTemp,
                    onSetTempBasal = onSetTempBasal,
                    onCarbsHint = onCarbsHint,
                    tickInterval = timeService.tickInterval,
                    carbsInsulinCalculationModel = carbsInsulinCalculationModel
                )
                onDataUpdated()

                timeService.registerTickHandler(TickPriority.APS, this@Core)

                Log.d(TAG, "Finished initialization...")
                setCoreState(CoreState.Suspended)
            }
        }
    }

    /**
     * Processes a new glucose reading. This is called from the glucose readings pipeline but
     * can also be called from outside to provide an additional bg value, e.g. from a bloody measure.
     */
    suspend fun updateBg(bg: BgReading) {
        if (coreState !is CoreState.Active) return
        busyWork {
            if (bg.sampleKind == BgSampleKind.Invalid) {
                Log.d(TAG, "Skipping BG entry $bg because it has an invalid value")
                return@busyWork
            } else {
                Log.d(TAG, "New BG: $bg")
            }
            if (bg.timestamp.ms > Timestamp.now().ms + EARLY_BG_GUARD.inMs()) {
                Log.w(TAG, "Rejecting BG reading because it's timestamp is in the future (now() = ${Timestamp.now().ms}, BG timestamp = ${bg.timestamp.ms}")
                return@busyWork
            }
            atomic {
                if (currentBg == null || bg.timestamp >= currentBg!!.timestamp) {
                    lastBg = currentBg
                    currentBg = bg
                }

                val alg = calculationAlgorithm
                onWaitForAndResetInsulinJobs()
                glucoseSourceManager.sampledBgReadings.sampleAvgValues()
                alg.recalculate(glucoseSourceManager.sampledBgReadings)
            }
            onDataUpdated()
        }
    }

    /**
     * Triggered when the therapy settings (i.e. profile) has changed.
     */
    suspend fun onTherapySettingsChanged(newData: CurrentTherapySettings?) {
        // For the future... If we don't need this handler, we can remove it
    }

    /**
     * Triggered on insulin or meal events.
     */
    suspend fun onMetabolicEventsChanged() {
        atomic {
            calculationAlgorithm.updateMealsAndInsulin()
            isPredictionsStale = true
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

            onDataUpdated: () -> Unit,
            onCoreStateChanged: () -> Unit,
            onAcquireBusyState: () -> Unit,
            onReleaseBusyState: () -> Unit,

            onCancelInsulinJobs: () -> Unit,
            onDeliverBolus: (amount: InsulinAmount) -> Unit,
            onCheckSetTemp: () -> Boolean,
            onSetTempBasal: (durationInHours: Int, unitsPerHour: Double) -> Unit,
            onCarbsHint: (amountInGram: Int) -> Unit,
            onWaitForAndResetInsulinJobs: suspend () -> Unit,
        ): Core {
            return Core(
                treatmentRepository = treatmentRepository,
                therapyManager = therapyManager,
                timeService = timeService,
                carbsInsulinCalculationModel = carbsInsulinCalculationModel,
                glucoseSourceManager = glucoseSourceManager,

                onDataUpdated = onDataUpdated,
                onCoreStateChanged = onCoreStateChanged,
                onAcquireBusyState = onAcquireBusyState,
                onReleaseBusyState = onReleaseBusyState,

                onCancelInsulinJobs = onCancelInsulinJobs,
                onDeliverBolus = onDeliverBolus,
                onCheckSetTemp = onCheckSetTemp,
                onSetTempBasal = onSetTempBasal,
                onCarbsHint = onCarbsHint,
                onWaitForAndResetInsulinJobs = onWaitForAndResetInsulinJobs,
            )
        }
    }
}