package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.pump.PumpCoordinator
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

enum class CoreState {
    /**
     * The APS core was created but not initialized yet. No data have been loaded from the DB
     * and the calculation modules have not been connected yet.
     */
    Uninitialized,

    /**
     * The APS core is being initialized. During this time, the data is not reliable yet.
     */
    Initializing,

    /**
     * The APS core data is valid and can be used.
     */
    Idle,

    /**
     * The APS core is currently calculating a new state. All data is valid in the meantime.
     */
    Calculating,

    /**
     * Either CGM input or pump connection is missing, APS Core is not able to do its work.
     * The work will be continued when the connection is available again.
     */
    ConnectionMissing,

    /**
     * The APS Core is suspended by the user.
     */
    Suspended,

    /**
     * The system is being shut down. No more calculation will take place anymore.
     */
    Shutdown,

    /**
     * The system is in an unrecoverable error. This is a fatal situation and should
     * hopefully not happen.
     */
    Error
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
    private val glucoseRepository: GlucoseRepository,
    private val therapyRepository: TherapyRepository,
    val treatmentRepository: TreatmentRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    val therapyManager: TherapyManager,
    val pumpCoordinator: PumpCoordinator,
    private val onDataUpdated: () -> Unit,
    private val onCoreStateChanged: () -> Unit,
    private val onAcquireBusyState: () -> Unit,
    private val onReleaseBusyState: () -> Unit
) {
    private var calculationAlgorithm: ApsAlgorithm? = null

    // State
    var currentBg: BgReading? = null
        private set
    var lastBg: BgReading? = null
        private set

    var coreState: CoreState = CoreState.Uninitialized
        private set

    /**
     * Time delay between a glucose value in blood and the given Timestamp of the bg reading.
     * Typically, the bg reading timestamp represents the time of measure of the CGM system, which
     * is about 5 minutes behind blood glucose.
     */
    var glucoseReadingsTimeDelay: Minutes = Minutes(5)

    /**
     * Lock which is acquired in situations where a suspend function
     * must be atomic. Other (non-suspend) functions don't need to be locked since
     * we're single-threaded inside this class by design.
     */
    private val atomicOperationLock = Mutex()

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
        return atomicOperationLock.withLock {
            block()
        }
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

                treatmentRepository.load()

                // Not so nice, in fact, the readings history is part of the ApsAlgorithmImpl and should
                // be built there. But for the moment, I want to keep ApsAlgorithmImpl free of dataRepository,
                // lets see if we change that in the future.
                val readingsHistory = glucoseRepository.loadBgReadings(from = Timestamp.now().minus(
                    ApsAlgorithmImpl.DEVIATION_TIME_BASE))

                currentBg = readingsHistory.lastOrNull()
                lastBg = if (readingsHistory.size >= 2) readingsHistory[readingsHistory.size - 2] else null

                calculationAlgorithm = ApsAlgorithmImpl.create(
                    treatmentRepository,
                    readingsHistory,
                    therapyManager,
                    pumpCoordinator = pumpCoordinator,
                    tickInterval = TICK_INTERVAL
                )
                onDataUpdated()

                Log.d(TAG, "Finished initialization...")
                setCoreState(CoreState.Idle)
            }
        }
    }

    /**
     * Installs the input Flow of BG values from the given source plugin.
     */
    suspend fun installGlucosePipeline(
        plugin: GlucoseSource,
        dataProvider: DataProvider,
        sensorType: SensorType
    ) {
        Log.d(TAG, "Installing glucose pipeline")

        val readingsInterval = plugin.readingsInterval
        val datasourceTimeDelay = plugin.readingsTimeDelay
        // TODO: Save changes in readings interval (5 minutes to 1 minutes and vice versa) to database

        glucoseReadingsTimeDelay = datasourceTimeDelay

        // Persist values
        val persistedValues = plugin.getValues()
            .persist(glucoseRepository, dataProvider, sensorType)

        // Collect for core calculation
        persistedValues
            // Threading notice:
            // The .collect call will block our coroutine, so it must be the last action in this method.
            // But since we're in a coroutine, the call won't block our (single) thread while
            // waiting for new values; instead, it will just suspend and free the thread for other work.
            .collect { bg ->
                updateBg(bg)
            }
    }

    /**
     * Processes a new glucose reading. This is called from the glucose readings pipeline but
     * can also be called from outside to provide an additional bg value, e.g. from a bloody measure.
     */
    suspend fun updateBg(bg: BgReading) {
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

                val isRecent = abs(bg.timestamp.ms - Timestamp.now().ms) < RECENT_BG_THRESHOLD.inMs()
                val alg = calculationAlgorithm
                if (isRecent && alg != null) {
                    setCoreState(CoreState.Calculating)
                    alg.recalculateForNewBgValue(bg)
                    setCoreState(CoreState.Idle)
                }
            }
            onDataUpdated()
        }
    }

    companion object {
        val TAG = Core::class.simpleName

        const val METABOLIC_EVENTS_HISTORY_HOURS = 10
        const val TICK_INTERVAL_MINUTES: Short = 5
        val TICK_INTERVAL = Minutes(TICK_INTERVAL_MINUTES)

        fun createProductiveCore(
            glucoseRepository: GlucoseRepository,
            therapyRepository: TherapyRepository,
            treatmentRepository: TreatmentRepository,
            appPreferencesRepository: AppPreferencesRepository,
            onDataUpdated: () -> Unit,
            onCoreStateChanged: () -> Unit,
            onAcquireBusyState: () -> Unit,
            onReleaseBusyState: () -> Unit
        ): Core {
            val therapyManager = TherapyManager(
                therapyRepository,
                appPreferencesRepository
            )
            val pumpCoordinator = PumpCoordinator.create(treatmentRepository)
            return Core(
                glucoseRepository = glucoseRepository,
                therapyRepository = therapyRepository,
                treatmentRepository = treatmentRepository,
                appPreferencesRepository = appPreferencesRepository,
                therapyManager = therapyManager,
                pumpCoordinator = pumpCoordinator,
                onDataUpdated = onDataUpdated,
                onCoreStateChanged = onCoreStateChanged,
                onAcquireBusyState = onAcquireBusyState,
                onReleaseBusyState = onReleaseBusyState
            )
        }
    }
}