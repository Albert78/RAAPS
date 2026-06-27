package de.dh.raaps.plugin.simbody

import de.dh.raaps.common.model.BasalHistoryPoint
import de.dh.raaps.common.model.BolusHistoryPoint
import de.dh.raaps.common.model.data.TherapyData
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
    initialProfile: TherapyData
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

    private val _activeProfile = MutableStateFlow<TherapyData>(initialProfile)
    val activeProfile: StateFlow<TherapyData> = _activeProfile.asStateFlow()

    // Internal history storage
    private val _bolusHistory = CopyOnWriteArrayList<BolusHistoryEntry>()
    private val _basalHistory = CopyOnWriteArrayList<BasalHistoryEntry>()

    private var tempBasalRate: Double? = null
    private var lastBasalDeliveryTimestamp: Long = System.currentTimeMillis()

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

    fun setProfile(profile: TherapyData) {
        _activeProfile.value = profile
    }

    fun getProfileBasalRate(): Double {
        val profile = _activeProfile.value
        return profile.basalBlocks.getAmountForMinute(Timestamp.now().minutesSinceMidnight())
    }

    /**
     * Advances the internal device state.
     * Implements active basal control: delivers 1/3 of the basal rate every 20 minutes.
     */
    fun advanceToTick(currentTimestamp: Timestamp) {
        val twentyMinutesMs = 20 * 60 * 1000L
        if (currentTimestamp.ms - lastBasalDeliveryTimestamp >= twentyMinutesMs) {
            val rate = tempBasalRate ?: getProfileBasalRate()
            val basalToDeliver = rate / 3.0
            deliverInternalBasal(basalToDeliver)
            lastBasalDeliveryTimestamp = currentTimestamp.ms
        }
    }

    private fun deliverInternalBasal(units: Double) {
        // Active delivery only works if hardware is OK
        if (isBroken.value || hasHardwareError.value || isOccluded.value || !isPrimed.value) {
            return
        }
        if (reservoirLevel.value < units) {
            return
        }

        _reservoirLevel.value = (reservoirLevel.value - units).coerceAtLeast(0.0)
        bodyModel.bolus(units) // Basal is just small boluses in this simplified model
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
        _bolusHistory.add(BolusHistoryEntry(System.currentTimeMillis(), units))
        cleanupHistory()

        return true
    }

    fun updateBasalRate(unitsPerHour: Double?) {
        tempBasalRate = unitsPerHour

        _basalHistory.add(BasalHistoryEntry(System.currentTimeMillis(), unitsPerHour ?: getProfileBasalRate()))
        cleanupHistory()
    }

    fun getBolusHistory(): List<BolusHistoryPoint> = _bolusHistory.toList()
    fun getBasalHistory(): List<BasalHistoryPoint> = _basalHistory.toList()

    private fun cleanupHistory() {
        val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
        _bolusHistory.removeIf { it.timestamp < threeDaysAgo }
        _basalHistory.removeIf { it.timestamp < threeDaysAgo }
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
    fun primeCatheter() {
        if (reservoirLevel.value >= 1.0) {
            _reservoirLevel.value -= 1.0 // Priming uses some insulin
            _isPrimed.value = true
        }
    }

    private data class BolusHistoryEntry(
        override val timestamp: Long,
        override val amount: Double
    ) : BolusHistoryPoint

    private data class BasalHistoryEntry(
        override val timestamp: Long,
        override val unitsPerHour: Double
    ) : BasalHistoryPoint
}
