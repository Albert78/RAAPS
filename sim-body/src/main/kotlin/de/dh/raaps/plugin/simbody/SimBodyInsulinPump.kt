package de.dh.raaps.plugin.simbody

import de.dh.pump.PumpConnectionException
import de.dh.raaps.common.model.BasalStatus
import de.dh.raaps.common.model.BolusDeliveryState
import de.dh.raaps.common.model.BolusEvent
import de.dh.raaps.common.model.BolusStatus
import de.dh.raaps.common.model.HardwareInformation
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinCategory
import de.dh.raaps.common.model.InsulinConcentration
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    override var insulinConcentration: InsulinConcentration = InsulinConcentration.U100

    companion object {
        const val SIM_PUMP_MIN_BASAL_RATE = 0.0
        val SIM_PUMP_MIN_BASAL_INCREMENT = InsulinAmount(0.01)
        val SIM_PUMP_MIN_BOLUS_INCREMENT = InsulinAmount(0.01)
        val SIM_PUMP_MAX_BOLUS_SIZE = InsulinAmount(40.0)
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
            override val lastSyncTimestamp: Timestamp = if (connected) Timestamp.now() else Timestamp.INVALID
        }
    }.stateIn(scope, SharingStarted.Eagerly, object : InsulinPumpStatus {
        override val pumpSuspended: Boolean = false
        override val batteryRemainingPercent: Int = 100
        override val reservoirRemainingUnits: InsulinAmount = InsulinAmount(300.0)
        override val lastSyncTimestamp: Timestamp = Timestamp.now()
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

    private val _bolusStatus = MutableStateFlow(BolusStatus())
    override val bolusStatus: StateFlow<BolusStatus> = _bolusStatus.asStateFlow()

    private val _bolusEvents = MutableSharedFlow<BolusEvent>(extraBufferCapacity = 64)
    override val bolusEvents: SharedFlow<BolusEvent> = _bolusEvents.asSharedFlow()

    private val _history = MutableStateFlow<InsulinHistory?>(null)
    override val history: StateFlow<InsulinHistory?> = _history

    override suspend fun bolus(amount: InsulinAmount, bolusId: String?) {
        if (!_isConnected.value) throw PumpConnectionException("Pump not connected to App")

        val startTimestamp = Timestamp.now()
        _bolusStatus.value = BolusStatus(
            state = BolusDeliveryState.DELIVERING,
            bolusId = bolusId,
            targetAmount = amount,
            deliveredAmount = InsulinAmount.ZERO,
            timestamp = startTimestamp
        )
        _bolusEvents.emit(BolusEvent.Started(bolusId, amount, startTimestamp))

        try {
            device.deliverBolus(amount)
            val completedTimestamp = Timestamp.now()
            _bolusStatus.value = BolusStatus(
                state = BolusDeliveryState.COMPLETED,
                bolusId = bolusId,
                targetAmount = amount,
                deliveredAmount = amount,
                timestamp = completedTimestamp
            )
            _bolusEvents.emit(BolusEvent.Completed(bolusId, amount, amount, completedTimestamp))
            refreshStatus()
        } catch (e: Exception) {
            val stoppedTimestamp = Timestamp.now()
            _bolusStatus.value = BolusStatus(
                state = BolusDeliveryState.STOPPED,
                bolusId = bolusId,
                targetAmount = amount,
                deliveredAmount = InsulinAmount.ZERO,
                timestamp = stoppedTimestamp
            )
            _bolusEvents.emit(BolusEvent.Stopped(bolusId, amount, InsulinAmount.ZERO, stoppedTimestamp))
            throw e
        }
    }

    override suspend fun stopBolus() {
        val current = _bolusStatus.value
        if (current.state == BolusDeliveryState.DELIVERING) {
            val timestamp = Timestamp.now()
            _bolusStatus.value = current.copy(
                state = BolusDeliveryState.STOPPED,
                timestamp = timestamp
            )
            _bolusEvents.emit(
                BolusEvent.Stopped(
                    bolusId = current.bolusId,
                    targetAmount = current.targetAmount,
                    deliveredAmount = current.deliveredAmount,
                    timestamp = timestamp
                )
            )
        }
    }

    override suspend fun tempBasal(percent: Int, durationHours: Int) {
        if (!_isConnected.value) throw PumpConnectionException("Pump not connected to App")
        device.updateTempBasalPercent(percent, durationHours)
        refreshStatus()
    }

    override suspend fun cancelTempBasal() {
        if (!_isConnected.value) throw PumpConnectionException("Pump not connected to App")
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
                override val pumpId: String? = point.id
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