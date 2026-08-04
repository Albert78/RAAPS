package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.GlucoseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the glucose source and the history of blood glucose readings.
 */
class GlucoseSourceManager(
    private val glucoseRepository: GlucoseRepository,
    private val timeService: TimeService
) {
    private var glucoseJob: Job? = null
    private var scope: CoroutineScope? = null
    private var inAPSThread: ((suspend CoroutineScope.() -> Unit) -> Job)? = null
    private var onNewBg: (suspend (BgReading) -> Unit)? = null

    private val _currentBg = MutableStateFlow<BgReading?>(null)
    val currentBg: StateFlow<BgReading?> = _currentBg.asStateFlow()

    private val _lastDataTime = MutableStateFlow<Timestamp>(Timestamp(0))
    val lastDataTime: StateFlow<Timestamp> = _lastDataTime.asStateFlow()

    var lastBg: BgReading? = null
        private set

    var glucoseSource: GlucoseSource? = null
        set(value) {
            field?.stop()
            field = value
            field?.start()
            restartGlucosePipeline()
        }

    val history = RecentBgReadingsHistory(ApsAlgorithmImpl.DEVIATION_TIME_BASE)
    val sampledBgReadings = SampledBgReadings(Timeline(timeService.tickInterval), history)

    /**
     * Time delay between a glucose value in blood and the given Timestamp of the bg reading.
     * Typically, the bg reading timestamp represents the time of measure of the CGM system, which
     * is about 5 minutes behind blood glucose.
     */
    var readingsTimeDelay: Minutes = Minutes(5)
        private set

    fun setThreading(scope: CoroutineScope, inAPSThread: (suspend CoroutineScope.() -> Unit) -> Job) {
        this.scope = scope
        this.inAPSThread = inAPSThread
    }

    fun setOnNewBg(onNewBg: suspend (BgReading) -> Unit) {
        this.onNewBg = onNewBg
    }

    suspend fun initialize() {
        val readings = glucoseRepository.loadBgReadings(from = Timestamp.now().minus(ApsAlgorithmImpl.DEVIATION_TIME_BASE))
        history.setAll(readings)

        val readingsHistory = history.toList()
        _currentBg.value = readingsHistory.lastOrNull()
        lastBg = if (readingsHistory.size >= 2) readingsHistory[readingsHistory.size - 2] else null
        sampledBgReadings.sampleAvgValues()
        _lastDataTime.value = Timestamp.now()
    }

    private fun restartGlucosePipeline() {
        glucoseJob?.cancel()
        val plugin = glucoseSource ?: return
        val inAPS = inAPSThread ?: return

        glucoseJob = inAPS {
            installGlucosePipeline(plugin)
        }
    }

    private suspend fun installGlucosePipeline(plugin: GlucoseSource) {
        Log.d(TAG, "Installing glucose pipeline")

        val sensorType = glucoseRepository.getOrCreateSensorTypeByName(plugin.getSensorTypeName())
        val dataProvider = glucoseRepository.getOrCreateDataProviderByName(plugin.name, plugin.dataProviderType)

        readingsTimeDelay = plugin.readingsTimeDelay

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
                addReading(bg)
                onNewBg?.invoke(bg)
                // Synchronize our internal ticking grid to fire 20s after the BG reading.
                timeService.synchronize(Timestamp.now().plusSeconds(20))
            }
    }

    fun addReading(reading: BgReading) {
        history.add(reading)

        if (_currentBg.value == null || reading.timestamp >= _currentBg.value!!.timestamp) {
            lastBg = _currentBg.value
            _currentBg.value = reading
        }

        sampledBgReadings.sampleAvgValues()
        _lastDataTime.value = Timestamp.now()
    }

    fun isBgStale(): Boolean {
        val lastReading = history.last()
        return lastReading != null && lastReading.timestamp + STALE_BG_THRESHOLD < Timestamp.now()
    }

    fun nextBgStaleCheckAt(): Timestamp {
        return Timestamp.now() + STALE_BG_THRESHOLD
    }

    fun stop() {
        glucoseSource?.stop()
        glucoseSource = null
        glucoseJob?.cancel()
    }

    companion object {
        private val TAG = GlucoseSourceManager::class.simpleName
    }
}