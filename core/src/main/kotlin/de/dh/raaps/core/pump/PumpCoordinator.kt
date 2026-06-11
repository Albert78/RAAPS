package de.dh.raaps.core.pump

import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.ToDo
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.MetabolicEventsModel
import de.dh.raaps.core.repository.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    val supportsTempBasal: Boolean,
    val supportsExtendedBolus: Boolean,
    val audibleTempBasalReminder: Boolean,
    val deliversBasalWhileBolusing: Boolean,
    val internalTimeManagement: Boolean
)

/**
 * Dynamic state of the connected pump.
 */
data class PumpState(
    val lastConnectionTime: Long = 0,
    val reservoirLevel: InsulinAmount = InsulinAmount.ZERO,
    val batteryLevel: Int? = null,
    val isConnected: Boolean = false
)

/**
 * The PumpCoordinator is the high-level interface to the insulin pump subsystem.
 * It abstracts from the connection state and manages a queue of [PumpJob]s.
 * It ensures that commands from the APS core are eventually executed or invalidated.
 */
class PumpCoordinator(
    private val dataRepository: DataRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val metabolicEventsModel: MetabolicEventsModel,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    var pumpDriver: InsulinPump? = null

    private val _pendingJobs = MutableStateFlow<List<PumpJob>>(emptyList())
    val pendingJobs: StateFlow<List<PumpJob>> = _pendingJobs.asStateFlow()

    private val _pumpState = MutableStateFlow(PumpState())
    val pumpState: StateFlow<PumpState> = _pumpState.asStateFlow()

    // Provided by the driver during initialization
    var pumpInformation: PumpInformation? = null
    var pumpCapabilities: PumpCapabilities? = null

    init {
        startSyncLoop()
    }

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
        // TODO: Persist job via dataRepository if needed across app restarts
    }

    /**
     * Cancels pending jobs that match the given predicate.
     */
    fun cancelJobs(predicate: (PumpJob) -> Boolean = { true }) {
        _pendingJobs.update { it.filterNot(predicate) }
    }

    private fun startSyncLoop() {
        scope.launch {
            while (isActive) {
                if (_pendingJobs.value.isNotEmpty()) {
                    processNextJob()
                }
                delay(10000) // Re-check every 10 seconds
            }
        }
    }

    private suspend fun processNextJob() {
        val job = _pendingJobs.value.firstOrNull { it.isReady() } ?: return

        if (job.isExpired()) {
            _pendingJobs.update { it - job }
            return
        }

        val driver = pumpDriver ?: return // No driver, no progress

        try {
            executeOnDriver(driver, job.command)
            handleSuccess(job)
            _pendingJobs.update { it - job }
        } catch (e: Exception) {
            _pumpState.update { it.copy(isConnected = false) }
            // Job stays in queue for next attempt
        }
    }

    private suspend fun executeOnDriver(driver: InsulinPump, command: PumpCommand) {
        // Translation layer to the specific driver interface
        when (command) {
            is PumpCommand.DeliverBolus -> {
                // driver.deliverBolus(command.amount)
                ToDo.toBeImplemented("driver.deliverBolus")
            }
            is PumpCommand.SetTempBasal -> {
                // driver.setTempBasal(command.unitsPerHour, command.durationMinutes)
                ToDo.toBeImplemented("driver.setTempBasal")
            }
            is PumpCommand.SetProfile -> {
                // driver.setProfile(command.profile)
                ToDo.toBeImplemented("driver.setProfile")
            }
            is PumpCommand.CancelTempBasal -> {
                // driver.cancelTempBasal()
                ToDo.toBeImplemented("driver.cancelTempBasal")
            }
            is PumpCommand.CancelBolus -> {
                // driver.cancelBolus()
                ToDo.toBeImplemented("driver.cancelBolus")
            }
        }
    }

    private suspend fun handleSuccess(job: PumpJob) {
        _pumpState.update { it.copy(isConnected = true, lastConnectionTime = System.currentTimeMillis()) }

        when (val cmd = job.command) {
            is PumpCommand.DeliverBolus -> {
                // metabolicEventsModel.addInsulinApplication(...)
                ToDo.toBeImplemented("Record successful bolus in MetabolicEventsModel")
            }
            else -> {
                // Log other successes
            }
        }
    }

    companion object {
        fun create(
            dataRepository: DataRepository,
            appPreferencesRepository: AppPreferencesRepository,
            metabolicEventsModel: MetabolicEventsModel
        ): PumpCoordinator {
            return PumpCoordinator(dataRepository, appPreferencesRepository, metabolicEventsModel)
        }
    }
}