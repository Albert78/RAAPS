package de.dh.raaps.plugin.glucose.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.RawBg
import de.dh.raaps.common.model.data.Timestamp
import kotlin.math.round
import kotlin.time.Duration.Companion.days

class DataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        processIntent(context, intent)
    }

    fun processIntent(context: Context, intent: Intent) {
        val bundle = intent.extras ?: return

        val pluginInstance = ReceiverGlucosePlugin.instance ?: return
        when (intent.action) {
            Intents.XDRIP_ACTION_BG_ESTIMATE -> {
                // Sanity check: Don't receive values from source which is not configured
                if (pluginInstance.externalSourceType != ExternalSourceType.xDrip1Min
                    && pluginInstance.externalSourceType != ExternalSourceType.xDrip5Min)
                    return

                val timestampMs = bundle.getLong(Intents.XDRIP_EXTRA_TIMESTAMP, 0)
                val valueMgDl = round(bundle.getDouble(Intents.XDRIP_EXTRA_BG_ESTIMATE, 0.0))
                val raw = round(bundle.getDouble(Intents.XDRIP_EXTRA_RAW, 0.0))
                val sourceSensorName = bundle.getString(Intents.XDRIP_DATA_SOURCE)

                val now = System.currentTimeMillis()
                var sensorStartTime: Long? = bundle.getLong(Intents.XDRIP_EXTRA_SENSOR_STARTED_AT, 0)
                // check start time validity
                sensorStartTime?.let {
                    if (it == 0L || it > now || now - it > 30.days.inWholeMilliseconds) {
                        sensorStartTime = null
                    }
                }
                val rawValue = RawBg(
                    BgValue.fromMgDl(valueMgDl.toInt()),
                    Timestamp(timestampMs)
                )
                fun mapRawXDripValues(raw: RawBg): BgReading {
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
                if (timestampMs > 0) {
                    pluginInstance.injectReading(mapRawXDripValues(rawValue))
                }
            }
        }
    }
}