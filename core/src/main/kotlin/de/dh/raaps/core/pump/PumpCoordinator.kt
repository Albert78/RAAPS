package de.dh.raaps.core.pump

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.TreatmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/**
 * Commands that can be sent to the pump.
 */
sealed class PumpCommand {
    data class SetProfile(val profile: TherapyData) : PumpCommand()
    data class SetTempBasal(val unitsPerHour: Double, val durationMinutes: Minutes) : PumpCommand()
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
    val expiresAt: Timestamp? = null
) {
    fun isExpired(): Boolean = expiresAt?.let { it < Timestamp.now() } ?: false
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

interface PumpState {
    object Initializing : PumpState

    /**
     * Dynamic state of the connected pump.
     */
    data class Normal(
        val lastConnectionTime: Long = 0,
        val reservoirLevel: InsulinAmount? = null,
        val batteryLevel: Int? = null,
        val isConnected: Boolean = false
    ) : PumpState

    object NoPump : PumpState
    object Suspended : PumpState
}

/**
 * The PumpCoordinator is the high-level interface to the insulin pump subsystem.
 * It abstracts from the connection state and manages a queue of [PumpJob]s.
 * It ensures that commands from the APS core are eventually executed or invalidated.
 *
 * The lifetime of a PumpCoordinator is as big as the enclosing APS instance. During its lifetime,
 * pumps can be attached and removed. While a pump is attached, the pump loop runs and dispatches
 * pump commands, even if the pump is suspended or erroneous.
 */
class PumpCoordinator(
    private val treatmentRepository: TreatmentRepository,
    private val onAcquireBusyState: () -> Unit,
    private val onReleaseBusyState: () -> Unit,
    private val onJobError: (job: PumpJob, code: JobErrorCode) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _pumpCoordinatorState = MutableStateFlow(PumpCoordinatorState.Idle)
    val pumpCoordinatorState: StateFlow<PumpCoordinatorState> = _pumpCoordinatorState.asStateFlow()

    private var pumpDriver: InsulinPump? = null

    private val _pumpState = MutableStateFlow<PumpState>(PumpState.Initializing)
    val pumpState: StateFlow<PumpState> = _pumpState.asStateFlow()

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
        startJobLoop()
    }

    /**
     * Stops this PumpCoordinator and removes the pump.
     */
    suspend fun stop() {
        _TODO()
        // Detach pump listeners

        pumpDriver = null // This will automatically stop our job loop
        _pumpState.value = PumpState.NoPump

        // Wait for job loop to finish
        pumpCoordinatorState.first { it == PumpCoordinatorState.Idle }
    }

    private suspend fun initializePump(driver: InsulinPump) {
        pumpDriver = driver
        _pumpState.value = PumpState.Initializing
        _TODO()
        // Query Pump information, capabilities etc.
        // Listen to Pump state
        // Listen to notifications:
        // - Bolus delivered -> add insulin application to treatmentRepository
        // - Alarms -> onPumpAlarm
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
    }

    /**
     * Cancels pending jobs that match the given predicate.
     */
    fun cancelJobs(predicate: (PumpJob) -> Boolean = { true }) {
        _pendingJobs.update { it.filterNot(predicate) }
    }

    fun startJobLoop() {
        scope.launch {
            _pumpCoordinatorState.value = PumpCoordinatorState.Running
            try {
                jobLoop()
            } finally {
                _pumpCoordinatorState.value = PumpCoordinatorState.Idle
            }
        }
    }

    suspend fun jobLoop() {
        while (true) {
            val driver = pumpDriver
            if (driver == null) {
                _pumpState.value = PumpState.NoPump
            }
            val pumpState = pumpState.value
            if (pumpState == PumpState.NoPump) {
                return
            }

            if (pumpState is PumpState.Normal && _pendingJobs.value.isNotEmpty()) {
                onAcquireBusyState()
                try {
                    while (_pendingJobs.value.isNotEmpty()) {
                        val job = _pendingJobs.value.firstOrNull() ?: break

                        if (job.isExpired()) {
                            _pendingJobs.update { it - job }
                            onJobError(job, JobErrorCode.Expired)
                        }

                        _TODO()
                        // Increment Job retry state
                        try {
                            executeOnDriver(driver!!, job.command)
                            handleSuccess(job)
                            _pendingJobs.update { it - job }
                        } catch (_: Exception) {
                            _pumpState.update {
                                if (it is PumpState.Normal) {
                                    it.copy(isConnected = false)
                                } else {
                                    it
                                }
                            }
                            // Job stays in queue for next attempt
                        }
                    }
                } finally {
                    onReleaseBusyState()
                }
            }
            delay(10000) // Re-check every 10 seconds
        }
    }

    private suspend fun executeOnDriver(driver: InsulinPump, command: PumpCommand) {
        // Translation layer to the driver interface
        when (command) {
            is PumpCommand.DeliverBolus -> {
                driver.deliverBolus(command.amount)
            }
            is PumpCommand.SetTempBasal -> {
                driver.setTempBasal(command.unitsPerHour, command.durationMinutes)
            }
            is PumpCommand.SetProfile -> {
                driver.setProfile(command.profile)
            }
            is PumpCommand.CancelTempBasal -> {
                driver.cancelTempBasal()
            }
            is PumpCommand.CancelBolus -> {
                driver.cancelBolus()
            }
        }
    }

    private suspend fun handleSuccess(job: PumpJob) {
        var pumpState = _pumpState.value
        if (pumpState is PumpState.Normal) {
           pumpState = pumpState.copy(isConnected = true, lastConnectionTime = System.currentTimeMillis())
        } else {
            pumpState = PumpState.Normal(isConnected = true, lastConnectionTime = System.currentTimeMillis())
        }
        _pumpState.value = pumpState
    }

    companion object {
        fun create(
            treatmentRepository: TreatmentRepository,
            onAcquireBusyState: () -> Unit,
            onReleaseBusyState: () -> Unit,
            onJobError: (PumpJob, JobErrorCode) -> Unit,
        ): PumpCoordinator {
            return PumpCoordinator(
                treatmentRepository = treatmentRepository,
                onAcquireBusyState = onAcquireBusyState,
                onReleaseBusyState = onReleaseBusyState,
                onJobError = onJobError,
            )
        }
    }
}