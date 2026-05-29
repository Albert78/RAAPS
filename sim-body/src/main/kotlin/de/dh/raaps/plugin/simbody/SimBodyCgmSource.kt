package de.dh.raaps.plugin.simbody

import android.app.Application
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.RawBg
import de.dh.raaps.common.model.data.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

class SimBodyCgmSource(
    val bodyModel: BodyModel,
    val application: Application
): GlucoseSource {
    override val name: String = "Sim Body CGM Plugin"

    override val dataProviderType: String = "CGM"

    override val readingsInterval: BgReadingsInterval
        get() = BgReadingsInterval.FiveMinutes

    override val readingsTimeDelay = Minutes(5)

    override fun getSensorTypeName() = "Sim Body Dexcom G6 Plugin"

    override fun start() {
        // Nothing to do
    }

    override fun stop() {
        // Nothing to do
    }

    fun getRawGlucoseReadings(): Flow<RawBg> = flow {
        while (true) {
            val reading = RawBg(
                value = BgValue.fromMgDl(100 + Random.nextInt(-10, 10)),
                timestamp = Timestamp(System.currentTimeMillis()),
            )
            emit(reading)
            delay(1000*60*5) // Every 5 minutes
        }
    }

    override fun getValues(): Flow<BgReading> {
        return getRawGlucoseReadings().map { raw ->
            sampleMapRawValues(raw)
        }
    }

    private fun sampleMapRawValues(raw: RawBg): BgReading {
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