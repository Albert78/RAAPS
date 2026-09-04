package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.Minutes
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
 * Manages the glucose source plugin and its pipeline.
 */
class GlucoseSourceManager(
    private val glucoseRepository: GlucoseRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var glucoseJob: Job? = null

    private val _glucoseSource = MutableStateFlow<GlucoseSource?>(null)
    val activeGlucoseSource: StateFlow<GlucoseSource?> = _glucoseSource.asStateFlow()

    var glucoseSource: GlucoseSource?
        get() = _glucoseSource.value
        set(value) {
            _glucoseSource.value?.stop()
            _glucoseSource.value = value
            _glucoseSource.value?.start()
            restartGlucosePipeline()
        }

    /**
     * Time delay between a glucose value in blood and the given Timestamp of the bg reading.
     * Typically, the bg reading timestamp represents the time of measure of the CGM system, which
     * is about 5 minutes behind blood glucose.
     */
    var readingsTimeDelay: Minutes = Minutes(5)
        private set

    var readingsInterval: BgReadingsInterval = BgReadingsInterval.FiveMinutes
        private set

    private fun restartGlucosePipeline() {
        glucoseJob?.cancel()
        val plugin = glucoseSource ?: return

        glucoseJob = scope.launch {
            Log.d(TAG, "Installing glucose pipeline: ${plugin.name}")

            val sensorType = glucoseRepository.getOrCreateSensorTypeByName(plugin.getSensorTypeName())
            val dataProvider = glucoseRepository.getOrCreateDataProviderByName(plugin.name, plugin.dataProviderType)

            readingsTimeDelay = plugin.readingsTimeDelay
            readingsInterval = plugin.readingsInterval

            // Persist values and update in-memory history in repository
            plugin.getValues()
                .collect { reading ->
                    glucoseRepository.addReading(reading, dataProvider, sensorType)
                }
        }
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