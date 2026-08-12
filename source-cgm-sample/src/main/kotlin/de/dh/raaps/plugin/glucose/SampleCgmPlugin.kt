package de.dh.raaps.plugin.glucose

import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.Plugin
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.RawBg
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.screens.systemcontrol.CgmPluginUiProvider
import de.dh.raaps.ui.screens.systemcontrol.SectionHeader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class SampleCgmPlugin : GlucoseSource, Plugin, CgmPluginUiProvider {
    override val neededPermissions: Collection<String> = emptyList()

    override val name: String = "Sample CGM Plugin"

    override val dataProviderType: String = "CGM"

    override val readingsInterval: BgReadingsInterval
        get() = BgReadingsInterval.OneMinute

    override val readingsTimeDelay = Minutes(5)

    override fun getSensorTypeName() = "Dexcom G6"

    override fun initialize(pluginManager: PluginManager) {
        // Nothing to do
    }

    override fun start() {
        // Nothing to do
    }

    override fun stop() {
        // Nothing to do
    }

    @Composable
    override fun CgmControlSection() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionHeader(title = "Sample CGM Extra Info")
                Text(
                    text = "This content is provided by the Sample CGM Plugin.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Plugin Version: 1.0.0-sample",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    fun getRawGlucoseReadings(): Flow<RawBg> = flow {
        while (true) {
            val reading = RawBg(
                value = BgValue.fromMgDl(100 + Random.nextInt(-10, 10)),
                timestamp = Timestamp(System.currentTimeMillis()),
            )
            emit(reading)
            delay((1000*60).milliseconds) // Emit minute for demo purposes
        }
    }

    override fun getValues(): Flow<BgReading> {
        return getRawGlucoseReadings().map { raw ->
            sampleMapRawValues(raw)
        }
    }

    private fun sampleMapRawValues(raw: RawBg): BgReading {
        // Sample decoding for raw values
        val kind = when (raw.value.mgdl.toInt()) {
            39 -> BgSampleKind.Low
            401 -> BgSampleKind.High
            0 -> BgSampleKind.Invalid
            else -> BgSampleKind.Value
        }
        return BgReading(
            value = raw.value,
            sampleKind = kind,
            timestamp = raw.timestamp
        )
    }
}
