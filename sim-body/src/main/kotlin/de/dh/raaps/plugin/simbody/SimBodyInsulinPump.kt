package de.dh.raaps.plugin.simbody

import android.app.Application
import de.dh.raaps.common.model.BasalHistoryPoint
import de.dh.raaps.common.model.BasalStatus
import de.dh.raaps.common.model.BolusHistoryPoint
import de.dh.raaps.common.model.HardwareInformation
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.InsulinPumpStatus
import de.dh.raaps.common.model.PumpAlerts
import de.dh.raaps.common.model.PumpCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SimBodyInsulinPump(
    val bodyModel: BodyModel,
    val application: Application
): InsulinPump {
    override val hardwareInformation: StateFlow<HardwareInformation?> = MutableStateFlow(
        HardwareInformation(
            manufacturer = "RAAPS",
            model = "Simulator",
            serialNumber = "SIM-001",
            pumpDescription = "RAAPS Body Simulator Pump"
        )
    )

    override val pumpCapabilities: StateFlow<PumpCapabilities> = MutableStateFlow(
        PumpCapabilities(
            minBasalRate = 0.0,
            supportsZeroBasal = true,
            minBasalIncrement = 0.05,
            minBolusIncrement = 0.05,
            maxBolusSize = 25.0
        )
    )

    override val isConnected: StateFlow<Boolean> = MutableStateFlow(true)

    override val pumpStatus: StateFlow<InsulinPumpStatus>
        get() = TODO("Not yet implemented")
    override val alerts: StateFlow<PumpAlerts>
        get() = TODO("Not yet implemented")
    override val basalStatus: StateFlow<BasalStatus>
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
