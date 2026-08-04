package de.dh.raaps.core.pump

import android.content.Intent
import android.util.Log
import de.dh.raaps.common.model.InsulinHistory
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.system.SystemWakeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Implementation of [PumpManager].
 */
class PumpManagerImpl(
    private val scope: CoroutineScope,
    private val wakeService: SystemWakeService
) : PumpManager {

    private val _pumpIssues = MutableStateFlow<Set<PumpIssue>>(emptySet())
    override val pumpIssues: StateFlow<Set<PumpIssue>> = _pumpIssues.asStateFlow()

    override var pumpCoordinator: PumpCoordinator? = null
        private set

    private var pumpMonitorJob: Job? = null
    private var historyUpdateListener: (suspend (InsulinHistory) -> Unit)? = null

    init {
        wakeService.registerHandler(WAKE_TAG, this)
    }

    override var insulinPump: InsulinPump? = null
        set(value) {
            field = value
            pumpMonitorJob?.cancel()
            pumpCoordinator = if (value == null) {
                null
            } else {
                val pc = PumpCoordinator.create(
                    pump = value,
                    onAcquireBusyState = { wakeService.acquireBusyState(WAKE_TAG) },
                    onReleaseBusyState = { wakeService.releaseBusyState(WAKE_TAG) },
                    onRequestWakeup = { timestamp -> wakeService.scheduleWakeup(WAKE_TAG, WAKEUP_PUMP_COORDINATOR, timestamp) },
                    onJobError = { job, jobErrorCode -> handleJobError(job, jobErrorCode) },
                )
                pumpMonitorJob = scope.launch {
                    launch {
                        pc.lastConnectionTime.collect { time ->
                            if (time != Timestamp.INVALID) {
                                removeIssue(PumpIssue.ConnectionMissing)
                            }
                        }
                    }
                    launch {
                        pc.pump.pumpStatus.collect { status ->
                            if (status.pumpSuspended) {
                                addIssue(PumpIssue.Inoperative)
                            } else {
                                removeIssue(PumpIssue.Inoperative)
                            }
                        }
                    }
                    // Sync history
                    launch {
                        pc.pump.history.collect { history ->
                            history?.let { historyUpdateListener?.invoke(it) }
                        }
                    }
                }
                pc
            }
        }

    override suspend fun issueCommand(command: PumpCommand, isCancelableAPSCommand: Boolean) {
        pumpCoordinator?.issueCommand(command, isCancelableAPSCommand)
    }

    override fun cancelJobs(predicate: (PumpJob) -> Boolean) {
        pumpCoordinator?.cancelJobs(predicate)
    }

    override suspend fun waitForIdle() {
        pumpCoordinator?.waitForIdle()
    }

    override fun wakeup() {
        pumpCoordinator?.wakeup()
    }

    override fun hasPendingJobs(): Boolean {
        return pumpCoordinator?.hasPendingJobs() ?: false
    }

    override fun setOnHistoryUpdateListener(listener: suspend (InsulinHistory) -> Unit) {
        historyUpdateListener = listener
    }

    override fun onWakeup(wakeupId: UInt?, intent: Intent?) {
        if (wakeupId == WAKEUP_PUMP_COORDINATOR) {
            wakeup()
        }
    }

    private fun addIssue(issue: PumpIssue) {
        if (issue !in _pumpIssues.value) {
            _pumpIssues.value += issue
        }
    }

    private fun removeIssue(issue: PumpIssue) {
        if (issue in _pumpIssues.value) {
            _pumpIssues.value -= issue
        }
    }

    private fun handleJobError(job: PumpJob, jobErrorCode: JobErrorCode) {
        when (jobErrorCode) {
            JobErrorCode.Expired -> addIssue(PumpIssue.ConnectionMissing)
            else -> {
                Log.e(TAG, "Pump job error: $jobErrorCode for job $job")
                addIssue(PumpIssue.Other)
            }
        }
    }

    companion object {
        private const val TAG = "PumpManager"
        private const val WAKE_TAG = "PUMP"
        val WAKEUP_PUMP_COORDINATOR = 0u
    }
}