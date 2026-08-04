package de.dh.raaps.core.aps

import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinHistory
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.getAmountForMinute
import de.dh.raaps.common.model.data.getBgForMinute
import de.dh.raaps.core.pump.PumpCommand
import de.dh.raaps.core.pump.PumpManager
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

/**
 * Recommendations for manual treatments, which are displayed as notifications to the user.
 */
sealed class ApsRecommendation {
    data class Carbs(val amountInGram: Int) : ApsRecommendation()
    data class Bolus(val amount: InsulinAmount) : ApsRecommendation()
}

sealed class LockResult {
    data object Success : LockResult()
    data class Busy(val owner: String) : LockResult()
}

data class DeferredBolus(
    val amount : InsulinAmount,
    val timestamp : Timestamp
)

class TherapyManager(
    private val therapyRepository: TherapyRepository,
    private val treatmentRepository: TreatmentRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val pumpManager: PumpManager,
    private val systemManager: SystemManager,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val executionMutex = Mutex()
    private var currentExecutionOwner: String? = null

    private val _recommendations = MutableStateFlow<List<ApsRecommendation>>(emptyList())
    val recommendations: StateFlow<List<ApsRecommendation>> = _recommendations.asStateFlow()

    val currentTherapySettingsFlow: Flow<CurrentTherapySettings> = therapyRepository.observeCurrentTherapySettings()

    /**
     * Wires up the therapy manager with external components.
     */
    fun startInitialization() {
        pumpManager.setOnHistoryUpdateListener { history ->
            updatePumpHistory(history)
        }

        scope.launch {
            currentTherapySettingsFlow.drop(1).collect { settings ->
                pumpManager.issueCommand(
                    PumpCommand.SetProfile(settings.insulinProfile),
                    isCancelableAPSCommand = false
                )
            }
        }
    }

    suspend fun getActiveTherapySettings(): CurrentTherapySettings = therapyRepository.getCurrentTherapySettings()

    /**
     * Gets the planned basal rate at the given timestamp.
     * Unit: Insulin units.
     */
    suspend fun getBasalPerHour(timestamp: Timestamp): Double {
        val settings = getActiveTherapySettings()
        val profile = settings.insulinProfile
        val baseBasal = profile.basalBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.insulinAdjustmentPercentage) / 100.0
        return baseBasal * factor
    }

    /**
     * Gets the carbohydrate to insulin ratio to be used for calculations at the given timestamp.
     * The CR is a measure of how many grams of carbohydrates are covered by one unit of insulin.
     * Unit: Grams of carbs.
     */
    suspend fun getCrFactor(timestamp: Timestamp): Double {
        val settings = getActiveTherapySettings()
        val profile = settings.insulinProfile
        val baseCr = profile.crBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.insulinAdjustmentPercentage) / 100.0
        return baseCr / factor
    }

    /**
     * Gets the insulin sensitivity factor to be used for calculations for the given timestamp.
     * The ISF (sometimes also called the correction factor, CF) is a measure of how much a single
     * unit of insulin lowers blood glucose levels.
     * Unit: Blood glucose delta.
     */
    suspend fun getIsfFactor(timestamp: Timestamp): BgDelta {
        val settings = getActiveTherapySettings()
        val profile = settings.insulinProfile
        val amount = profile.isfBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.insulinAdjustmentPercentage) / 100.0
        return BgDelta.fromMgDl((amount / factor).toInt())
    }

    suspend fun getBgSettings(): Pair<BgValue, BgValue> {
        val settings = getActiveTherapySettings()
        val defaultBg = settings.defaultBgBlocks.getBgForMinute(Timestamp.now().minutesSinceMidnight())
        return Pair(
            settings.targetBgOverride ?: defaultBg.first,
            settings.lowThresholdOverride ?: defaultBg.second
        )
    }

    suspend fun updateDefaultBgBlocks(blocks: List<BgBlock>) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                defaultBgBlocks = blocks
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun getPumpInsulinType(): InsulinType {
        val currentSettings = getActiveTherapySettings()
        return currentSettings.insulinProfile.insulinType
    }

    suspend fun getAllInsulinProfiles() = therapyRepository.getAllInsulinProfiles()

    fun observeAllInsulinProfiles() = therapyRepository.observeAllInsulinProfiles()

    /**
     * Updates the current therapy settings based on a selected profile.
     * This will create a copy of the profile's therapy data as the active configuration.
     */
    suspend fun selectInsulinProfile(profile: InsulinProfile) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()

            val newSettings = currentSettings.copy(
                insulinProfile = profile
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setTherapyAdjustment(percentage: Int, targetBg: BgValue?, lowThreshold: BgValue?, adjustmentHint: String?) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                insulinAdjustmentPercentage = percentage,
                targetBgOverride = targetBg,
                lowThresholdOverride = lowThreshold,
                adjustmentHint = adjustmentHint
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setInsulinAdjustmentPercentage(percentage: Int) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                insulinAdjustmentPercentage = percentage
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setTargetBgOverride(target: BgValue?) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                targetBgOverride = target
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setLowThresholdOverride(threshold: BgValue?) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                lowThresholdOverride = threshold
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    /**
     * Triggered when the history of actual bolus and basal values was updated.
     */
    suspend fun updatePumpHistory(history: InsulinHistory) {
        val cts = getActiveTherapySettings()
        treatmentRepository.mergeInsulinHistory(history, cts.insulinProfile.insulinType)
    }

    fun issueBolus(amount: InsulinAmount) {
        when (systemManager.apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> recommendBolus(amount)
            ApsMode.AutoCorrection -> {
                scope.launch {
                    pumpManager.issueCommand(
                        PumpCommand.DeliverBolus(amount),
                        isCancelableAPSCommand = true
                    )
                }
            }
        }
    }

    fun setTempBasal(durationInHours: Int, unitsPerHour: Double) {
        when (systemManager.apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> return
            ApsMode.AutoCorrection -> {
                scope.launch {
                    pumpManager.issueCommand(
                        PumpCommand.SetTempBasal(
                            absoluteUnits = unitsPerHour,
                            durationHours = durationInHours
                        ),
                        isCancelableAPSCommand = true
                    )
                }
            }
        }
    }

    fun clearTempBasal() {
        when (systemManager.apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> return
            ApsMode.AutoCorrection -> {
                scope.launch {
                    pumpManager.issueCommand(
                        PumpCommand.CancelTempBasal,
                        isCancelableAPSCommand = true
                    )
                }
            }
        }
    }

    fun clearRecommendations() {
        _recommendations.value = emptyList()
    }

    fun recommendCarbs(amountInGram: Int) {
        _recommendations.value += ApsRecommendation.Carbs(amountInGram)
    }

    fun recommendBolus(amount: InsulinAmount) {
        _recommendations.value += ApsRecommendation.Bolus(amount)
    }

    fun addDeferredBolus(deferredBolus: DeferredBolus) {
        todo()
    }

    fun getDeferredBoluses(): List<DeferredBolus> {
        add_management_for_deferred_bolus___also_db()
        use_management_in_bolus_screen()
    }

    fun coreCancelInsulinJobs() {
        when (systemManager.apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> return
            ApsMode.AutoCorrection -> {
                pumpManager.cancelJobs { it.isCancelableAPSCommand }
            }
        }
    }

    suspend fun waitForAndResetInsulinJobs() {
        if (pumpManager.hasPendingJobs()) {
            pumpManager.wakeup()
            pumpManager.waitForIdle()
            delay(10.seconds)
        }
        if (systemManager.apsMode.value == ApsMode.AutoCorrection) {
            if (pumpManager.hasPendingJobs()) {
                // This issue will now be handled by PumpManager
                pumpManager.cancelJobs({ it.isCancelableAPSCommand })
            }
        }
    }

    /**
     * Tries to acquire a lock for a specific execution block.
     * If the lock is already held by another system part, returns [LockResult.Busy].
     * Otherwise, executes the block and returns [LockResult.Success].
     */
    suspend fun tryAcquire(tag: String, block: suspend () -> Unit): LockResult {
        if (!executionMutex.tryLock()) {
            return LockResult.Busy(currentExecutionOwner ?: "Unknown")
        }
        currentExecutionOwner = tag
        return try {
            block()
            LockResult.Success
        } finally {
            currentExecutionOwner = null
            executionMutex.unlock()
        }
    }
}