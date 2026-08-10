package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.InsulinProfile
import kotlinx.coroutines.flow.StateFlow

/**
 * Manufacturer and hardware information about a pump.
 */
data class HardwareInformation(
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
    val pumpDescription: String
)

/**
 * Technical specification and hardware characteristics.
 */
data class PumpCapabilities(
    val minBasalRate: InsulinAmount,
    val supportsZeroBasal: Boolean,
    val minBasalIncrement: InsulinAmount,
    val minBolusIncrement: InsulinAmount,
    val maxBolusSize: InsulinAmount,
    // TODO: Continue list for sensible capability values
//    val supportsTempBasal: Boolean,
//    val supportsExtendedBolus: Boolean,
//    val audibleTempBasalReminder: Boolean,
//    val deliversBasalWhileBolusing: Boolean,
//    val internalTimeManagement: Boolean
)

/**
 * Generic data representing the general status of an insulin pump.
 */
interface InsulinPumpStatus {
    val pumpSuspended: Boolean
    val batteryRemainingPercent: Int
    val reservoirRemainingUnits: InsulinAmount
    val lastSyncTimestamp: Long
}

/**
 * Normalized state of pump-related alerts.
 */
data class PumpAlerts(
    val batteryLow: Boolean = false,
    val reservoirLow: Boolean = false,
    val other: Boolean = false
)

/**
 * Normalized state of the basal insulin delivery.
 */
data class BasalStatus(
    /**
     * The absolute amount of insulin in Units per Hour (U/h) that the pump is currently delivering.
     * This value is inclusive of any temporary basal rate or suspension.
     * If the pump is suspended, this value is always 0.0.
     */
    val activeRate: InsulinAmount = InsulinAmount.ZERO,

    val isTempBasal: Boolean = false,

    /**
     * The relative amount of insulin in percent (%) compared to the profile basal rate.
     * Only set if [isTempBasal] is true.
     */
    val tempBasalPercent: Int? = null,

    /**
     * True if insulin delivery is globally suspended on the hardware level.
     * While suspended, [activeRate] is always 0.0. This state is distinct
     * from a 0% temporary basal rate as it usually requires a manual 'resume' action.
     */
    val isSuspended: Boolean = false,
)

/**
 * Generic interface for an insulin pump.
 */
interface InsulinPump {
    /**
     * Pump hardware information. Null if not yet retrieved.
     */
    val hardwareInformation: StateFlow<HardwareInformation?>

    /**
     * Pump capabilities information. Null if not yet retrieved.
     */
    val pumpCapabilities: StateFlow<PumpCapabilities>

    val isConnected: StateFlow<Boolean>

    /**
     * General status and data of the pump (Battery, Reservoir, etc.).
     * Can be updated by calling [refreshStatus].
     */
    val pumpStatus: StateFlow<InsulinPumpStatus>

    /**
     * Current status of normalized pump alerts. If one or more alerts are set, the user should check the pump.
     * Can be updated by calling [syncHistory].
     */
    val alerts: StateFlow<PumpAlerts>

    /**
     * Current status of the basal insulin delivery.
     */
    val basalStatus: StateFlow<BasalStatus>

    /**
     * History of insulin deliveries (Basal and Bolus) from the pump.
     */
    val history: StateFlow<InsulinHistory?>

    /**
     * Initiates a bolus delivery.
     *
     * @throws Exception if the command cannot be sent or the pump connection fails.
     */
    suspend fun bolus(amount: InsulinAmount)

    /**
     * Immediately stops any currently running bolus delivery.
     *
     * @throws Exception if the command cannot be sent or the pump connection fails.
     */
    suspend fun stopBolus()

    /**
     * Starts a temporary basal rate.
     *
     * @throws Exception if the command cannot be sent or the pump connection fails.
     */
    suspend fun tempBasal(percent: Int, durationHours: Int)

    /**
     * Cancels the currently active temporary basal rate.
     *
     * @throws Exception if the command cannot be sent or the pump connection fails.
     */
    suspend fun cancelTempBasal()

    /**
     * Sets the active therapy profile on the pump.
     *
     * @throws Exception if the command cannot be sent or the pump connection fails.
     */
    suspend fun setProfile(profile: InsulinProfile)

    /**
     * Performs a synchronization of the pump's history events.
     *
     * @throws Exception if the command cannot be sent or the pump connection fails.
     */
    suspend fun syncHistory()

    /**
     * Refreshes the last known data and status from the pump.
     *
     * @throws Exception if the command cannot be sent or the pump connection fails.
     */
    suspend fun refreshStatus()

    /**
     * Stops this insulin pump connection.
     */
    fun stop()
}
