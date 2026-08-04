package de.dh.raaps.plugin.simbody

import de.dh.raaps.common.model.InsulinHistoryPoint
import de.dh.raaps.common.model.MS_PER_DAY
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.getAmountForMinute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    initialProfile: InsulinProfile
) {
    private val _batteryLevel = MutableStateFlow(0.85) // 0.0 to 1.0
    val batteryLevel: StateFlow<Double> = _batteryLevel.asStateFlow()

    private val _reservoirLevel = MutableStateFlow(180.0) // Units
    val reservoirLevel: StateFlow<Double> = _reservoirLevel.asStateFlow()

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

    private val _tempBasalRate = MutableStateFlow<Double?>(null)
    val tempBasalRate: StateFlow<Double?> = _tempBasalRate.asStateFlow()

    private var lastBasalDeliveryTimestamp: Long = 0L // Initialize on first tick

    fun setBatteryLevel(level: Double) {
        _batteryLevel.value = level.coerceIn(0.0, 1.0)
    }

    fun setReservoirLevel(units: Double) {
        _reservoirLevel.value = units.coerceAtLeast(0.0)
    }

    fun setOcclusion(occluded: Boolean) {
        _isOccluded.value = occluded
    }

    fun setPrimed(primed: Boolean) {
        _isPrimed.value = primed
    }

    fun setHardwareError(error: Boolean) {
        _hasHardwareError.value = error
    }

    fun setBroken(broken: Boolean) {
        _isBroken.value = broken
    }

    fun setProfile(profile: InsulinProfile) {
        _activeProfile.value = profile
    }

    fun getProfileBasalRate(timestamp: Timestamp = Timestamp.now()): Double {
        val profile = _activeProfile.value
        return profile.basalBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
    }

    /**
     * Advances the internal device state.
     * Implements active basal control: delivers 1/3 of the basal rate every 20 minutes.
     * These deliveries are recorded as insulin history points.
     */
    fun advanceToTick(currentTimestamp: Timestamp) {
        val twentyMinutesMs = 20 * 60 * 1000L

        // Initialize if first tick to prevent retroactive deliveries
        if (lastBasalDeliveryTimestamp == 0L) {
            lastBasalDeliveryTimestamp = currentTimestamp.ms
            return
        }

        while (currentTimestamp.ms - lastBasalDeliveryTimestamp >= twentyMinutesMs) {
            val deliveryTimestamp = Timestamp(lastBasalDeliveryTimestamp + twentyMinutesMs)
            val rate = _tempBasalRate.value ?: getProfileBasalRate(deliveryTimestamp)
            val basalToDeliver = rate / 3.0

            deliverInternalBasal(basalToDeliver, deliveryTimestamp)
            lastBasalDeliveryTimestamp = deliveryTimestamp.ms
        }
    }

    private fun deliverInternalBasal(units: Double, timestamp: Timestamp) {
        // Active delivery only works if hardware is OK
        if (isBroken.value || hasHardwareError.value || isOccluded.value || !isPrimed.value) {
            return
        }
        if (reservoirLevel.value < units) {
            return
        }

        _reservoirLevel.value = (reservoirLevel.value - units).coerceAtLeast(0.0)

        // Report to body as a small bolus
        bodyModel.bolus(units, timestamp = timestamp)

        // Record in history as an insulin delivery
        _history.add(HistoryEntry(
            timestamp = timestamp.ms,
            amount = units
        ))
        cleanupHistory()
    }

    /**
     * Simulates insulin delivery. Reduces reservoir level and reports to BodyModel.
     * Returns true if delivery was successful on the hardware level.
     */
    fun deliverBolus(units: Double): Boolean {
        if (isBroken.value || hasHardwareError.value || isOccluded.value || !isPrimed.value) {
            return false
        }
        if (reservoirLevel.value < units) {
            return false
        }

        _reservoirLevel.value = (reservoirLevel.value - units).coerceAtLeast(0.0)

        // Report to body
        bodyModel.bolus(units)

        // Record in history
        _history.add(HistoryEntry(
            timestamp = System.currentTimeMillis(),
            amount = units
        ))
        cleanupHistory()

        return true
    }

    fun updateBasalRate(unitsPerHour: Double?) {
        _tempBasalRate.value = unitsPerHour
    }

    fun getHistory(): List<InsulinHistoryPoint> = _history.toList()

    private fun cleanupHistory() {
        val threeDaysAgo = System.currentTimeMillis() - (3 * MS_PER_DAY)
        _history.removeIf { it.timestamp < threeDaysAgo }
    }

    /**
     * Replaces the reservoir and resets levels.
     */
    fun replaceReservoir(units: Double = 300.0) {
        _reservoirLevel.value = units
        _isPrimed.value = false // Need to prime after reservoir change
    }

    /**
     * Performs priming of the catheter.
     */
    fun primeCatheter(): Boolean {
        if (reservoirLevel.value >= 10.0) {
            _reservoirLevel.value -= 10.0 // Priming uses some insulin
            _isPrimed.value = true
            return true
        }
        return false
    }

    private data class HistoryEntry(
        override val timestamp: Long,
        override val amount: Double
    ) : InsulinHistoryPoint
}