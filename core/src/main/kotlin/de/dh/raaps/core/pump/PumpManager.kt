package de.dh.raaps.core.pump

import de.dh.raaps.common.model.InsulinHistory
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.core.system.WakeupHandler
import kotlinx.coroutines.flow.StateFlow

/**
 * Central manager for insulin pump interactions and status monitoring.
 */
interface PumpManager : WakeupHandler {
    /**
     * Active issues preventing the pump from working correctly.
     */
    val pumpIssues: StateFlow<Set<PumpIssue>>

    /**
     * The current insulin pump device. Setting this will initiate a new connection.
     */
    var insulinPump: InsulinPump?

    /**
     * Provides access to the underlying coordinator.
     * Note: Prefer using high-level methods of this manager.
     */
    val pumpCoordinator: PumpCoordinator?

    /**
     * Issues a command to the pump.
     */
    suspend fun issueCommand(command: PumpCommand, isCancelableAPSCommand: Boolean)

    /**
     * Cancels pending jobs that match the predicate.
     */
    fun cancelJobs(predicate: (PumpJob) -> Boolean)

    /**
     * Waits until all pending pump jobs are completed.
     */
    suspend fun waitForIdle()

    /**
     * Explicitly wakes up the pump connection to process pending jobs or refresh status.
     */
    fun wakeup()

    /**
     * Returns true if there are pending jobs in the queue.
     */
    fun hasPendingJobs(): Boolean

    /**
     * Sets a listener to be notified when new insulin history data is received from the pump.
     */
    fun setOnHistoryUpdateListener(listener: suspend (InsulinHistory) -> Unit)
}