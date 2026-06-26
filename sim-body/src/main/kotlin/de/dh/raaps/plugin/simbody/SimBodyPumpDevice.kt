package de.dh.raaps.plugin.simbody

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the physical (simulated) insulin pump device.
 * This class holds the state of the hardware, including battery, insulin levels,
 * and error conditions like occlusions.
 */
class SimBodyPumpDevice {
    private val _batteryLevel = MutableStateFlow(1.0) // 0.0 to 1.0
    val batteryLevel: StateFlow<Double> = _batteryLevel.asStateFlow()

    private val _reservoirLevel = MutableStateFlow(300.0) // Units
    val reservoirLevel: StateFlow<Double> = _reservoirLevel.asStateFlow()

    private val _isOccluded = MutableStateFlow(false)
    val isOccluded: StateFlow<Boolean> = _isOccluded.asStateFlow()

    private val _isPrimed = MutableStateFlow(true)
    val isPrimed: StateFlow<Boolean> = _isPrimed.asStateFlow()

    private val _hasHardwareError = MutableStateFlow(false)
    val hasHardwareError: StateFlow<Boolean> = _hasHardwareError.asStateFlow()

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

    /**
     * Simulates insulin delivery. Reduces reservoir level.
     */
    fun deliverInsulin(units: Double): Boolean {
        if (isOccluded.value || hasHardwareError.value || !isPrimed.value || reservoirLevel.value < units) {
            return false
        }
        _reservoirLevel.value = (reservoirLevel.value - units).coerceAtLeast(0.0)
        return true
    }
}