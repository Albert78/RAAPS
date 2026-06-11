package de.dh.raaps.core.pump

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.ToDo
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    val executeAfter: Timestamp = createdAt,
    val expiresAt: Timestamp? = null
) {
    fun isExpired(): Boolean = expiresAt?.let { it < Timestamp.now() } ?: false
    fun isReady(): Boolean = executeAfter <= Timestamp.now()
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

/**
 * Dynamic state of the connected pump.
 */
data class PumpState(
    val lastConnectionTime: Long = 0,
    val reservoirLevel: InsulinAmount? = null,
    val batteryLevel: Int? = null,
    val isConnected: Boolean = false
)

/**
 * The PumpCoordinator is the high-level interface to the insulin pump subsystem.
 * It abstracts from the connection state and manages a queue of [PumpJob]s.
 * It ensures that commands from the APS core are eventually executed or invalidated.
 */
class PumpCoordinator(
    private val treatmentRepository: TreatmentRepository,
    private val onAcquireBusyState: () -> Unit,
    private val onReleaseBusyState: () -> Unit,
    private val onRequestWakeup: (Timestamp) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    var pumpDriver: InsulinPump? = null

    private val _pendingJobs = MutableStateFlow<List<PumpJob>>(emptyList())
    val pendingJobs: StateFlow<List<PumpJob>> = _pendingJobs.asStateFlow()

    private val _pumpState = MutableStateFlow(PumpState())
    val pumpState: StateFlow<PumpState> = _pumpState.asStateFlow()

    // Provided by the driver during initialization
    var pumpInformation: PumpInformation? = null
    var pumpCapabilities: PumpCapabilities? = null

    /**
     * Issues a new command to the pump.
     * @param command The command to execute.
     * @param executeAfter Optional time when the command should be executed.
     * @param expirationMinutes Optional time after which the command becomes invalid.
     */
    fun issueCommand(
        command: PumpCommand,
        executeAfter: Timestamp = Timestamp.now(),
        expirationMinutes: Minutes? = null
    ) {
        val job = PumpJob(
            command = command,
            executeAfter = executeAfter,
            expiresAt = expirationMinutes?.let { executeAfter.plusMinutes(it.value.toInt()) }
        )

        _pendingJobs.update { it + job }
    }

    /**
     * Cancels pending jobs that match the given predicate.
     */
    fun cancelJobs(predicate: (PumpJob) -> Boolean = { true }) {
        _pendingJobs.update { it.filterNot(predicate) }
    }

    internal fun wakeup() {
        handleJobs()
    }

    enum class JobResult {
        Success,
        NoJob,
        ConfigurationError,
        JobException
    }

    fun handleJobs() {
        scope.launch {
            onAcquireBusyState()
            try {
                while (_pendingJobs.value.isNotEmpty()) {
                    val jobResult = processNextJob()
                    if (jobResult == JobResult.ConfigurationError) {
                        _TODO()
                        // TODO: Notify user
                        break
                    }
                    if (jobResult == JobResult.JobException) {
                        _TODO()
                        // TODO: Try 2 more times, then try again 2 more times, 5 minutes apart (3 tries each), and then report the error
                        break
                    }
                }
                delay(10000) // Re-check every 10 seconds
            } finally {
                onReleaseBusyState()
            }
        }
    }

    private suspend fun processNextJob(): JobResult {
        val job = _pendingJobs.value.firstOrNull { it.isReady() } ?: return JobResult.NoJob

        if (job.isExpired()) {
            _pendingJobs.update { it - job }
            return JobResult.Success
        }

        val driver = pumpDriver ?: return JobResult.ConfigurationError // No driver, no progress

        try {
            executeOnDriver(driver, job.command)
            handleSuccess(job)
            _pendingJobs.update { it - job }
            return JobResult.Success
        } catch (e: Exception) {
            _pumpState.update { it.copy(isConnected = false) }
            // Job stays in queue for next attempt
            return JobResult.JobException
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
        _pumpState.update { it.copy(isConnected = true, lastConnectionTime = System.currentTimeMillis()) }

        when (val cmd = job.command) {
            is PumpCommand.DeliverBolus -> {
                // treatmentRepository.addInsulinApplication(...)
                ToDo.toBeImplemented("Record successful bolus in TreatmentRepository")
            }
            else -> {
                // Log other successes
            }
        }
    }

    companion object {
        fun create(
            treatmentRepository: TreatmentRepository,
            onAcquireBusyState: () -> Unit,
            onReleaseBusyState: () -> Unit,
            onRequestWakeup: (timestamp: Timestamp) -> Unit
        ): PumpCoordinator {
            return PumpCoordinator(
                treatmentRepository = treatmentRepository,
                onAcquireBusyState = onAcquireBusyState,
                onReleaseBusyState = onReleaseBusyState,
                onRequestWakeup = onRequestWakeup
            )
        }
    }
}