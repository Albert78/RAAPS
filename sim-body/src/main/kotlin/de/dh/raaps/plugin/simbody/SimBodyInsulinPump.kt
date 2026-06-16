package de.dh.raaps.plugin.simbody

import android.app.Application
import de.dh.raaps.common.model.BasalHistoryPoint
import de.dh.raaps.common.model.BasalStatus
import de.dh.raaps.common.model.BolusHistoryPoint
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.InsulinPumpStatus
import de.dh.raaps.common.model.PumpAlerts
import kotlinx.coroutines.flow.StateFlow

class SimBodyInsulinPump(
    val bodyModel: BodyModel,
    val application: Application
): InsulinPump {
    override val status: StateFlow<InsulinPumpStatus>
        get() = TODO("Not yet implemented")
    override val alerts: StateFlow<PumpAlerts>
        get() = TODO("Not yet implemented")
    override val basal: StateFlow<BasalStatus>
        get() = TODO("Not yet implemented")
    override val basalHistory: StateFlow<List<BasalHistoryPoint>>
        get() = TODO("Not yet implemented")
    override val bolusHistory: StateFlow<List<BolusHistoryPoint>>
        get() = TODO("Not yet implemented")

    override suspend fun bolus(amount: Double) {
        TODO("Not yet implemented")
    }

    override suspend fun stopBolus() {
        TODO("Not yet implemented")
    }

    override suspend fun tempBasal(percent: Int, durationHours: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun cancelTempBasal() {
        TODO("Not yet implemented")
    }

    override suspend fun syncHistory() {
        TODO("Not yet implemented")
    }

    override suspend fun refreshStatus() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        // TODO
    }
}