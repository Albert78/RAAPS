package de.dh.raaps.core.pump

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.InsulinPumpStatus
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.TreatmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class PumpCoordinatorState {
    Idle, Running
}

data object EmptyInsulinPumpStatus : InsulinPumpStatus {
    override val pumpSuspended: Boolean = false
    override val batteryRemainingPercent: Int = 0
    override val reservoirRemainingUnits: Double = 0.0
    override val lastSyncTimestamp: Long = 0
}

/**
 * Commands that can be sent to the pump.
 */
sealed class PumpCommand {
    data class SetProfile(val profile: TherapyData) : PumpCommand()
    data class SetTempBasal(val percent: Int, val durationHours: Int) : PumpCommand()
    object CancelTempBasal : PumpCommand()
    data class DeliverBolus(val amount: InsulinAmount) : PumpCommand()
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
    val nextAttemptAt: Timestamp = createdAt
) {
    fun isExpired(): Boolean = expiresAt?.let { it < Timestamp.now() } ?: false
    fun isReady(): Boolean = nextAttemptAt <= Timestamp.now()
}

enum class JobErrorCode {
    Expired
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
 * and continues to dispatch commands even if pump drivers are detached or replaced,
 * effectively decoupling the core logic from the specific driver's lifetime.
 */
// TODO: Multithreading/thread allocation
// TODO: Notifications from pump; persist insulin applications in repository
// TODO: Show pending pump jobs in UI
@OptIn(ExperimentalCoroutinesApi::class)
class PumpCoordinator(
    private val treatmentRepository: TreatmentRepository,
    private val onAcquireBusyState: () -> Unit,
    private val onReleaseBusyState: () -> Unit,
    private val onRequestWakeup: (Timestamp) -> Unit,
    private val onJobError: (job: PumpJob, code: JobErrorCode) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _pumpCoordinatorState = MutableStateFlow(PumpCoordinatorState.Idle)
    val pumpCoordinatorState: StateFlow<PumpCoordinatorState> = _pumpCoordinatorState.asStateFlow()

    private val activePump =
        MutableStateFlow<InsulinPump?>(null)

    private val _pendingJobs = MutableStateFlow<List<PumpJob>>(emptyList())
    val pendingJobs: StateFlow<List<PumpJob>> = _pendingJobs.asStateFlow()

    private val _lastConnectionTime = MutableStateFlow(Timestamp.INVALID)
    val lastConnectionTime: StateFlow<Timestamp> = _lastConnectionTime.asStateFlow()

    val isConnected =
        activePump.flatMapLatest {
            it?.isConnected ?: flowOf(false)
        }.stateIn(scope, SharingStarted.Eagerly, false)

    // ----------------------------------------- Pump data ----------------------------------------

    val hardwareInformation =
        activePump.flatMapLatest {
            it?.hardwareInformation ?: flowOf(null)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val pumpCapabilities =
        activePump.flatMapLatest {
            it?.pumpCapabilities ?: flowOf(null)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val pumpStatus =
        activePump.flatMapLatest {
            it?.pumpStatus ?: flowOf(null)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val alerts =
        activePump.flatMapLatest {
            it?.alerts ?: flowOf(null)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val basalStatus =
        activePump.flatMapLatest {
            it?.basalStatus ?: flowOf(null)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val basalHistory =
        activePump.flatMapLatest {
            it?.basalHistory ?: flowOf(null)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val bolusHistory =
        activePump.flatMapLatest {
            it?.bolusHistory ?: flowOf(null)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    // --------------------------------------------------------------------------------------------

    /**
     * Sets the pump and initializes this [PumpCoordinator].
     * @param driver The pump driver to use.
     */
    suspend fun initialize(driver: InsulinPump) {
        stop()
        initializePump(driver)
        startPumpConnection()
    }

    /**
     * Stops this PumpCoordinator and removes the pump.
     */
    suspend fun stop() {
        activePump.value = null

        // Wait for job loop to finish
        waitForIdle()
    }

    suspend fun waitForIdle() {
        pumpCoordinatorState.first { it == PumpCoordinatorState.Idle }
    }

    private suspend fun initializePump(pump: InsulinPump) {
        activePump.value = pump

        scope.launch {
            // Sync history
            launch {
                pump.bolusHistory.collect { /* TODO: Sync with repository */ }
            }
            launch {
                pump.basalHistory.collect { /* TODO: Sync with repository */ }
            }
            launch {
                pump.isConnected.collect { _ ->
                    // Set last connection time on each change of isConnected -
                    // If we're currently connected, lastConnectionTime is the time when the
                    // connection was established, if we currently disconnected,
                    // lastConnectionTime is the time when the connection was disconnected.
                    _lastConnectionTime.value = Timestamp.now()
                }
            }
        }

        pump.refreshStatus()
        pump.syncHistory()
        // Update more data, if necessary...
        // Here, we have all data from the pump and we are operational.
    }

    fun clearPendingJobs() {
        _pendingJobs.value = emptyList()
    }

    /**
     * Issues a new command to the pump.
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
        wakeup()
    }

    /**
     * Cancels pending jobs that match the given predicate.
     */
    fun cancelJobs(predicate: (PumpJob) -> Boolean = { true }) {
        _pendingJobs.update { it.filterNot(predicate) }
    }

    fun wakeup() {
        if (pumpCoordinatorState.value == PumpCoordinatorState.Idle) {
            startPumpConnection()
        }
    }

    private fun startPumpConnection() {
        scope.launch {
            onAcquireBusyState()
            _pumpCoordinatorState.value = PumpCoordinatorState.Running
            try {
                sync()
                scheduleNextWakeup()
            } finally {
                _pumpCoordinatorState.value = PumpCoordinatorState.Idle
                onReleaseBusyState()
            }
        }
    }

    private suspend fun sync() {
        val pump = activePump.value ?: return

        checkHeartbeat(pump)
        processJobs(pump)
    }

    private suspend fun checkHeartbeat(pump: InsulinPump) {
        val now = Timestamp.now()
        val lastConn = _lastConnectionTime.value

        if (lastConn + HEARTBEAT_INTERVAL < now) {
            repeat(3) {
                try {
                    pump.refreshStatus()
                    pump.syncHistory()
                    return
                } catch (_: Exception) {
                    if (it < 2) delay(1000)
                }
            }
            // If heartbeat failed, we don't necessarily abort everything,
            // but jobs will probably fail too.
        }
    }

    private suspend fun processJobs(pump: InsulinPump) {
        while (true) {
            val job = _pendingJobs.value
                .filter { it.isReady() && !it.isExpired() }
                .minByOrNull { it.nextAttemptAt } ?: break

            val success = tryExecuteJobWithRetries(pump, job)
            if (success) {
                _pendingJobs.update { it - job }
            } else {
                // Schedule for 1 minute later
                val updatedJob = job.copy(
                    retryCount = job.retryCount + 3,
                    nextAttemptAt = Timestamp.now().plusMinutes(1)
                )
                _pendingJobs.update { (it - job) + updatedJob }
                // Break loop and try again next time
                break
            }
        }

        // Cleanup expired jobs
        val expired = _pendingJobs.value.filter { it.isExpired() }
        if (expired.isNotEmpty()) {
            _pendingJobs.update { it - expired.toSet() }
            expired.forEach { onJobError(it, JobErrorCode.Expired) }
        }
    }

    private suspend fun tryExecuteJobWithRetries(pump: InsulinPump, job: PumpJob): Boolean {
        repeat(3) {
            try {
                executeOnPump(pump, job.command)
                return true
            } catch (_: Exception) {
                if (it < 2) delay(RETRY_INTERVAL_MS)
            }
        }
        return false
    }

    private suspend fun executeOnPump(pump: InsulinPump, command: PumpCommand) {
        when (command) {
            is PumpCommand.DeliverBolus -> pump.bolus(command.amount.iu)
            is PumpCommand.SetTempBasal -> {
                pump.tempBasal(command.percent, command.durationHours)
            }
            is PumpCommand.SetProfile -> {
                // TODO: SetProfile not supported by current InsulinPump interface
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

    companion object {
        private val HEARTBEAT_INTERVAL = Minutes(15)
        private const val RETRY_INTERVAL_MS: Long = 10_000

        fun create(
            treatmentRepository: TreatmentRepository,
            onAcquireBusyState: () -> Unit,
            onReleaseBusyState: () -> Unit,
            onRequestWakeup: (Timestamp) -> Unit,
            onJobError: (PumpJob, JobErrorCode) -> Unit,
        ): PumpCoordinator {
            return PumpCoordinator(
                treatmentRepository = treatmentRepository,
                onAcquireBusyState = onAcquireBusyState,
                onReleaseBusyState = onReleaseBusyState,
                onRequestWakeup = onRequestWakeup,
                onJobError = onJobError,
            )
        }
    }
}