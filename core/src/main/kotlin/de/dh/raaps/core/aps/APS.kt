package de.dh.raaps.core.aps

import android.content.Context
import android.content.Intent
import android.util.Log
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.pump.JobErrorCode
import de.dh.raaps.core.pump.PumpCommand
import de.dh.raaps.core.pump.PumpCoordinator
import de.dh.raaps.core.pump.PumpJob
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository
import de.dh.raaps.core.system.SystemWakeService
import de.dh.raaps.core.system.WakeupHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

enum class ApsIssue {
    /**
     * No recent glucose value available, the loop cannot calculate new treatments.
     */
    StaleBG,

    /**
     * The pump connection is missing, APS Core is not able to do its work.
     * The work will be continued when the connection is available again.
     */
    PumpConnectionMissing,

    /**
     * The pump is connected but in a state where it cannot deliver insulin (e.g. suspended,
     * battery empty, reservoir empty, hardware error).
     */
    PumpInoperative,

    /**
     * Any other issue that prevents the core from working.
     */
    Other
}

sealed class ApsRecommendation {
    data class Carbs(val amountInGram: Int) : ApsRecommendation()
    data class Bolus(val amount: InsulinAmount) : ApsRecommendation()
}

/**
 * APS system facade for the access from outside (UI, ...).
 * Manages threading and plugin lifecycles, ensuring all calls to the core are serialized
 * on a single background thread.
 */
