package de.dh.raaps.plugin.simbody

import de.dh.raaps.common.model.BasalStatus
import de.dh.raaps.common.model.HardwareInformation
import de.dh.raaps.common.model.InsulinHistory
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

class SimBodyInsulinPump(
    private val device: SimBodyPumpDevice,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
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
            override val reservoirRemainingUnits: Double = reservoir
            override val lastSyncTimestamp: Long = if (connected) System.currentTimeMillis() else 0L
        }
    }.stateIn(scope, SharingStarted.Eagerly, object : InsulinPumpStatus {
        override val pumpSuspended: Boolean = false
        override val batteryRemainingPercent: Int = 100
        override val reservoirRemainingUnits: Double = 300.0
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
            reservoirLow = reservoir < 20.0,
            other = occluded || hwError || broken || !primed
        )
    }.stateIn(scope, SharingStarted.Eagerly, PumpAlerts())

    override val basalStatus: StateFlow<BasalStatus> = combine(
        device.tempBasalRate,
        device.activeProfile,
        combine(device.isBroken, device.hasHardwareError, device.isOccluded) { b, h, o -> b || h || o }
    ) { tempRate, profile, isSuspended ->
        val normalRate = profile.basalBlocks.getAmountForMinute(Timestamp.now().minutesSinceMidnight())
        BasalStatus(
            activeRate = if (isSuspended) 0.0 else (tempRate ?: normalRate),
            isTempBasal = tempRate != null,
            isSuspended = isSuspended
        )
    }.stateIn(scope, SharingStarted.Eagerly, BasalStatus())

    private val _history = MutableStateFlow<InsulinHistory?>(null)
    override val history: StateFlow<InsulinHistory?> = _history

    override suspend fun bolus(amount: Double) {
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
                else -> "Unknown hardware failure"
            }
            throw Exception("Bolus failed: $errorReason")
        }
    }

    override suspend fun stopBolus() {
        // Simple simulator: bolus is instant
    }

    override suspend fun tempBasal(absoluteUnits: Double, durationHours: Int) {
        if (!_isConnected.value) throw Exception("Pump not connected to App")

        if (device.isBroken.value || device.hasHardwareError.value) {
            throw Exception("Pump hardware error - cannot set temp basal")
        }

        device.updateBasalRate(absoluteUnits)
        refreshStatus()
    }

    override suspend fun cancelTempBasal() {
        if (!_isConnected.value) throw Exception("Pump not connected to App")
        device.updateBasalRate(null) // Clear temp basal override
        refreshStatus()
    }

    override suspend fun setProfile(profile: InsulinProfile) {
        if (!_isConnected.value) throw Exception("Pump not connected to App")
        device.setProfile(profile)
    }

    override suspend fun syncHistory() {
        if (!_isConnected.value) return
        val points = device.getHistory()
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