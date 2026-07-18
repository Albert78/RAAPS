package de.dh.raaps.core.aps

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
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
    val context: Context
) {
    // Threading: Single background thread to avoid race conditions in the core logic
    private val apsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val apsScope = CoroutineScope(apsDispatcher + SupervisorJob())

    private val recursiveBusyState: AtomicInteger = AtomicInteger(0)

    // Power Management
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "raaps:ApsCoreLock")

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        instance = this
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
                        onAcquireBusyState = { acquireBusyState() },
                        onReleaseBusyState = { releaseBusyState() },
                        onRequestWakeup = { timestamp -> scheduleSystemWakeup(timestamp, WAKEUP_PUMP_COORDINATOR) },
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
                treatmentRepository.observeBoluses().drop(1).collect { data ->
                    core.onMetabolicEventsChanged()
                }
            }
            launch {
                treatmentRepository.observeBasalHistory().drop(1).collect { data ->
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
        recursiveBusyState.incrementAndGet()
        if (!wakeLock.isHeld) wakeLock.acquire(5_000)
    }

    private fun releaseBusyState() {
        val busyState = recursiveBusyState.decrementAndGet()
        if (busyState > 0) {
            // Still busy
            return
        }
        if (wakeLock.isHeld) {
            try {
                wakeLock.release()
            } catch (_: RuntimeException) {
                // Ignore if already released
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt", "MissingPermission")
    private fun scheduleSystemWakeup(timestamp: Timestamp, wakeupId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "Permission for exact alarms is missing, APS cannot do its work")
                return
            }
        }

        val intent = Intent(context, ApsAlarmReceiver::class.java).apply {
            action = ACTION_WAKEUP
            putExtra(EXTRA_WAKEUP_ID, wakeupId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            wakeupId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timestamp.ms,
                pendingIntent
            )
            Log.d(TAG, "Scheduled system wakeup at $timestamp with ID $wakeupId")
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to schedule exact alarm", e)
        }
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
            scheduleSystemWakeup(it, WAKEUP_STALE_CHECK)
        }
    }

    /**
     * Entry point for system wakeups.
     * Guaranteed to run on the internal APS thread.
     */
    fun wakeup(wakeupId: Int) = inAPSThread {
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

        const val WAKEUP_STALE_CHECK = 0
        const val WAKEUP_PUMP_COORDINATOR = 1

        private const val ACTION_WAKEUP = "de.dh.raaps.core.aps.ACTION_WAKEUP"
        private const val EXTRA_WAKEUP_ID = "wakeup_id"

        @Volatile
        private var instance: APS? = null

        /**
         * Entry point for the [ApsAlarmReceiver] to forward the alarm to the APS instance.
         */
        fun handleWakeup(context: Context, intent: Intent) {
            if (intent.action == ACTION_WAKEUP) {
                val wakeupId = intent.getIntExtra(EXTRA_WAKEUP_ID, -1)
                if (wakeupId != -1) {
                    instance?.wakeup(wakeupId)
                }
            }
        }
    }
}