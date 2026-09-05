package de.dh.raaps.core.aps

import android.app.Notification
import android.content.Context
import android.content.Intent
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculator
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TickPriority
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.pump.PumpIssue
import de.dh.raaps.core.pump.PumpManager
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.SettingsRepository
import de.dh.raaps.core.repository.SystemMetricsRepository
import de.dh.raaps.core.repository.TreatmentRepository
import de.dh.raaps.core.system.AndroidNotifications
import de.dh.raaps.core.system.SystemWakeService
import de.dh.raaps.core.system.WakeupHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

sealed interface ApsIssue {
    /**
     * No recent glucose value available, the loop cannot calculate new treatments.
     */
    data object StaleBG : ApsIssue

    /**
     * Active core issue preventing calculation or normal loop execution.
     */
    data class Core(val issue: CoreIssue) : ApsIssue

    /**
     * Active pump issue preventing insulin delivery or pump operation.
     */
    data class Pump(val issue: PumpIssue) : ApsIssue

    /**
     * Any other issue that prevents the core from working.
     */
    data class Other(val message: String? = null) : ApsIssue
}

/**
 * Orchestrates the different subsystems, manages the application mode, dispatches
 * notifications. It also handles persistence and provides a reactive state for other components to observe.
 */
interface SystemOrchestrator {
    /**
     * The current APS mode.
     */
    val apsMode: StateFlow<ApsMode>

    /**
     * Active issues of the APS.
     */
    val apsIssues: StateFlow<Set<ApsIssue>>

    /**
     * Signal flow indicating whether the blood glucose value is stale.
     */
    val isBgStale: StateFlow<Boolean>

    /**
     * State of the core loop.
     */
    val coreState: StateFlow<CoreState>

    /**
     * Updates the APS mode and persists the change.
     */
    fun setApsMode(mode: ApsMode)

    /**
     * Starts the initialization of the system core.
     */
    fun startInitialization(
        treatmentRepository: TreatmentRepository,
        therapyManager: TherapyManager,
        pumpManager: PumpManager,
        appPreferencesRepository: AppPreferencesRepository,
        carbsInsulinCalculator: CarbsInsulinCalculator,
        systemMetricsRepository: SystemMetricsRepository,
        context: Context
    )

    /**
     * Creates a notification for the foreground service.
     */
    fun createForegroundServiceNotification(): Notification

    /**
     * Gracefully stops the system.
     */
    fun stop()

    /**
     * Returns the predicted blood glucose value for the given timestamp.
     */
    suspend fun getAssumedBg(timestamp: Timestamp): BgValue

    /**
     * Returns the bolus correction calculator.
     */
    fun getBolusCorrectionCalculator(): BolusCorrectionCalculator

    /**
     * Returns whether the meal bolus screen can be opened.
     */
    fun canOpenMealCorrectionBolus(): Boolean

    companion object {
        const val EXECUTION_DELAY_AFTER_BG_MS = 20_000L
    }
}

/**
 * Implementation of [SystemOrchestrator].
 */
