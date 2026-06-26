package de.dh.raaps.plugin.pump

import de.dh.raaps.common.model.BasalHistoryPoint
import de.dh.raaps.common.model.BasalStatus
import de.dh.raaps.common.model.BolusHistoryPoint
import de.dh.raaps.common.model.HardwareInformation
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.InsulinPumpStatus
import de.dh.raaps.common.model.Plugin
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.PumpAlerts
import de.dh.raaps.common.model.PumpCapabilities
import de.dh.raaps.common.model.data.TherapyData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SampleInsulinPumpPlugin : InsulinPump, Plugin {
    // *************************** Plugin members ********************************

    override val neededPermissions: Collection<String> = emptyList()

    override val name: String = "Sample Pump Plugin"

    override fun initialize(pluginManager: PluginManager) {
        // Nothing to do
    }

    // *************************** Insulin pump members ********************************

    override val hardwareInformation: StateFlow<HardwareInformation?> = MutableStateFlow(
        HardwareInformation(
            manufacturer = "Sample Manufacturer",
            model = "Sample Model",
            serialNumber = "12345678",
            pumpDescription = "A sample insulin pump for testing purposes"
        )
    )
    override val pumpCapabilities: StateFlow<PumpCapabilities> = MutableStateFlow(
        PumpCapabilities(
            minBasalRate = 0.05,
            supportsZeroBasal = true,
            minBasalIncrement = 0.01,
            minBolusIncrement = 0.1,
            maxBolusSize = 25.0
        )
    )
    override val isConnected: StateFlow<Boolean> = MutableStateFlow(true)
    override val pumpStatus: StateFlow<InsulinPumpStatus> = MutableStateFlow(
        object : InsulinPumpStatus {
            override val pumpSuspended: Boolean = false
            override val batteryRemainingPercent: Int = 85
            override val reservoirRemainingUnits: Double = 120.5
            override val lastSyncTimestamp: Long = System.currentTimeMillis()
        }
    )
    override val alerts: StateFlow<PumpAlerts> = MutableStateFlow(PumpAlerts())
    override val basalStatus: StateFlow<BasalStatus> = MutableStateFlow(BasalStatus(activeRate = 0.5))
    override val basalHistory: StateFlow<List<BasalHistoryPoint>> = MutableStateFlow(emptyList())
    override val bolusHistory: StateFlow<List<BolusHistoryPoint>> = MutableStateFlow(emptyList())

    override suspend fun bolus(amount: Double) {
        // TODO: Implement bolus delivery
    }

    override suspend fun stopBolus() {
        // TODO: Stop ongoing bolus
    }

    override suspend fun tempBasal(percent: Int, durationHours: Int) {
        // TODO: Set temporary basal rate
    }

    override suspend fun cancelTempBasal() {
        // TODO: Cancel temporary basal rate
    }

    override suspend fun setProfile(profile: TherapyData) {
        // TODO: Set therapy profile
    }

    override suspend fun syncHistory() {
        // TODO: Sync pump history
    }

    override suspend fun refreshStatus() {
        // TODO: Refresh pump status from hardware
    }

    override fun stop() {
        // TODO
    }
}