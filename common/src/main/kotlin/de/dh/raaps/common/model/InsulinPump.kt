package de.dh.raaps.common.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Normalized state of pump-related alerts.
 */
data class PumpAlerts(
    val batteryLow: Boolean = false,
    val reservoirLow: Boolean = false,
    val suspended: Boolean = false,
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
    val activeRate: Double = 0.0,

    val isTempBasal: Boolean = false,

    /**
     * If [isTempBasal] is true, this holds the percentage of the temporary basal rate (e.g., 150 for 150%).
     * This field is purely informational and explains the origin of the current [activeRate].
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
 * Generic data representing the general status of an insulin pump.
 */
interface InsulinPumpStatus {
    val batteryRemainingPercent: Int
    val reservoirRemainingUnits: Double
    val lastSyncTimestamp: Long
}

/**
 * Snapshot of a basal rate at a specific point in time.
 */
interface BasalHistoryPoint {
    val timestamp: Long
    val unitsPerHour: Double
}

/**
 * Snapshot of a delivered bolus.
 */
interface BolusHistoryPoint {
    val timestamp: Long
    val amount: Double
}

/**
 * Generic interface for an insulin pump.
 */
interface InsulinPump {
    /**
     * General status and data of the pump (Battery, Reservoir, etc.).
     * Can be updated by calling [refreshStatus].
     */
    val status: StateFlow<InsulinPumpStatus>

    /**
     * Current state of normalized pump alerts. If one or more alerts are set, the user should check the pump.
     * Can be updated by calling [syncHistory].
     */
    val alerts: StateFlow<PumpAlerts>

    /**
     * Current state of the basal insulin delivery.
     */
    val basal: StateFlow<BasalStatus>

    /**
     * History of basal insulin delivery for the last 24 hours.
     */
    val basalHistory: StateFlow<List<BasalHistoryPoint>>

    /**
     * History of bolus deliveries for the last 24 hours.
     */
    val bolusHistory: StateFlow<List<BolusHistoryPoint>>

    /**
     * Initiates a bolus delivery.
     *
     * @throws Exception if the command cannot be sent or the pump connection fails.
     */
    suspend fun bolus(amount: Double)

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