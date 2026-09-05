package de.dh.raaps.common.model

import de.dh.pump.PumpCommandException
import de.dh.pump.PumpConnectionException
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Timestamp
import kotlinx.coroutines.flow.SharedFlow
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
    val lastSyncTimestamp: Timestamp
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
 * Delivery state of an active or recent bolus.
 */
enum class BolusDeliveryState {
    IDLE,
    DELIVERING,
    COMPLETED,
    STOPPED
}

/**
 * Status snapshot of bolus delivery.
 */
data class BolusStatus(
    val state: BolusDeliveryState = BolusDeliveryState.IDLE,
    val bolusId: String? = null,
    val targetAmount: InsulinAmount = InsulinAmount.ZERO,
    val deliveredAmount: InsulinAmount = InsulinAmount.ZERO,
    val timestamp: Timestamp = Timestamp.INVALID,
) {
    /**
     * Delivery progress in percent (0 to 100).
     */
    val progressPercent: Int
        get() = if (targetAmount > InsulinAmount.ZERO) ((deliveredAmount / targetAmount) * 100).coerceIn(0.0, 100.0).toInt() else 0
}

/**
 * Real-time event for bolus delivery lifecycle updates.
 */
sealed interface BolusEvent {
    val bolusId: String?
    val timestamp: Timestamp

    data class Started(
        override val bolusId: String?,
        val targetAmount: InsulinAmount,
        override val timestamp: Timestamp = Timestamp.now(),
    ) : BolusEvent

    data class Progress(
        override val bolusId: String?,
        val targetAmount: InsulinAmount,
        val deliveredAmount: InsulinAmount,
        override val timestamp: Timestamp = Timestamp.now(),
    ) : BolusEvent

    data class Completed(
        override val bolusId: String?,
        val targetAmount: InsulinAmount,
        val deliveredAmount: InsulinAmount,
        override val timestamp: Timestamp = Timestamp.now(),
    ) : BolusEvent

    data class Stopped(
        override val bolusId: String?,
        val targetAmount: InsulinAmount,
        val deliveredAmount: InsulinAmount,
        override val timestamp: Timestamp = Timestamp.now(),
    ) : BolusEvent
}

/**
 * Generic interface for an insulin pump.
 */
interface InsulinPump {
    /**
     * Pump hardware information. Null if not yet retrieved.
     */
    val hardwareInformation: StateFlow<HardwareInformation?>

    /**
     * Pump capabilities information.
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
    val bolusStatus: StateFlow<BolusStatus>

    /**
     * Event stream for bolus delivery lifecycle events.
     */
    val bolusEvents: SharedFlow<BolusEvent>

    /**
     * Current status of active or recent bolus delivery.
     */
    val history: StateFlow<InsulinHistory?>

    /**
     * Concentration of the insulin loaded in the pump. Default is [InsulinConcentration.U100].
     * Needed to convert [InsulinAmount] to pump units and vice versa.
     */
    var insulinConcentration: InsulinConcentration

    /**
     * Initiates a bolus delivery with an optional tracking [bolusId].
     * @param amount The amount of insulin to deliver.
     * @param bolusId Optional tracking ID for the bolus. This ID will be used
     * in [bolusEvents] and [bolusStatus] for the duration of this bolus delivery.
     * @throws PumpConnectionException if the technical connection fails.
     * @throws PumpCommandException if the command is rejected by the pump hardware.
     */
    suspend fun bolus(amount: InsulinAmount, bolusId: String? = null)

    /**
     * Immediately stops any currently running bolus delivery.
     *
     * @throws PumpConnectionException if the technical connection fails.
     * @throws PumpCommandException if the command is rejected by the pump hardware.
     */
    suspend fun stopBolus()

    /**
     * Starts a temporary basal rate.
     *
     * @throws PumpConnectionException if the technical connection fails.
     * @throws PumpCommandException if the command is rejected by the pump hardware.
     */
    suspend fun tempBasal(percent: Int, durationHours: Int)

    /**
     * Cancels the currently active temporary basal rate and switches back to the normal basal rate.
     *
     * @throws PumpConnectionException if the technical connection fails.
     * @throws PumpCommandException if the command is rejected by the pump hardware.
     */
    suspend fun cancelTempBasal()

    /**
     * Sets the active therapy profile on the pump.
     *
     * @throws PumpConnectionException if the technical connection fails.
     */
    suspend fun setProfile(profile: InsulinProfile)

    /**
     * Performs a synchronization of the pump's history events.
     *
     * @throws PumpConnectionException if the technical connection fails.
     */
    suspend fun syncHistory()

    /**
     * Refreshes the last known data and status from the pump.
     *
     * @throws PumpConnectionException if the technical connection fails.
     */
    suspend fun refreshStatus()

    /**
     * Stops this insulin pump connection.
     */
    fun stop()
}