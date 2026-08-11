package de.dh.raaps.plugin.simbody

import android.app.Application
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.Minutes
import kotlinx.coroutines.flow.Flow

class SimBodyCgmSource(
    private val glucoseReadings: Flow<BgReading>
): GlucoseSource {
    override val name: String = "Sim Body CGM Plugin"

    override val dataProviderType: String = "CGM"

    override val readingsInterval: BgReadingsInterval
        get() = BgReadingsInterval.FiveMinutes

    override val readingsTimeDelay = DEFAULT_READINGS_DELAY

    override fun getSensorTypeName() = "Sim Body Dexcom G6 Plugin"

    override fun start() {
    }

    override fun stop() {
    }

    override fun getValues(): Flow<BgReading> {
        return glucoseReadings
    }

    companion object {
        val DEFAULT_READINGS_DELAY = Minutes(5)
    }
}
