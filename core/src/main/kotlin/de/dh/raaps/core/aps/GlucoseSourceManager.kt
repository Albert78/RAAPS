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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages the glucose source and the history of blood glucose readings.
 */
class GlucoseSourceManager(
    private val glucoseRepository: GlucoseRepository,
    private val timeService: TimeService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var glucoseJob: Job? = null

    private val _currentBg = MutableStateFlow<BgReading?>(null)
    val currentBg: StateFlow<BgReading?> = _currentBg.asStateFlow()

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

    suspend fun initialize() {
        val readings = glucoseRepository.loadBgReadings(from = Timestamp.now().minus(ApsAlgorithmImpl.DEVIATION_TIME_BASE))
        history.setAll(readings)

        val readingsHistory = history.toList()
        _currentBg.value = readingsHistory.lastOrNull()
        lastBg = if (readingsHistory.size >= 2) readingsHistory[readingsHistory.size - 2] else null
        sampledBgReadings.sampleAvgValues()
    }

    private fun restartGlucosePipeline() {
        glucoseJob?.cancel()
        val plugin = glucoseSource ?: return

        glucoseJob = scope.launch {
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

        persistedValues
            .collect { bg ->
                addReading(bg)
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
        scope.cancel()
    }

    companion object {
        private val TAG = GlucoseSourceManager::class.simpleName
    }
}