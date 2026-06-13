package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TherapyData

/**
 * Status information returned by the pump.
 */
data class PumpStatus(
    val reservoirLevel: InsulinAmount,
    val batteryLevel: Int?
)

/**
 * Interface for an insulin delivery device.
 * Manages the connection to the physical pump hardware and handles insulin delivery operations.
 */
interface InsulinPump {
    val name: String

    fun start()
    fun stop()

    /**
     * Delivers a bolus of the given amount.
     */
    suspend fun deliverBolus(amount: InsulinAmount)

    /**
     * Sets a temporary basal rate.
     */
    suspend fun setTempBasal(unitsPerHour: Double, durationMinutes: Minutes)

    /**
     * Cancels the currently active temporary basal rate.
     */
    suspend fun cancelTempBasal()

    /**
     * Sets the active basal profile on the pump.
     */
    suspend fun setProfile(profile: TherapyData)

    /**
     * Cancels any currently delivering bolus.
     */
    suspend fun cancelBolus()

    /**
     * Reads the current status (reservoir, battery) from the pump.
     */
    suspend fun readStatus(): PumpStatus
}