class SystemOrchestratorImpl(
    private val glucoseSourceManager: GlucoseSourceManager,
    private val glucoseRepository: GlucoseRepository,
    private val wakeService: SystemWakeService,
    private val settingsRepository: SettingsRepository,
    private val timeService: TimeService,
    private val androidNotifications: AndroidNotifications,
    private val scope: CoroutineScope
) : SystemOrchestrator {
    // Threading: Single background thread to avoid race conditions in the core logic
    private val coreDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val coreScope = CoroutineScope(coreDispatcher + SupervisorJob())

    private val _apsMode = MutableStateFlow(ApsMode.Suspend)
    override val apsMode: StateFlow<ApsMode> = _apsMode.asStateFlow()

    private val _apsIssues = MutableStateFlow<Set<ApsIssue>>(emptySet())
    override val apsIssues: StateFlow<Set<ApsIssue>> = _apsIssues.asStateFlow()

    private val _isBgStale = MutableStateFlow(false)
    override val isBgStale: StateFlow<Boolean> = _isBgStale.asStateFlow()

    private val _coreState = MutableStateFlow<CoreState>(CoreState.Initializing)
    override val coreState: StateFlow<CoreState> = _coreState.asStateFlow()

    // Computation Core: Pure logic and state, completely thread-agnostic
    private lateinit var core: Core

    private var therapyManager: TherapyManager? = null
    private var treatmentRepository: TreatmentRepository? = null
    private var carbsInsulinCalculator: CarbsInsulinCalculator? = null

    private inner class NotificationTickHandler : TickHandler {
        override suspend fun onTick(tick: Tick) {
            androidNotifications.updateMainAppNotification(glucoseRepository)
        }
    }

    private inner class SystemWakeupHandler : WakeupHandler {
        override fun onWakeup(wakeupId: UInt?, intent: Intent?) {
            if (wakeupId == WAKEUP_STALE_CHECK) {
                staleCheck()
            }
        }
    }

    /**
     * Executes the given block on the internal core thread asynchronously.
     */
    private fun inCoreThreadAsync(block: suspend CoroutineScope.() -> Unit): Job {
        return coreScope.launch {
            block()
        }
    }

    /**
     * Executes the given block on the internal core thread and waits for its completion.
     */
    private suspend fun <T> inCoreThreadSync(block: suspend CoroutineScope.() -> T): T {
        return withContext(coreDispatcher) {
            block()
        }
    }

    /**
     * Executes the given block in the external dispatcher asynchronously.
     */
    private fun inExternalDispatcherAsync(block: suspend CoroutineScope.() -> Unit): Job {
        return coreScope.launch(Dispatchers.Default) {
            block()
        }
    }

    /**
     * Executes the given block in the external dispatcher and waits for its completion.
     */
    private suspend fun <T> inExternalDispatcherSync(block: suspend CoroutineScope.() -> T): T {
        return withContext(Dispatchers.Default) {
            block()
        }
    }

    override fun startInitialization(
        treatmentRepository: TreatmentRepository,
        therapyManager: TherapyManager,
        pumpManager: PumpManager,
        appPreferencesRepository: AppPreferencesRepository,
        carbsInsulinCalculator: CarbsInsulinCalculator,
        systemMetricsRepository: SystemMetricsRepository,
        context: Context
    ) {
        this.therapyManager = therapyManager
        this.treatmentRepository = treatmentRepository
        this.carbsInsulinCalculator = carbsInsulinCalculator

        scope.launch {
            combine(
                _isBgStale,
                coreState,
                pumpManager.pumpIssues
            ) { stale, state, pIssues ->
                val issues = mutableSetOf<ApsIssue>()
                if (stale) {
                    issues.add(ApsIssue.StaleBG)
                }
                if (state is CoreState.Active) {
                    state.issues.forEach { issues.add(ApsIssue.Core(it)) }
                }
                pIssues.forEach { issues.add(ApsIssue.Pump(it)) }

                // TODO: Depending on severity, switch APS mode to manual and issue alarm
                issues
            }.collect { combinedIssues ->
                _apsIssues.value = combinedIssues
            }
        }

        // Configure execution offset on timeService:
        // The 5-minute tick interval is centered around the BG reading (halfTickMs = 2.5 minutes).
        // Tick handlers should execute 20 seconds after the expected BG reading arrival.
        val halfTickMs = timeService.timeline.tickSizeMs / 2
        timeService.executionOffsetMs = halfTickMs + SystemOrchestrator.EXECUTION_DELAY_AFTER_BG_MS

        wakeService.registerHandler(WAKE_TAG, SystemWakeupHandler())

        scope.launch {
            settingsRepository.observeCurrentSettings().collect { settings ->
                if (settings != null) {
                    _apsMode.value = settings.apsMode
                }
            }
        }

        scope.launch {
            glucoseRepository.currentBg.drop(1).collect { bg ->
                if (bg != null) {
                    // Schedule stale check for the next window
                    val nextCheck = nextBgStaleCheckAt()
                    wakeService.scheduleWakeup(WAKE_TAG, WAKEUP_STALE_CHECK, nextCheck)

                    // First sync the timeline to our BG...
                    if (glucoseSourceManager.readingsInterval == BgReadingsInterval.FiveMinutes) {
                        // Center the 5-minute tick interval around the BG reading timestamp
                        // (bg.timestamp lies in the center: [bg.timestamp - 2.5m, bg.timestamp + 2.5m)).
                        // This ensures that timing fluctuations in incoming BG readings do not cause tick boundary jumps.
                        val halfTickMs = timeService.timeline.tickSizeMs / 2
                        val centeredTimestamp = Timestamp(bg.timestamp.ms - halfTickMs)
                        timeService.synchronize(centeredTimestamp)
                    } else {
                        // For reading intervals smaller than our 5-minute tick interval, it's ok
                        // if BG values arrive around the interval border. Even if both readings
                        // at the interval borders (start and end) go to the neighbor interval,
                        // we have enough values in the interval left.
                        timeService.synchronize(Timestamp(0))
                    }

                    // ...then add BG value
                    inCoreThreadAsync {
                        core.onNewBgReading(bg)
                    }
                    // Update notification immediately
                    androidNotifications.updateMainAppNotification(glucoseRepository)
                }
            }
        }

        androidNotifications.createNotificationChannels()
        timeService.registerTickHandler(TickPriority.UI, NotificationTickHandler(), "Notifications")

        scope.launch {
            appPreferencesRepository.glucoseUnit.drop(1).collect {
                androidNotifications.updateMainAppNotification(glucoseRepository)
            }
        }

        scope.launch {
            therapyManager.recommendations.collect { recommendations ->
                if (recommendations.isEmpty()) {
                    androidNotifications.cancelRecommendationNotification()
                } else {
                    androidNotifications.showRecommendationNotification(recommendations.first())
                }
            }
        }

        scope.launch {
            apsIssues.collect { issues ->
                if (issues.isNotEmpty()) {
                    androidNotifications.showApsIssueNotification(issues)
                } else {
                    androidNotifications.cancelApsIssueNotification()
                }
            }
        }

        core = Core.createProductiveCore(
            therapyManager = therapyManager,
            treatmentRepository = treatmentRepository,
            timeline = timeService.timeline,
            carbsInsulinCalculator = carbsInsulinCalculator,
            glucoseRepository = glucoseRepository,

            onCoreStateChanged = { handleCoreStateChanged() },
            onAcquireBusyState = { acquireBusyState() },
            onReleaseBusyState = { releaseBusyState() },

            onDeliverBolus = { treatmentLock, amount, meal, handledDeferredBoluses, containsCorrectionPart, containsBasalPart ->
                therapyManager.issueBolus(
                    treatmentLock = treatmentLock,
                    amount = amount,
                    meal = meal,
                    handledDeferredBoluses = handledDeferredBoluses,
                    containsCorrectionPart = containsCorrectionPart,
                    containsBasalPart = containsBasalPart
                )
            },
            onApplyDeferredBolusUpdates = { treatmentLock, updates -> therapyManager.applyDeferredBolusUpdates(treatmentLock, updates) },
            onSetTempBasal = { treatmentLock, durationInHours, percent -> therapyManager.setTempBasal(treatmentLock, durationInHours, percent) },
            onClearTempBasal = { treatmentLock -> therapyManager.clearTempBasal(treatmentLock) },
            onCarbsHint = { treatmentLock, amountInGram -> therapyManager.recommendCarbs(treatmentLock, amountInGram) },
            onClearRecommendations = { treatmentLock -> therapyManager.clearRecommendations(treatmentLock) },
            onWaitForPumpSync = { treatmentLock -> therapyManager.waitForPumpSync(treatmentLock) },
            systemMetricsRepository = systemMetricsRepository,
            scope = scope
        )

        inCoreThreadAsync {
            core.initialize()

            launch {
                apsMode.collect { mode ->
                    when (mode) {
                        ApsMode.AutoCorrection -> core.activate(isReadOnly = false)
                        ApsMode.BasalOnly -> core.activate(isReadOnly = true)
                        ApsMode.Suspend -> core.suspend()
                    }
                    if (mode != ApsMode.Suspend) {
                        core.processCalculation()
                    }
                }
            }
            launch {
                therapyManager.currentTherapySettingsFlow.drop(1).collect { _ ->
                    core.onTherapySettingsChanged()
                }
            }
            launch {
                treatmentRepository.observeMeals().drop(1).collect { _ ->
                    core.onMealsChanged()
                }
            }
            launch {
                treatmentRepository.observeInsulinApplications().drop(1).collect { _ ->
                    core.onInsulinChanged()
                }
            }
        }
        timeService.registerTickHandler(TickPriority.APS, object : TickHandler {
            override suspend fun onTick(tick: Tick) {
                inCoreThreadSync {
                    core.processCalculation()
                }
            }
        }, "APS Core")
    }

    private fun acquireBusyState() {
        wakeService.acquireBusyState(WAKE_TAG)
    }

    private fun releaseBusyState() {
        wakeService.releaseBusyState(WAKE_TAG)
    }

    private fun handleCoreStateChanged() {
        inExternalDispatcherAsync {
            _coreState.emit(core.coreState)
        }
    }

    override fun createForegroundServiceNotification(): Notification {
        return androidNotifications.createMainAppNotification(glucoseRepository)
    }

    override fun stop() {
        coreScope.cancel()
        coreDispatcher.close()
    }

    override suspend fun getAssumedBg(timestamp: Timestamp): BgValue {
        return if (::core.isInitialized) {
            core.getAssumedBg(timestamp)
        } else {
            BgValue.INVALID
        }
    }

    override fun getBolusCorrectionCalculator(): BolusCorrectionCalculator {
        val tm = therapyManager
        val tr = treatmentRepository
        val cic = carbsInsulinCalculator

        return if (::core.isInitialized) {
            core.getBolusCorrectionCalculator()
        } else if (tm != null && tr != null && cic != null) {
            SimpleBolusCorrectionCalculator(tm, glucoseRepository)
        } else {
            NoopAlgorithm().getBolusCorrectionCalculator()
        }
    }

    override fun canOpenMealCorrectionBolus(): Boolean = when (apsMode.value) {
        ApsMode.AutoCorrection, ApsMode.BasalOnly -> true
        ApsMode.Suspend -> false
    }

    override fun setApsMode(mode: ApsMode) {
        _apsMode.value = mode

        scope.launch {
            val currentSettings = settingsRepository.getCurrentSettings()
            if (currentSettings != null) {
                settingsRepository.updateCurrentSettings(currentSettings.copy(apsMode = mode))
            }
        }
    }

    private fun staleCheck() {
        _isBgStale.value = isBgStale()
    }

    fun isBgStale(): Boolean {
        val lastDataTime = glucoseSourceManager.lastInputTimestamp.value
        return lastDataTime.isInvalid() || lastDataTime + STALE_BG_THRESHOLD < Timestamp.now()
    }

    private fun nextBgStaleCheckAt(): Timestamp {
        val lastDataTime = glucoseSourceManager.lastInputTimestamp.value
        return lastDataTime.ifValidOrNow() + STALE_BG_THRESHOLD
    }

    companion object {
        const val WAKE_TAG = "SystemOrchestrator"
        const val WAKEUP_STALE_CHECK = 0u
    }
}