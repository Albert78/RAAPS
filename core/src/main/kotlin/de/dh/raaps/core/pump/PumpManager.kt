package de.dh.raaps.core.pump

import de.dh.raaps.common.model.BolusStatus
import de.dh.raaps.common.model.InsulinHistory
import de.dh.raaps.common.model.InsulinPump
import kotlinx.coroutines.flow.StateFlow

import kotlin.time.Duration

/**
 * Central manager for insulin pump interactions and status monitoring. Pump manager and [PumpCoordinator]
 * have a similar job but [PumpCoordinator] is bound to a pump connection, while [PumpManager]
 * is the system-wide service which even exists if no pump is configured in the system.
 */
interface PumpManager {
    /**
     * Active issues preventing the pump from working correctly.
     */
    val pumpIssues: StateFlow<Set<PumpIssue>>

    /**
     * The current insulin pump device. Setting this will initiate a new connection and a [pumpCoordinator]
     * to be created.
     */
    var insulinPump: InsulinPump?

    /**
     * Observable flow of the current insulin pump device.
     */
    val activeInsulinPump: StateFlow<InsulinPump?>

    /**
     * Provides access to the underlying coordinator. The pump coordinator is null if no pump
     * is present.
     * Note: Prefer using high-level methods of this manager.
     */
    val pumpCoordinator: PumpCoordinator?

    /**
     * Issues a command to the pump.
     */
    fun issueCommand(command: PumpCommand)

    /**
     * Cancels pending jobs that match the predicate.
     */
    fun cancelJobs(predicate: (PumpJob) -> Boolean)

    /**
     * Waits until all pending pump jobs are completed or until the timeout expires.
     */
    suspend fun waitForJobsOrError(timeout: Duration = PumpCoordinator.DEFAULT_WAIT_FOR_JOBS_TIMEOUT)

    /**
     * Explicitly wakes up the pump connection to process pending jobs or refresh status.
     */
    fun wakeup()

    /**
     * Returns true if there are pending jobs in the queue.
     */
    fun hasPendingJobs(): Boolean

    /**
     * Returns the number of pending jobs in the queue.
     */
    fun getPendingJobsCount(): Int

    /**
     * Resets the pump coordinator from an error state back to idle and clears command errors.
     */
    fun reset()

    /**
     * Sets a listener to be notified when new insulin history data is received from the pump.
     */
    fun setOnHistoryUpdateListener(listener: suspend (InsulinHistory) -> Unit)

    /**
     * Sets a listener to be notified when bolus status is updated by the pump.
     */
    fun setOnBolusStatusUpdateListener(listener: suspend (BolusStatus) -> Unit)
}