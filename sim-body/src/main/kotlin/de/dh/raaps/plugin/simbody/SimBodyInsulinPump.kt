package de.dh.raaps.plugin.simbody

import de.dh.raaps.common.model.BasalStatus
import de.dh.raaps.common.model.HardwareInformation
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinCategory
import de.dh.raaps.common.model.InsulinHistory
import de.dh.raaps.common.model.InsulinHistoryPoint
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.InsulinPumpStatus
import de.dh.raaps.common.model.PumpAlerts
import de.dh.raaps.common.model.PumpCapabilities
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.getAmountForMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * SimBody insulin pump handler that implements the [InsulinPump] interface.
 *
 * The actual simulated pump hardware is modeled as a separate class to reflect
 * the real-world scenario where the pump is a standalone device connected via
 * a wireless communication protocol.
 */
class SimBodyInsulinPump(
    private val device: SimBodyPumpDevice,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): InsulinPump {
    companion object {
        const val SIM_PUMP_MIN_BASAL_RATE = 0.0
        val SIM_PUMP_MIN_BASAL_INCREMENT = InsulinAmount(0.05)
        val SIM_PUMP_MIN_BOLUS_INCREMENT = InsulinAmount(0.05)
        val SIM_PUMP_MAX_BOLUS_SIZE = InsulinAmount(25.0)
    }

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
            minBasalRate = InsulinAmount(SIM_PUMP_MIN_BASAL_RATE),
            supportsZeroBasal = true,
            minBasalIncrement = SIM_PUMP_MIN_BASAL_INCREMENT,
            minBolusIncrement = SIM_PUMP_MIN_BOLUS_INCREMENT,
            maxBolusSize = SIM_PUMP_MAX_BOLUS_SIZE
        )
    )

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    // TODO: Simulate connection loss
    fun connect() {
        scope.launch {
            _isConnecting.value = true
            delay(1000.milliseconds) // Simulate connection delay
            _isConnected.value = true
            _isConnecting.value = false
        }
    }

    fun disconnect() {
        _isConnected.value = false
    }

    override val pumpStatus: StateFlow<InsulinPumpStatus> = combine(
        device.batteryLevel,
        device.reservoirLevel,
        device.isBroken,
        _isConnected
    ) { battery, reservoir, broken, connected ->
        object : InsulinPumpStatus {
            override val pumpSuspended: Boolean = broken
            override val batteryRemainingPercent: Int = (battery * 100).toInt()
            override val reservoirRemainingUnits: InsulinAmount = reservoir
            override val lastSyncTimestamp: Long = if (connected) System.currentTimeMillis() else 0L
        }
    }.stateIn(scope, SharingStarted.Eagerly, object : InsulinPumpStatus {
        override val pumpSuspended: Boolean = false
        override val batteryRemainingPercent: Int = 100
        override val reservoirRemainingUnits: InsulinAmount = InsulinAmount(300.0)
        override val lastSyncTimestamp: Long = System.currentTimeMillis()
    })

    override val alerts: StateFlow<PumpAlerts> = combine(
        device.batteryLevel,
        device.reservoirLevel,
        device.isOccluded,
        device.hasHardwareError,
        combine(device.isBroken, device.isPrimed) { broken, primed -> broken to primed }
    ) { battery, reservoir, occluded, hwError, (broken, primed) ->
        PumpAlerts(
            batteryLow = battery < 0.1,
            reservoirLow = reservoir < InsulinAmount(20.0),
            other = occluded || hwError || broken || !primed
        )
    }.stateIn(scope, SharingStarted.Eagerly, PumpAlerts())

    override val basalStatus: StateFlow<BasalStatus> = combine(
        device.tempBasalPercent,
        device.activeProfile,
        combine(device.isBroken, device.hasHardwareError, device.isOccluded) { b, h, o -> b || h || o }
    ) { tempPercent, profile, isSuspended ->
        val normalRate = profile.basalBlocks.getAmountForMinute(Timestamp.now().minutesSinceMidnight())
        val activeRate = if (isSuspended) 0.0 else {
            if (tempPercent != null) normalRate * (tempPercent / 100.0) else normalRate
        }
        BasalStatus(
            activeRate = InsulinAmount(activeRate),
            isTempBasal = tempPercent != null,
            tempBasalPercent = tempPercent,
            isSuspended = isSuspended
        )
    }.stateIn(scope, SharingStarted.Eagerly, BasalStatus())

    private val _history = MutableStateFlow<InsulinHistory?>(null)
    override val history: StateFlow<InsulinHistory?> = _history

    override suspend fun bolus(amount: InsulinAmount) {
        if (!_isConnected.value) throw Exception("Pump not connected to App")

        if (device.deliverBolus(amount)) {
            // Success - device level handled reporting to body and history
            refreshStatus()
        } else {
            val errorReason = when {
                device.isBroken.value -> "Hardware broken"
                device.hasHardwareError.value -> "Hardware error"
                device.isOccluded.value -> "Occlusion detected"
                !device.isPrimed.value -> "Pump not primed"
                device.reservoirLevel.value < amount -> "Insulin reservoir empty"
                amount < SIM_PUMP_MIN_BOLUS_INCREMENT -> "Amount below minimum increment (${SIM_PUMP_MIN_BOLUS_INCREMENT.iu} IU)"
                else -> "Unknown hardware failure"
            }
            throw Exception("Bolus failed: $errorReason")
        }
    }

    override suspend fun stopBolus() {
        // Simple simulator: bolus is instant
    }

    override suspend fun tempBasal(percent: Int, durationHours: Int) {
        if (!_isConnected.value) throw Exception("Pump not connected to App")

        if (device.isBroken.value || device.hasHardwareError.value) {
            throw Exception("Pump hardware error - cannot set temp basal")
        }

        device.updateTempBasalPercent(percent, durationHours)
        refreshStatus()
    }

    override suspend fun cancelTempBasal() {
        if (!_isConnected.value) throw Exception("Pump not connected to App")
        device.updateTempBasalPercent(null) // Clear temp basal override
        refreshStatus()
    }

    override suspend fun setProfile(profile: InsulinProfile) {
        if (!_isConnected.value) throw Exception("Pump not connected to App")
        device.setProfile(profile)
    }

    override suspend fun syncHistory() {
        if (!_isConnected.value) return
        val points = device.getHistory().map { point ->
            object : InsulinHistoryPoint {
                override val timestamp: Long = point.timestamp
                override val amount: InsulinAmount = point.amount
                override val category: InsulinCategory = point.category
            }
        }
        if (points.isNotEmpty()) {
            _history.value = InsulinHistory(
                from = points.minOf { it.timestamp },
                to = points.maxOf { it.timestamp },
                points = points
            )
        }
    }

    override suspend fun refreshStatus() {
        if (!_isConnected.value) return
        // pumpStatus and alerts are already connected via flows in this simulator.
        // We also sync history on refresh in this sim to stay up-to-date.
        syncHistory()
    }

    override fun stop() {
        // Cleanup if needed
    }
}