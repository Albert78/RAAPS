package de.dh.raaps.core.pump

import android.util.Log
import de.dh.pump.PumpCommandException
import de.dh.pump.PumpConnectionException
import de.dh.pump.PumpStatus
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class PumpCoordinatorState {
    Idle, Running, Error
}

/**
 * Commands that can be sent to the pump.
 */
sealed class PumpCommand {
    object SyncHistory : PumpCommand()
    data class SetProfile(val profile: InsulinProfile) : PumpCommand()
    data class SetTempBasal(val percent: Int, val durationHours: Int) : PumpCommand()
    object CancelTempBasal : PumpCommand()
    data class DeliverBolus(val amount: InsulinAmount, val bolusId: String? = null) : PumpCommand()
    object CancelBolus : PumpCommand()
}

/**
 * A wrapper for a [PumpCommand] with metadata for retry and expiration handling.
 */
data class PumpJob(
    val id: String = UUID.randomUUID().toString(),
    val command: PumpCommand,
    val createdAt: Timestamp = Timestamp.now(),
    val expiresAt: Timestamp? = null,
    val retryCount: Int = 0,
    val nextAttemptAt: Timestamp = createdAt,
    val lastError: JobErrorCode? = null
) {
    fun isExpired(): Boolean = expiresAt?.let { it < Timestamp.now() } ?: false
    fun isReady(): Boolean = nextAttemptAt <= Timestamp.now()
}

sealed interface JobErrorCode {
    data object Expired : JobErrorCode
    data class ConnectionFailed(val message: String? = null) : JobErrorCode
    data class CommandFailed(val status: PumpStatus, val message: String? = null) : JobErrorCode
    data class TechnicalError(val message: String? = null) : JobErrorCode
}

/**
 * The PumpCoordinator is the high-level orchestrator for the insulin pump subsystem.
 * It acts as a mediator between the APS core and the physical pump hardware (abstracted via [InsulinPump]).
 *
 * Key Responsibilities:
 * - **Job Management:** Manages a queue of [PumpJob]s, ensuring commands are executed in order
 *   or invalidated upon expiration.
 * - **Resilience:** Implements a multi-tier retry strategy (immediate retries followed by
 *   time-delayed attempts) to handle transient connectivity issues.
 * - **Connectivity Monitoring:** Maintains a persistent link via a regular "heartbeat" to
 *   keep status information (reservoir, battery) up to date.
 * - **Power Management:** Coordinates with the system's power state by acquiring wake-locks
 *   during active communication and scheduling system wakeups for future tasks.
 *
 * The coordinator's lifecycle is bound to the enclosing APS instance. It remains active
 * and continues to dispatch commands even if the pump is disconnected,
 * effectively decoupling the core logic from the pump availability.
 */