class APS(
    val glucoseRepository: GlucoseRepository,
    val therapyRepository: TherapyRepository,
    val treatmentRepository: TreatmentRepository,
    val appPreferencesRepository: AppPreferencesRepository,
    val therapyManager: TherapyManager,
    val wakeService: SystemWakeService,
    val context: Context
) : WakeupHandler {
    // Threading: Single background thread to avoid race conditions in the core logic
    private val apsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val apsScope = CoroutineScope(apsDispatcher + SupervisorJob())

    init {
        instance = this
        wakeService.registerHandler(WAKE_TAG, this)
    }

    var pumpCoordinator: PumpCoordinator? = null
    private var pumpMonitorJob: Job? = null

    // Computation Core: Pure logic and state, completely thread-agnostic
    private val core: Core = Core.createProductiveCore(
        therapyManager = therapyManager,
        glucoseRepository = glucoseRepository,
        treatmentRepository = treatmentRepository,
        appPreferencesRepository = appPreferencesRepository,

        onDataUpdated = { emitDataUpdateEvent() },
        onCoreStateChanged = { emitCoreStateChangedEvent() },
        onAcquireBusyState = { acquireBusyState() },
        onReleaseBusyState = { releaseBusyState() },

        onCancelInsulinJobs = { coreCancelInsulinJobs() },
        onDeliverBolus = { amount -> deliverBolus(amount) },
        onCheckZeroTemp = { canIssueZeroTemp() },
        onZeroTemp = { durationInHours -> issueZeroTemp(durationInHours) },
        onCarbsHint = { amountInGram -> issueCarbHint(amountInGram) },
        onWaitForAndResetInsulinJobs = { waitForAndResetInsulinJobs() }
    )

    // Plugins & Active Jobs
    private var glucoseJob: Job? = null
    var glucoseSource: GlucoseSource? = null
        set(value) {
            field?.stop()
            field = value
            field?.start()
            restartGlucosePipeline()
        }

    var insulinPump: InsulinPump? = null
        set(value) {
            field = value
            inAPSThread {
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
                    pumpMonitorJob = launch {
                        launch {
                            pc.lastConnectionTime.collect { time ->
                                if (time != Timestamp.INVALID) {
                                    removeIssue(ApsIssue.PumpConnectionMissing)
                                }
                            }
                        }
                        launch {
                            pc.pump.pumpStatus.collect { status ->
                                if (status.pumpSuspended) {
                                    addIssue(ApsIssue.PumpInoperative)
                                } else {
                                    removeIssue(ApsIssue.PumpInoperative)
                                }
                            }
                        }
                        // Sync history
                        launch {
                            pc.pump.bolusHistory.collect { core.updatePumpBolusHistory(it) }
                        }
                        launch {
                            pc.pump.basalHistory.collect { core.updatePumpActualBasalHistory(it) }
                        }
                    }
                    pc
                }
            }
        }

    // Observers (Updated by the internal core, read by the facade/UI)
    private val _lastDataTime = MutableStateFlow<Timestamp>(Timestamp(0))
    val lastDataTime: StateFlow<Timestamp> = _lastDataTime.asStateFlow()

    private val _coreState = MutableStateFlow<CoreState>(CoreState.Initializing)
    /**
     * State of the core.
     * Watch the core state to be notified when it changes, e.g.:
     * ```
     * aps.coreState.first { it is CoreState.Active }
     * ```
     */
    val coreState: StateFlow<CoreState> = _coreState.asStateFlow()

    private val _apsMode = MutableStateFlow<ApsMode>(ApsMode.Manual)
    val apsMode: StateFlow<ApsMode> = _apsMode.asStateFlow()

    private val _apsIssues = MutableStateFlow<Set<ApsIssue>>(emptySet())
    /**
     * Active issues of the APS.
     */
    val apsIssues: StateFlow<Set<ApsIssue>> = _apsIssues.asStateFlow()

    private val _recommendations = MutableStateFlow<List<ApsRecommendation>>(emptyList())
    /**
     * Active recommendations of the APS.
     */
    val recommendations: StateFlow<List<ApsRecommendation>> = _recommendations.asStateFlow()

    private fun addIssue(issue: ApsIssue) {
        if (issue !in _apsIssues.value) {
            _apsIssues.value += issue
        }
    }

    private fun removeIssue(issue: ApsIssue) {
        if (issue in _apsIssues.value) {
            _apsIssues.value -= issue
        }
    }

    private fun handleJobError(job: PumpJob, jobErrorCode: JobErrorCode) {
        when (jobErrorCode) {
            JobErrorCode.Expired -> addIssue(ApsIssue.PumpConnectionMissing)
            else -> {
                // TODO: Log
                addIssue(ApsIssue.Other)
            }
        }
    }

    /**
     * Executes the given block on the internal APS thread.
     */
    private fun inAPSThread(block: suspend CoroutineScope.() -> Unit): Job {
        return apsScope.launch {
            block()
        }
    }

    /**
     * Executes the given block in a thread of the default dispatcher for
     * async executions of outgoing events.
     */
    private fun inExternalDispatcher(block: suspend CoroutineScope.() -> Unit): Job {
        return apsScope.launch(Dispatchers.Default) {
            block()
        }
    }

    /**
     * Starts the initialization of the APS core asynchronously.
     * See [coreState].
     */
    fun startInitialization() {
        inAPSThread {
            core.initialize()

            launch {
                therapyManager.currentTherapySettingsFlow.drop(1).collect { settings ->
                    if (settings == null) return@collect
                    pumpCoordinator?.issueCommand(
                        PumpCommand.SetProfile(settings.profile.therapyData),
                        isCancelableAPSCommand = false
                    )
                    core.onTherapySettingsChanged(settings)
                }
            }
            launch {
                treatmentRepository.observeMeals().drop(1).collect { data ->
                    core.onMetabolicEventsChanged()
                }
            }
            launch {
                treatmentRepository.observeInsulinApplications().drop(1).collect { data ->
                    core.onMetabolicEventsChanged()
                }
            }

            val initialSettings = therapyManager.getActiveTherapySettings()
            val initialMode = initialSettings?.apsMode ?: ApsMode.Manual
            _apsMode.value = initialMode
            if (initialMode != ApsMode.Manual) {
                core.activate()
            }
        }
        restartGlucosePipeline()
    }

    private fun acquireBusyState() {
        wakeService.acquireBusyState(WAKE_TAG)
    }

    private fun releaseBusyState() {
        wakeService.releaseBusyState(WAKE_TAG)
    }

    private fun restartGlucosePipeline() {
        glucoseJob?.cancel() // Cancel old pipeline if one exists
        val plugin = glucoseSource ?: return

        glucoseJob = inAPSThread {
            installGlucosePipeline_ApsThread(plugin)
        }
    }

    private suspend fun installGlucosePipeline_ApsThread(plugin: GlucoseSource) {
        val sensorType = glucoseRepository.getOrCreateSensorTypeByName(plugin.getSensorTypeName())
        val dataProvider =
            glucoseRepository.getOrCreateDataProviderByName(plugin.name, plugin.dataProviderType)
        core.installGlucosePipeline(plugin, dataProvider, sensorType)
    }

    private fun emitDataUpdateEvent() = inExternalDispatcher {
        _lastDataTime.emit(Timestamp.now())
    }

    private fun emitCoreStateChangedEvent() = inExternalDispatcher {
        _coreState.emit(core.coreState)
    }

    fun setApsMode(mode: ApsMode) = inAPSThread {
        _apsMode.value = mode
        when {
            mode == ApsMode.Manual -> {
                core.suspend()
            }
            else -> {
                core.activate()
            }
        }
        
        // Persist mode
        val currentSettings = therapyManager.getActiveTherapySettings()
        if (currentSettings != null) {
            therapyRepository.updateCurrentTherapySettings(currentSettings.copy(apsMode = mode))
        }
    }

    fun coreCancelInsulinJobs() {
        when (apsMode.value) {
            ApsMode.Manual -> return
            ApsMode.OpenLoop -> return
            ApsMode.ClosedLoop -> {
                pumpCoordinator?.cancelJobs { it.isCancelableAPSCommand }
            }
        }
    }

    fun deliverBolus(amount: InsulinAmount) {
        when (apsMode.value) {
            ApsMode.Manual -> return
            ApsMode.OpenLoop -> issueBolusHint(amount)
            ApsMode.ClosedLoop -> {
                pumpCoordinator?.issueCommand(
                    PumpCommand.DeliverBolus(amount),
                    isCancelableAPSCommand = true
                )
            }
        }
    }

    fun canIssueZeroTemp(): Boolean {
        return when (apsMode.value) {
            ApsMode.Manual -> false
            ApsMode.OpenLoop -> false
            ApsMode.ClosedLoop -> true // TODO: Check if pump supports zero temp
        }
    }

    fun issueZeroTemp(durationInHours: Int) {
        when (apsMode.value) {
            ApsMode.Manual -> return
            ApsMode.OpenLoop -> return
            ApsMode.ClosedLoop -> {
                pumpCoordinator?.issueCommand(
                    PumpCommand.SetTempBasal(
                        percent = 0,
                        durationHours = durationInHours
                    ),
                    isCancelableAPSCommand = true
                )
            }
        }
    }

    fun issueCarbHint(amountInGram: Int) {
        _recommendations.value += ApsRecommendation.Carbs(amountInGram)
    }

    fun issueBolusHint(amount: InsulinAmount) {
        _recommendations.value += ApsRecommendation.Bolus(amount)
    }

    suspend fun waitForAndResetInsulinJobs() {
        val pc = pumpCoordinator ?: return
        if (pc.hasPendingJobs()) {
            pc.wakeup()
            pc.waitForIdle()
            delay(10.seconds)
        }
        if (apsMode.value == ApsMode.ClosedLoop) {
            if (pc.hasPendingJobs()) {
                addIssue(ApsIssue.PumpConnectionMissing)
                pc.cancelJobs({ it.isCancelableAPSCommand })
            }
        }
    }

    /**
     * Entry point for external BG updates.
     * Guaranteed to run on the internal APS thread.
     */
    fun updateBg(bg: BgReading) = inAPSThread {
        _recommendations.value = emptyList()
        core.updateBg(bg)
        core.nextBgStaleCheckAt()?.let {
            wakeService.scheduleWakeup(WAKE_TAG, WAKEUP_STALE_CHECK, it)
        }
    }

    /**
     * Entry point for system wakeups.
     * Guaranteed to run on the internal APS thread.
     */
    override fun onWakeup(wakeupId: Int, intent: Intent?) {
        inAPSThread {
            if (wakeupId == WAKEUP_STALE_CHECK) {
                if (core.isStale()) {
                    addIssue(ApsIssue.StaleBG)
                } else {
                    removeIssue(ApsIssue.StaleBG)
                }
            } else if (wakeupId == WAKEUP_PUMP_COORDINATOR) {
                pumpCoordinator?.wakeup()
            }
        }
    }

    /**
     * Gracefully stops the APS system and releases all background resources.
     */
    fun stop() {
        instance = null
        glucoseSource?.let {
            it.stop()
            glucoseSource = null
        }
        insulinPump?.let {
            it.stop()
            insulinPump = null
        }
        apsScope.cancel()
        apsDispatcher.close()
    }

    fun getCurrentBg(): BgReading? {
        return core.currentBg
    }

    fun getLastBg(): BgReading? {
        return core.lastBg
    }

    companion object {
        private val TAG = APS::class.simpleName

        const val WAKE_TAG = "APS"

        const val WAKEUP_STALE_CHECK = 0
        const val WAKEUP_PUMP_COORDINATOR = 1

        @Volatile
        private var instance: APS? = null
    }
}