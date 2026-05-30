package de.dh.raaps.core.aps

import android.content.Context
import android.os.PowerManager
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.pump.ApsPumpModel
import de.dh.raaps.core.repository.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * APS system facade for the access from outside (UI, ...).
 * Manages threading and plugin lifecycles, ensuring all calls to the core are serialized
 * on a single background thread.
 */
class APS(
    val dataRepository: DataRepository,
    val appPreferencesRepository: AppPreferencesRepository,
    val context: Context
) {
    // Threading: Single background thread to avoid race conditions in the core logic
    private val apsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val apsScope = CoroutineScope(apsDispatcher + SupervisorJob())

    private val recursiveBusyState: AtomicInteger = AtomicInteger(0)

    // Power Management
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "raaps:ApsCoreLock")

    // Computation Core: Pure logic and state, completely thread-agnostic
    private val core: Core = Core.createProductiveCore(
        dataRepository = dataRepository,
        appPreferencesRepository = appPreferencesRepository,
        onDataUpdated = { emitDataUpdateEvent() },
        onCoreStateChanged = { emitCoreStateChangedEvent() },
        onAcquireBusyState = { acquireBusyState() },
        onReleaseBusyState = { releaseBusyState() }
    )

    val therapyModel: TherapyModel get() = core.therapyModel
    val metabolicEventsModel: MetabolicEventsModel get() = core.metabolicEventsModel
    val pumpModel: ApsPumpModel get() = core.pumpModel

    // Plugins & Active Jobs
    private var glucoseJob: Job? = null
    var glucoseSource: GlucoseSource? = null
        set(value) {
            field?.stop()
            field = value
            field?.start()
            restartGlucosePipeline()
        }

    private var pumpJob: Job? = null
    var insulinPump: InsulinPump? = null
        set(value) {
            field?.stop()
            field = value
            field?.start()
            // TODO: restart calculation or subscription for pump
        }

    // Observers: Exposed from the internal core
    // Observers (Updated by the core, read by the facade/UI)
    private val _lastDataTime = MutableStateFlow<Timestamp>(Timestamp(0))
    val lastDataTime: StateFlow<Timestamp> = _lastDataTime.asStateFlow()

    private val _coreState = MutableStateFlow(CoreState.Initializing)
    /**
     * State of the core.
     * Watch the core state to be notified when it changes, e.g.:
     * ```
     * aps.coreState.first { it == APSCoreState.Idle }
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
        }
        restartGlucosePipeline()
    }

    private fun acquireBusyState() {
        recursiveBusyState.incrementAndGet()
        if (!wakeLock.isHeld) wakeLock.acquire(5_000)
    }

    private fun releaseBusyState() {
        val busyState = recursiveBusyState.decrementAndGet()
        if (busyState > 0) {
            // Still busy
            return
        }
        if (wakeLock.isHeld) {
            try {
                wakeLock.release()
            } catch (e: RuntimeException) {
                // Ignore if already released
            }
        }
    }

    private fun restartGlucosePipeline() {
        glucoseJob?.cancel() // Cancel old pipeline if one exists
        val plugin = glucoseSource ?: return

        glucoseJob = inAPSThread {
            installGlucosePipeline_ApsThread(plugin)
        }
    }

    private suspend fun installGlucosePipeline_ApsThread(plugin: GlucoseSource) {
        val sensorType = dataRepository.getOrCreateSensorTypeByName(plugin.getSensorTypeName())
        val dataProvider =
            dataRepository.getOrCreateDataProviderByName(plugin.name, plugin.dataProviderType)
        core.installGlucosePipeline(plugin, dataProvider, sensorType)
    }

    private fun emitDataUpdateEvent() = inExternalDispatcher {
        _lastDataTime.emit(Timestamp.now())
    }

    private fun emitCoreStateChangedEvent() = inExternalDispatcher {
        _coreState.emit(core.coreState)
    }

    /**
     * Entry point for external BG updates.
     * Guaranteed to run on the internal APS thread.
     */
    fun updateBg(bg: BgReading) = inAPSThread {
        core.updateBg(bg)
    }

    /**
     * Gracefully stops the APS system and releases all background resources.
     */
    fun stop() {
        glucoseSource?.let {
            it.stop()
            glucoseSource = null
        }
        insulinPump?.let {
            it.stop()
            insulinPump = null
        }
        apsScope.cancel()
        apsDispatcher.close()
    }

    fun getCurrentBg(): BgReading? {
        return core.currentBg
    }

    fun getLastBg(): BgReading? {
        return core.lastBg
    }
}