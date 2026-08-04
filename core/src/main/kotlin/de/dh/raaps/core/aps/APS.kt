package de.dh.raaps.core.aps

import android.content.Context
import android.content.Intent
import android.util.Log
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.pump.PumpCommand
import de.dh.raaps.core.pump.PumpManager
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.SettingsRepository
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
import kotlin.time.Duration.Companion.seconds

enum class ApsIssue {
    /**
     * No recent glucose value available, the loop cannot calculate new treatments.
     */
    StaleBG,

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
    val settingsRepository: SettingsRepository,
    val treatmentRepository: TreatmentRepository,
    val appPreferencesRepository: AppPreferencesRepository,
    val therapyManager: TherapyManager,
    val wakeService: SystemWakeService,
    val timeService: TimeService,
    val pumpManager: PumpManager,
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    val context: Context
) : WakeupHandler {
    // Threading: Single background thread to avoid race conditions in the core logic
    private val apsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val apsScope = CoroutineScope(apsDispatcher + SupervisorJob())

    init {
        wakeService.registerHandler(WAKE_TAG, this)
    }

    // Computation Core: Pure logic and state, completely thread-agnostic
    private val core: Core = Core.createProductiveCore(
        therapyManager = therapyManager,
        glucoseRepository = glucoseRepository,
        treatmentRepository = treatmentRepository,
        appPreferencesRepository = appPreferencesRepository,
        timeService = timeService,
        carbsInsulinCalculationModel = carbsInsulinCalculationModel,

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

    /**
     * Time delay between a glucose value in blood and the given Timestamp of the bg reading.
     * Typically, the bg reading timestamp represents the time of measure of the CGM system, which
     * is about 5 minutes behind blood glucose.
     */
    var glucoseReadingsTimeDelay: Minutes = Minutes(5)
        private set

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

    private val _apsMode = MutableStateFlow(ApsMode.Suspend)
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
            therapyManager.startInitialization(pumpManager)

            launch {
                settingsRepository.observeCurrentSettings().collect { settings ->
                    if (settings == null) return@collect
                    _apsMode.value = settings.apsMode
                    if (settings.apsMode != ApsMode.Suspend) {
                        core.activate()
                    } else {
                        core.suspend()
                    }
                }
            }
            launch {
                therapyManager.currentTherapySettingsFlow.drop(1).collect { settings ->
                    if (settings == null) return@collect
                    pumpManager.issueCommand(
                        PumpCommand.SetProfile(settings.insulinProfile),
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
        Log.d(TAG, "Installing glucose pipeline")

        val sensorType = glucoseRepository.getOrCreateSensorTypeByName(plugin.getSensorTypeName())
        val dataProvider =
            glucoseRepository.getOrCreateDataProviderByName(plugin.name, plugin.dataProviderType)

        glucoseReadingsTimeDelay = plugin.readingsTimeDelay

        // Persist values
        val persistedValues = plugin.getValues()
            .persist(glucoseRepository, dataProvider, sensorType)

        // Collect for core calculation
        persistedValues
            // Threading notice:
            // The .collect call will block our coroutine, so it must be the last action in this method.
            // But since we're in a coroutine, the call won't block our (single) thread while
            // waiting for new values; instead, it will just suspend and free the thread for other work.
            .collect { bg ->
                core.updateBg(bg)
                // Synchronize our internal ticking grid to fire 20s after the BG reading.
                timeService.synchronize(Timestamp.now().plusSeconds(20))
            }
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
            mode == ApsMode.Suspend -> {
                core.suspend()
            }
            else -> {
                core.activate()
            }
        }

        // Persist mode
        val currentSettings = settingsRepository.getCurrentSettings()
        if (currentSettings != null) {
            settingsRepository.updateCurrentSettings(currentSettings.copy(apsMode = mode))
        }
    }

    fun coreCancelInsulinJobs() {
        when (apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> return
            ApsMode.AutoCorrection -> {
                pumpManager.cancelJobs { it.isCancelableAPSCommand }
            }
        }
    }

    fun deliverBolus(amount: InsulinAmount) {
        when (apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> issueBolusHint(amount)
            ApsMode.AutoCorrection -> {
                inAPSThread {
                    pumpManager.issueCommand(
                        PumpCommand.DeliverBolus(amount),
                        isCancelableAPSCommand = true
                    )
                }
            }
        }
    }

    fun canIssueZeroTemp(): Boolean {
        return when (apsMode.value) {
            ApsMode.Suspend -> false
            ApsMode.BasalOnly -> false
            ApsMode.AutoCorrection -> true // TODO: Check if pump supports zero temp
        }
    }

    fun issueZeroTemp(durationInHours: Int) {
        when (apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> return
            ApsMode.AutoCorrection -> {
                inAPSThread {
                    pumpManager.issueCommand(
                        PumpCommand.SetTempBasal(
                            percent = 0,
                            durationHours = durationInHours
                        ),
                        isCancelableAPSCommand = true
                    )
                }
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
        if (pumpManager.hasPendingJobs()) {
            pumpManager.wakeup()
            pumpManager.waitForIdle()
            delay(10.seconds)
        }
        if (apsMode.value == ApsMode.AutoCorrection) {
            if (pumpManager.hasPendingJobs()) {
                // This issue will now be handled by PumpManager
                pumpManager.cancelJobs({ it.isCancelableAPSCommand })
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
    override fun onWakeup(wakeupId: UInt?, intent: Intent?) {
        inAPSThread {
            if (wakeupId == WAKEUP_STALE_CHECK) {
                if (core.isStale()) {
                    addIssue(ApsIssue.StaleBG)
                } else {
                    removeIssue(ApsIssue.StaleBG)
                }
            }
        }
    }

    /**
     * Gracefully stops the APS system and releases all background resources.
     */
    fun stop() {
        glucoseSource?.let {
            it.stop()
            glucoseSource = null
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

        val WAKEUP_STALE_CHECK = 0u
    }
}