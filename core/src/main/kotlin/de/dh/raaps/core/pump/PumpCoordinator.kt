package de.dh.raaps.core.pump

import de.dh.raaps.common.model.BasalStatus
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.InsulinPumpStatus
import de.dh.raaps.common.model.PumpAlerts
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.TreatmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class PumpCoordinatorState {
    Idle, Running
}

data object EmptyInsulinPumpStatus : InsulinPumpStatus {
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
 * Static information about the bonded pump.
 */
data class PumpInformation(
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
    val pumpDescription: String
)

/**
 * Technical specification and hardware characteristics.
 */
data class PumpCapabilities(
    val minBasalIncrement: Double,
    val minBolusIncrement: Double,
    val maxBolusSize: Double,
    // TODO: Continue list for sensible capability values
//    val supportsTempBasal: Boolean,
//    val supportsExtendedBolus: Boolean,
//    val audibleTempBasalReminder: Boolean,
//    val deliversBasalWhileBolusing: Boolean,
//    val internalTimeManagement: Boolean
)

enum class PumpState {
    Initializing, Normal, NoPump, Suspended
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

    private var pumpDriver: InsulinPump? = null
    private var flowCollectionJob: Job? = null

    private val _pumpState = MutableStateFlow(PumpState.Initializing)
    val pumpState: StateFlow<PumpState> = _pumpState.asStateFlow()

    private val _lastConnectionTime = MutableStateFlow(Timestamp.INVALID)
    val lastConnectionTime: StateFlow<Timestamp> = _lastConnectionTime.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _pumpStatus = MutableStateFlow<InsulinPumpStatus>(EmptyInsulinPumpStatus)
    val pumpStatus: StateFlow<InsulinPumpStatus> = _pumpStatus.asStateFlow()

    private val _reservoirLevel = MutableStateFlow<InsulinAmount?>(null)
    val reservoirLevel: StateFlow<InsulinAmount?> = _reservoirLevel.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _alerts = MutableStateFlow(PumpAlerts())
    val alerts: StateFlow<PumpAlerts> = _alerts.asStateFlow()

    private val _basalStatus = MutableStateFlow(BasalStatus())
    val basalStatus: StateFlow<BasalStatus> = _basalStatus.asStateFlow()

    // TODO: Should we mirror these from the driver?
//    val basalHistory: StateFlow<List<BasalHistoryPoint>>
//    val bolusHistory: StateFlow<List<BolusHistoryPoint>>

    // Provided by the driver during initialization
    var pumpInformation: PumpInformation? = null
    var pumpCapabilities: PumpCapabilities? = null

    private val _pendingJobs = MutableStateFlow<List<PumpJob>>(emptyList())
    val pendingJobs: StateFlow<List<PumpJob>> = _pendingJobs.asStateFlow()

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
        flowCollectionJob?.cancel()
        flowCollectionJob = null
        pumpDriver = null // This will automatically stop our job loop
        _pumpState.value = PumpState.NoPump
        _isConnected.value = false

        // Wait for job loop to finish
        pumpCoordinatorState.first { it == PumpCoordinatorState.Idle }
    }

    private suspend fun initializePump(driver: InsulinPump) {
        pumpDriver = driver
        _pumpState.value = PumpState.Initializing

        flowCollectionJob?.cancel()
        flowCollectionJob = scope.launch {
            // Collect status
            launch {
                driver.status.collect { status ->
                    _pumpStatus.value = status
                    handleStatusSuccess(status)
                }
            }

            // Collect basal status
            launch {
                driver.basal.collect { basal ->
                    _basalStatus.value = basal
                }
            }

            // Collect alerts
            launch {
                driver.alerts.collect { alerts ->
                    _alerts.value = alerts
                }
            }

            // Sync history
            launch {
                driver.bolusHistory.collect { /* TODO: Sync with repository */ }
            }
            launch {
                driver.basalHistory.collect { /* TODO: Sync with repository */ }
            }
            launch {
                driver.refreshStatus()
                driver.syncHistory()
            }
            _pumpState.value = PumpState.Normal
        }
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
        val driver = pumpDriver ?: return

        checkHeartbeat(driver)
        processJobs(driver)
    }

    private suspend fun checkHeartbeat(driver: InsulinPump) {
        val now = Timestamp.now()
        val lastConn = _lastConnectionTime.value

        if (lastConn + HEARTBEAT_INTERVAL < now) {
            repeat(3) {
                try {
                    driver.refreshStatus()
                    driver.syncHistory()
                    return
                } catch (_: Exception) {
                    if (it < 2) delay(1000)
                }
            }
            // If heartbeat failed, we don't necessarily abort everything,
            // but jobs will probably fail too.
        }
    }

    private suspend fun processJobs(driver: InsulinPump) {
        while (true) {
            val job = _pendingJobs.value
                .filter { it.isReady() && !it.isExpired() }
                .minByOrNull { it.nextAttemptAt } ?: break

            val success = tryExecuteJobWithRetries(driver, job)
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

    private suspend fun tryExecuteJobWithRetries(driver: InsulinPump, job: PumpJob): Boolean {
        repeat(3) {
            try {
                executeOnDriver(driver, job.command)
                handleSuccess(job)
                return true
            } catch (_: Exception) {
                if (it < 2) delay(RETRY_INTERVAL_MS)
            }
        }
        return false
    }

    private suspend fun executeOnDriver(driver: InsulinPump, command: PumpCommand) {
        when (command) {
            is PumpCommand.DeliverBolus -> driver.bolus(command.amount.iu)
            is PumpCommand.SetTempBasal -> {
                driver.tempBasal(command.percent, command.durationHours)
            }
            is PumpCommand.SetProfile -> {
                // TODO: SetProfile not supported by current InsulinPump interface
            }
            is PumpCommand.CancelTempBasal -> driver.cancelTempBasal()
            is PumpCommand.CancelBolus -> driver.stopBolus()
        }
    }

    private fun handleStatusSuccess(status: InsulinPumpStatus) {
        val now = Timestamp.now()
        _lastConnectionTime.value = now
        _reservoirLevel.value = InsulinAmount(status.reservoirRemainingUnits)
        _batteryLevel.value = status.batteryRemainingPercent
        _isConnected.value = true
        _pumpState.value = PumpState.Normal
    }

    private fun handleSuccess(job: PumpJob) {
        val now = Timestamp.now()
        _isConnected.value = true
        _lastConnectionTime.value = now
        _pumpState.value = PumpState.Normal
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