// TODO: Multithreading/thread allocation
// TODO: Notifications from pump
@OptIn(ExperimentalCoroutinesApi::class)
class PumpCoordinator(
    val pump: InsulinPump,
    /** Callback to acquire a wake-lock in the system to ensure the CPU stays awake during pump communication. */
    private val onAcquireBusyState: () -> Unit,
    /** Callback to release the wake-lock acquired via [onAcquireBusyState]. */
    private val onReleaseBusyState: () -> Unit,
    /** Callback to request a system wakeup at the specified [Timestamp]. The implementation must ensure [wakeup] is called when that time is reached. */
    private val onRequestWakeup: (Timestamp) -> Unit,
    private val onJobError: (job: PumpJob, code: JobErrorCode) -> Unit,
    private val scope: CoroutineScope,
) {
    private val _pendingJobs = MutableStateFlow<List<PumpJob>>(emptyList())
    val pendingJobs: StateFlow<List<PumpJob>> = _pendingJobs.asStateFlow()

    /**
     * Current execution state of the coordinator.
     * Used as a concurrency gate to ensure serial execution of pump commands.
     */
    // Must only be set by [operate]
    private val _pumpCoordinatorState = MutableStateFlow(PumpCoordinatorState.Idle)
    val pumpCoordinatorState: StateFlow<PumpCoordinatorState> = _pumpCoordinatorState.asStateFlow()

    // Must only be set by [operate]
    private val _pumpCommunicationErrorSince = MutableStateFlow<Timestamp>(Timestamp.INVALID)
    val pumpCommunicationErrorSince: StateFlow<Timestamp> = _pumpCommunicationErrorSince.asStateFlow()

    private val _lastConnectionTime = MutableStateFlow(Timestamp.INVALID)
    val lastConnectionTime: StateFlow<Timestamp> = _lastConnectionTime.asStateFlow()

    init {
        setupPump()
        operate()
    }

    private fun setupPump() {
        scope.launch {
            launch {
                pump.isConnected.collect { _ ->
                    // Set last connection time on each change of isConnected -
                    // If we're currently connected, lastConnectionTime is the time when the
                    // connection was established, if we currently disconnected,
                    // lastConnectionTime is the time when the connection was disconnected.
                    _lastConnectionTime.value = Timestamp.now()
                }
            }

            pump.refreshStatus()
            pump.syncHistory()
            // Update more data, if necessary...
            // Here, we have all data from the pump and we are operational.
        }
    }

    /**
     * Main execution loop.
     * Processes the jobs queue if possible. This method might be called from different threads,
     * internally, it schedules its work to be executed asynchronously, exactly once.
     * When the asynchronous coroutine finishes, either the job queue is empty or
     * [pumpCommunicationErrorSince] signals a communication error.
     */
    private fun operate() {
        // Early exit if already running or in error state to avoid unnecessary coroutine launches
        if (_pumpCoordinatorState.value != PumpCoordinatorState.Idle) {
            return
        }
        scope.launch {
            onAcquireBusyState()
            try {
                do {
                    try {
                        // Atomically transition to Running. If another coroutine won the race, exit.
                        if (!_pumpCoordinatorState.compareAndSet(PumpCoordinatorState.Idle, PumpCoordinatorState.Running)) {
                            return@launch
                        }
                        val success = sync()
                        scheduleNextWakeup()
                        if (success) {
                            _pumpCommunicationErrorSince.value = Timestamp.INVALID
                        } else {
                            if (_pumpCommunicationErrorSince.value == Timestamp.INVALID) {
                                _pumpCommunicationErrorSince.value = Timestamp.now()
                            } // Else keep former error time
                            // On communication error, stop the loop and rely on the next
                            // scheduled wakeup, reset or manual trigger to retry.
                            return@launch
                        }
                    } finally {
                        // Reset to Idle only if we were Running (don't overwrite Error state if set by job)
                        _pumpCoordinatorState.compareAndSet(PumpCoordinatorState.Running, PumpCoordinatorState.Idle)
                    }
                    // Catch-up check: To avoid race conditions with issueCommand(), we must verify
                    // if new jobs were added while we were processing. If so, re-enter the loop.
                } while (_pendingJobs.value.any { it.isReady() })
            } finally {
                onReleaseBusyState()
            }
        }
    }

    suspend fun waitForJobsOrError(timeout: Duration = DEFAULT_WAIT_FOR_JOBS_TIMEOUT) {
        // Wait for Idle or Error and all jobs processed (or error in communication)
        val completed = withTimeoutOrNull(timeout) {
            combine(pumpCoordinatorState, pendingJobs, pumpCommunicationErrorSince) { state, jobs, errorSince ->
                (state == PumpCoordinatorState.Idle || state == PumpCoordinatorState.Error) &&
                        (jobs.none { it.isReady() } || errorSince.isValid())
            }.first { it }
        }
        if (completed == null) {
            Log.w(TAG, "waitForJobsOrError timed out after $timeout while waiting for pump jobs to complete.")
        }
    }

    private suspend fun sync(): Boolean {
        checkHeartbeat()
        return processJobs()
    }

    private suspend fun checkHeartbeat() {
        val now = Timestamp.now()
        val lastConn = _lastConnectionTime.value

        if (lastConn + HEARTBEAT_INTERVAL < now) {
            repeat(3) {
                try {
                    pump.refreshStatus()
                    pump.syncHistory()
                    _lastConnectionTime.value = Timestamp.now()
                    return
                } catch (_: Exception) {
                    if (it < 2) delay(1000.milliseconds)
                }
            }
            // If heartbeat failed, we don't necessarily abort everything,
            // but jobs will probably fail too.
        }
    }

    /**
     * Processes all due jobs in the queue, if the pump connection is ready. Else, commands will
     * be deferred until next try.
     * @return `true` if all commands could be executed, else `false`.
     */
    private suspend fun processJobs(): Boolean {
        // Cleanup expired jobs
        val expired = _pendingJobs.value.filter { it.isExpired() }
        if (expired.isNotEmpty()) {
            _pendingJobs.update { it - expired.toSet() }
            expired.forEach { job ->
                val expiredJob = job.copy(lastError = JobErrorCode.Expired)
                onJobError(expiredJob, JobErrorCode.Expired)
            }
        }

        // Execute jobs
        while (true) {
            val job = _pendingJobs.value
                .filter { it.isReady() }
                .minByOrNull { it.nextAttemptAt } ?: return true // All due jobs executed

            val success = tryExecuteJobWithRetries(job)

            if (success) {
                _pendingJobs.update { pending -> pending.filterNot { it.id == job.id } }
            } else {
                if (_pumpCoordinatorState.value == PumpCoordinatorState.Error) {
                    return false
                }
                // Schedule for 1 minute later
                _pendingJobs.update { pending ->
                    pending.map { j ->
                        if (j.id == job.id) {
                            j.copy(
                                retryCount = j.retryCount + 1,
                                nextAttemptAt = Timestamp.now().plusMinutes(1)
                            )
                        } else {
                            j
                        }
                    }
                }
                // Return with pending jobs in the queue
                return false
            }
        }
    }

    private suspend fun tryExecuteJobWithRetries(job: PumpJob): Boolean {
        var connectionRetryCount = 0
        var busyRetryCount = 0

        while (true) {
            try {
                val command = job.command
                executeOnPump(command)
                _lastConnectionTime.value = Timestamp.now()
                return true
            } catch (e: PumpConnectionException) {
                if (connectionRetryCount < MAX_CONNECTION_RETRIES) {
                    connectionRetryCount++
                    delay(RETRY_INTERVAL_MS.milliseconds)
                } else {
                    val errorCode = JobErrorCode.ConnectionFailed(e.message)
                    val failedJob = job.copy(lastError = errorCode)
                    _pendingJobs.update { pending -> pending.map { if (it.id == job.id) failedJob else it } }
                    onJobError(failedJob, errorCode)
                    return false
                }
            } catch (e: PumpCommandException) {
                when (val status = e.status) {
                    PumpStatus.BUSY -> {
                        if (busyRetryCount < BUSY_RETRY_DELAYS_MS.size) {
                            val delayMs = BUSY_RETRY_DELAYS_MS[busyRetryCount]
                            busyRetryCount++
                            delay(delayMs.milliseconds)
                        } else {
                            val errorCode = JobErrorCode.CommandFailed(status, e.message)
                            val failedJob = job.copy(lastError = errorCode)
                            _pendingJobs.update { pending -> pending.map { if (it.id == job.id) failedJob else it } }
                            onJobError(failedJob, errorCode)
                            return false
                        }
                    }
                    PumpStatus.OK, // Should not happen
                    PumpStatus.REJECTED,
                    PumpStatus.INVALID_PARAMETER,
                    PumpStatus.NOT_AUTHORIZED,
                    PumpStatus.DEVICE_ERROR,
                    PumpStatus.ILLEGAL_STATE,
                    PumpStatus.TIMEOUT,
                    PumpStatus.UNKNOWN -> {
                        _pumpCoordinatorState.value = PumpCoordinatorState.Error
                        // Since this is an unexpected situation, we don't remove the job from the queue.
                        // The user must manually remove it in the management interface.
                        val errorCode = JobErrorCode.CommandFailed(status, e.message)
                        val failedJob = job.copy(lastError = errorCode)
                        _pendingJobs.update { pending -> pending.map { if (it.id == job.id) failedJob else it } }
                        onJobError(failedJob, errorCode)
                        return false
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unknown exception while executing pump job", e)
                _pumpCoordinatorState.value = PumpCoordinatorState.Error
                // Since this is an unexpected situation, we don't remove the job from the queue.
                // The user must manually remove it in the management interface.
                val errorCode = JobErrorCode.TechnicalError(e.message)
                val failedJob = job.copy(lastError = errorCode)
                _pendingJobs.update { pending -> pending.map { if (it.id == job.id) failedJob else it } }
                onJobError(failedJob, errorCode)
                return false
            }
        }
    }

    private suspend fun executeOnPump(command: PumpCommand) {
        when (command) {
            is PumpCommand.SyncHistory -> {
                pump.syncHistory()
            }
            is PumpCommand.DeliverBolus -> {
                pump.bolus(command.amount, command.bolusId)
            }
            is PumpCommand.SetTempBasal -> {
                pump.tempBasal(command.percent, command.durationHours)
            }
            is PumpCommand.SetProfile -> {
                pump.setProfile(command.profile)
            }
            is PumpCommand.CancelTempBasal -> pump.cancelTempBasal()
            is PumpCommand.CancelBolus -> pump.stopBolus()
        }
    }

    private fun scheduleNextWakeup() {
        val nextWakeup = calculateNextWakeup()
        if (nextWakeup != null) {
            onRequestWakeup(nextWakeup)
        }
    }

    private fun calculateNextWakeup(): Timestamp? {
        val now = Timestamp.now()
        val minWakeup = now.plusMinutes(1)

        val nextJobTime = _pendingJobs.value
            .filter { !it.isExpired() }
            .minOfOrNull { it.nextAttemptAt }

        val lastConn = _lastConnectionTime.value
        val nextHeartbeatTime = lastConn + HEARTBEAT_INTERVAL

        val earliest = listOfNotNull(nextJobTime, nextHeartbeatTime).minOrNull() ?: return null
        return if (earliest < minWakeup) minWakeup else earliest
    }

    fun hasPendingJobs() = _pendingJobs.value.isNotEmpty()

    fun getPendingJobsCount() = _pendingJobs.value.size

    fun clearPendingJobs() {
        _pendingJobs.value = emptyList()
    }

    /**
     * Puts a new command into our command queue and tries to send all commands to the pump,
     * if possible. If the pump is connected and if it accepts all commands, the command queue
     * will be empty a short time after calling this method. If the pump connection cannot be established
     * or the pump rejects one of the commands, the queue will still contain the failed commands and
     * [pumpCommunicationErrorSince] is filled.
     * Use [waitForJobsOrError] to wait for the operation cycle to finish.
     * @param command The command to execute.
     * @param expiresAt Optional time when the command becomes invalid.
     */
    fun issueCommand(
        command: PumpCommand,
        expiresAt: Timestamp? = null
    ) {
        val job = PumpJob(
            command = command,
            expiresAt = expiresAt
        )

        _pendingJobs.update { it + job }
        operate()
    }

    /**
     * Cancels pending jobs that match the given predicate.
     */
    fun cancelJobs(predicate: (PumpJob) -> Boolean = { true }) {
        _pendingJobs.update { it.filterNot(predicate) }
    }

    /**
     * Triggers a synchronization cycle.
     * This should be called by the system when a requested wakeup time (via [onRequestWakeup]) is reached.
     */
    internal fun wakeup() {
        operate()
    }

    /**
     * Resets the coordinator from an error state back to idle, clears communication errors,
     * schedules the next wakeup, and resumes processing.
     */
    fun reset() {
        if (_pumpCoordinatorState.value == PumpCoordinatorState.Error) {
            _pumpCoordinatorState.value = PumpCoordinatorState.Idle
            _pumpCommunicationErrorSince.value = Timestamp.INVALID
            scheduleNextWakeup()
            operate()
        }
    }

    /**
     * Shuts down the coordinator, cancelling all internal coroutines.
     */
    fun shutdown() {
        scope.cancel()
    }

    companion object {
        val TAG = PumpCoordinator::class.simpleName
        val DEFAULT_WAIT_FOR_JOBS_TIMEOUT: Duration = 30.seconds
        private val HEARTBEAT_INTERVAL = Minutes(15)
        private const val RETRY_INTERVAL_MS: Long = 10_000
        private const val MAX_CONNECTION_RETRIES = 3
        private val BUSY_RETRY_DELAYS_MS = listOf(10_000L, 20_000L, 30_000L)

        fun create(
            pump: InsulinPump,
            /** Callback to acquire a wake-lock in the system to ensure the CPU stays awake during pump communication. */
            onAcquireBusyState: () -> Unit,
            /** Callback to release the wake-lock acquired via [onAcquireBusyState]. */
            onReleaseBusyState: () -> Unit,
            /** Callback to request a system wakeup at the specified [Timestamp]. The implementation must ensure [wakeup] is called when that time is reached. */
            onRequestWakeup: (Timestamp) -> Unit,
            onJobError: (PumpJob, JobErrorCode) -> Unit,
            scope: CoroutineScope
        ): PumpCoordinator {
            return PumpCoordinator(
                pump = pump,
                onAcquireBusyState = onAcquireBusyState,
                onReleaseBusyState = onReleaseBusyState,
                onRequestWakeup = onRequestWakeup,
                onJobError = onJobError,
                scope = scope
            )
        }
    }
}