package de.dh.raaps.plugin.simbody

import android.util.Log
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinCategory
import de.dh.raaps.common.model.MS_PER_DAY
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.getAmountForMinute
import de.dh.raaps.plugin.simbody.repository.db.PumpDao
import de.dh.raaps.plugin.simbody.repository.db.PumpDeliveryType
import de.dh.raaps.plugin.simbody.repository.db.PumpHistoryEntity
import de.dh.raaps.plugin.simbody.repository.db.PumpStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Represents the physical (simulated) insulin pump device.
 * This class holds the state of the hardware, including battery, insulin levels,
 * and error conditions like occlusions.
 *
 * It is responsible for reporting to the [BodyModel].
 */
class SimBodyPumpDevice(
    private val bodyModel: BodyModel,
    initialProfile: InsulinProfile,
    private val pumpDao: PumpDao? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _batteryLevel = MutableStateFlow(0.85) // 0.0 to 1.0
    val batteryLevel: StateFlow<Double> = _batteryLevel.asStateFlow()

    private val _reservoirLevel = MutableStateFlow(InsulinAmount(180.0)) // Units
    val reservoirLevel: StateFlow<InsulinAmount> = _reservoirLevel.asStateFlow()

    private val _isOccluded = MutableStateFlow(false)
    val isOccluded: StateFlow<Boolean> = _isOccluded.asStateFlow()

    private val _isPrimed = MutableStateFlow(true)
    val isPrimed: StateFlow<Boolean> = _isPrimed.asStateFlow()

    private val _hasHardwareError = MutableStateFlow(false)
    val hasHardwareError: StateFlow<Boolean> = _hasHardwareError.asStateFlow()

    private val _isBroken = MutableStateFlow(false)
    val isBroken: StateFlow<Boolean> = _isBroken.asStateFlow()

    private val _activeProfile = MutableStateFlow(initialProfile)
    val activeProfile: StateFlow<InsulinProfile> = _activeProfile.asStateFlow()

    // Internal history storage
    private val _history = CopyOnWriteArrayList<HistoryEntry>()

    private val _tempBasalPercent = MutableStateFlow<Int?>(null)
    val tempBasalPercent: StateFlow<Int?> = _tempBasalPercent.asStateFlow()

    private val _tempBasalExpiryMs = MutableStateFlow<Long?>(null)
    val tempBasalExpiryMs: StateFlow<Long?> = _tempBasalExpiryMs.asStateFlow()

    private var lastBasalDeliveryTimestamp: Long = 0L // Initialize on first tick

    fun loadState() {
        val dao = pumpDao ?: return
        scope.launch {
            try {
                dao.getPumpState()?.let { state ->
                    _batteryLevel.value = state.batteryLevel
                    _reservoirLevel.value = InsulinAmount(state.reservoirLevel)
                    _isOccluded.value = state.isOccluded
                    _isPrimed.value = state.isPrimed
                    _hasHardwareError.value = state.hasHardwareError
                    _isBroken.value = state.isBroken
                    lastBasalDeliveryTimestamp = state.lastBasalDeliveryTimestampMs
                    _tempBasalPercent.value = state.tempBasalPercent
                    _tempBasalExpiryMs.value = state.tempBasalExpiryMs
                }

                val horizonMs = 3 * MS_PER_DAY
                val threshold = System.currentTimeMillis() - horizonMs
                val loadedHistory = dao.getHistorySince(threshold).map {
                    HistoryEntry(
                        it.timestampMs,
                        it.amount,
                        if (it.deliveryType == PumpDeliveryType.Bolus) InsulinCategory.Bolus else InsulinCategory.Basal
                    )
                }
                _history.clear()
                _history.addAll(loadedHistory)
            } catch (e: Exception) {
                Log.e("SimBodyPumpDevice", "Error loading state: ${e.message}")
            }
        }
    }

    private fun persistState() {
        val dao = pumpDao ?: return
        scope.launch {
            dao.updatePumpState(
                PumpStateEntity(
                    batteryLevel = _batteryLevel.value,
                    reservoirLevel = _reservoirLevel.value.iu,
                    isOccluded = _isOccluded.value,
                    isPrimed = _isPrimed.value,
                    hasHardwareError = _hasHardwareError.value,
                    isBroken = _isBroken.value,
                    lastBasalDeliveryTimestampMs = lastBasalDeliveryTimestamp,
                    tempBasalPercent = _tempBasalPercent.value,
                    tempBasalExpiryMs = _tempBasalExpiryMs.value
                )
            )
        }
    }

    fun setBatteryLevel(level: Double) {
        _batteryLevel.value = level.coerceIn(0.0, 1.0)
        persistState()
    }

    fun setReservoirLevel(amount: InsulinAmount) {
        _reservoirLevel.value = amount.coerceAtLeast(InsulinAmount.ZERO)
        persistState()
    }

    fun setOcclusion(occluded: Boolean) {
        _isOccluded.value = occluded
        persistState()
    }

    fun setPrimed(primed: Boolean) {
        _isPrimed.value = primed
        persistState()
    }

    fun setHardwareError(error: Boolean) {
        _hasHardwareError.value = error
        persistState()
    }

    fun setBroken(broken: Boolean) {
        _isBroken.value = broken
        persistState()
    }

    fun setProfile(profile: InsulinProfile) {
        _activeProfile.value = profile
        // Profiles are currently not persisted in pump_state, but in body_profiles if needed.
        // For SimBodyPumpDevice we might want to persist the profile too if it's pump-specific.
    }

    fun getProfileBasalRate(timestamp: Timestamp = Timestamp.now()): Double {
        val profile = _activeProfile.value
        return profile.basalBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
    }

    /**
     * Advances the internal device state.
     */
    fun advanceToTick(currentTimestamp: Timestamp) {
        handleBasal(currentTimestamp)
        return
    }

    /**
     * Implements active basal control: delivers 1/3 of the basal rate every 20 minutes.
     * These deliveries are recorded as insulin history points.
     */
    private fun handleBasal(currentTimestamp: Timestamp) {
        val twentyMinutesMs = 20 * 60 * 1000L

        // Initialize if first tick to prevent retroactive deliveries
        if (lastBasalDeliveryTimestamp == 0L) {
            lastBasalDeliveryTimestamp = currentTimestamp.ms
            persistState()
            return
        }

        while (currentTimestamp.ms - lastBasalDeliveryTimestamp >= twentyMinutesMs) {
            val deliveryTimestamp = Timestamp(lastBasalDeliveryTimestamp + twentyMinutesMs)

            // Auto-reset TBR if expired
            _tempBasalExpiryMs.value?.let { expiry ->
                if (deliveryTimestamp.ms >= expiry) {
                    _tempBasalPercent.value = null
                    _tempBasalExpiryMs.value = null
                    persistState()
                }
            }

            val profileRate = InsulinAmount(getProfileBasalRate(deliveryTimestamp))
            val currentPercent = _tempBasalPercent.value
            val rate = if (currentPercent != null) {
                profileRate * (currentPercent / 100.0)
            } else {
                profileRate
            }

            // Deliver 1/3 of the hourly rate (since we deliver every 20 minutes)
            val basalToDeliver = rate / 3.0

            deliverInternalBolus(
                basalToDeliver,
                deliveryTimestamp,
                if (_tempBasalPercent.value != null) PumpDeliveryType.Tbr else PumpDeliveryType.Basal
            )
            lastBasalDeliveryTimestamp = deliveryTimestamp.ms
            persistState()
        }
    }

    private fun deliverInternalBolus(units: InsulinAmount, timestamp: Timestamp, type: PumpDeliveryType = PumpDeliveryType.Basal) {
        // Active delivery only works if hardware is OK
        if (isBroken.value || hasHardwareError.value || isOccluded.value || !isPrimed.value) {
            return
        }
        if (units < SimBodyInsulinPump.SIM_PUMP_MIN_BOLUS_INCREMENT) { // Check against PU
            return
        }
        if (reservoirLevel.value < units) {
            return
        }

        _reservoirLevel.value = (reservoirLevel.value - units).coerceAtLeast(InsulinAmount.ZERO)
        persistState()

        // Report to body
        bodyModel.bolus(units, timestamp = timestamp)

        // Record in history as an insulin delivery
        val entry = HistoryEntry(
            timestamp = timestamp.ms,
            amount = units,
            category = if (type == PumpDeliveryType.Bolus) InsulinCategory.Bolus else InsulinCategory.Basal
        )
        _history.add(entry)

        pumpDao?.let { dao ->
            scope.launch {
                dao.insertHistoryEntry(PumpHistoryEntity(
                    timestampMs = entry.timestamp,
                    amount = entry.amount,
                    deliveryType = type
                ))
            }
        }

        cleanupHistory()
    }

    /**
     * Simulates insulin delivery. Reduces reservoir level and reports to BodyModel.
     * Returns true if delivery was successful on the hardware level.
     */
    fun deliverBolus(amount: InsulinAmount): Boolean {
        if (isBroken.value || hasHardwareError.value || isOccluded.value || !isPrimed.value) {
            return false
        }
        if (amount < SimBodyInsulinPump.SIM_PUMP_MIN_BOLUS_INCREMENT) {
            return false
        }
        if (reservoirLevel.value < amount) {
            return false
        }

        deliverInternalBolus(amount, Timestamp.now(), PumpDeliveryType.Bolus)
        return true
    }

    fun updateTempBasalPercent(percent: Int?, durationHours: Int? = null) {
        _tempBasalPercent.value = percent
        if (percent != null && durationHours != null) {
            _tempBasalExpiryMs.value = Timestamp.now().plusHours(durationHours).ms
        } else {
            _tempBasalExpiryMs.value = null
        }
        persistState()
    }

    fun getHistory(): List<HistoryEntry> = _history.toList()

    private fun cleanupHistory() {
        val threeDaysAgo = System.currentTimeMillis() - (3 * MS_PER_DAY)
        _history.removeIf { it.timestamp < threeDaysAgo }

        pumpDao?.let { dao ->
            scope.launch {
                dao.deleteOldHistory(threeDaysAgo)
            }
        }
    }

    /**
     * Replaces the reservoir and resets levels.
     */
    fun replaceReservoir(amount: InsulinAmount = InsulinAmount(300.0)) {
        _reservoirLevel.value = amount
        _isPrimed.value = false // Need to prime after reservoir change
        persistState()
    }

    /**
     * Performs priming of the catheter.
     */
    fun primeCatheter(): Boolean {
        val primeAmount = InsulinAmount(10.0)
        if (reservoirLevel.value >= primeAmount) {
            _reservoirLevel.value -= primeAmount // Priming uses some insulin
            _isPrimed.value = true
            persistState()
            return true
        }
        return false
    }

    data class HistoryEntry(
        val timestamp: Long,
        val amount: InsulinAmount,
        val category: InsulinCategory
    )
}
