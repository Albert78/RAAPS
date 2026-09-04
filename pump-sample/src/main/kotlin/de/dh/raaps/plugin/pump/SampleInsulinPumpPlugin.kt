package de.dh.raaps.plugin.pump

import de.dh.raaps.common.model.BasalStatus
import de.dh.raaps.common.model.BolusEvent
import de.dh.raaps.common.model.BolusStatus
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinConcentration
import de.dh.raaps.common.model.HardwareInformation
import de.dh.raaps.common.model.InsulinHistory
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.InsulinPumpStatus
import de.dh.raaps.common.model.Plugin
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.PumpAlerts
import de.dh.raaps.common.model.PumpCapabilities
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.screens.systemcontrol.PumpPluginUiProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class SampleInsulinPumpPlugin : InsulinPump, Plugin, PumpPluginUiProvider {
    // *************************** Plugin members ********************************

    override val neededPermissions: Collection<String> = emptyList()

    override val name: String = "Sample Pump Plugin"

    override fun initialize(pluginManager: PluginManager) {
        // Nothing to do
    }

    // *************************** Insulin pump members ********************************

    override var insulinConcentration: InsulinConcentration = InsulinConcentration.U100

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
            minBasalRate = InsulinAmount(0.05),
            supportsZeroBasal = true,
            minBasalIncrement = InsulinAmount(0.01),
            minBolusIncrement = InsulinAmount(0.1),
            maxBolusSize = InsulinAmount(25.0)
        )
    )
    override val isConnected: StateFlow<Boolean> = MutableStateFlow(true)
    override val pumpStatus: StateFlow<InsulinPumpStatus> = MutableStateFlow(
        object : InsulinPumpStatus {
            override val pumpSuspended: Boolean = false
            override val batteryRemainingPercent: Int = 85
            override val reservoirRemainingUnits: InsulinAmount = InsulinAmount(120.5)
            override val lastSyncTimestamp: Timestamp = Timestamp.now()
        }
    )
    override val alerts: StateFlow<PumpAlerts> = MutableStateFlow(PumpAlerts())
    override val basalStatus: StateFlow<BasalStatus> = MutableStateFlow(BasalStatus(activeRate = InsulinAmount(0.5)))
    override val bolusStatus: StateFlow<BolusStatus> = MutableStateFlow(BolusStatus())
    override val bolusEvents: SharedFlow<BolusEvent> = MutableSharedFlow()
    override val history: StateFlow<InsulinHistory?> = MutableStateFlow(null)

    override suspend fun bolus(amount: InsulinAmount, bolusId: String?) {
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

    override suspend fun setProfile(profile: InsulinProfile) {
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

    @Composable
    override fun PumpControlSection() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "This content is provided by the Sample Pump Plugin.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Maintenance status: Optimal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}