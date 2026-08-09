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
import de.dh.raaps.common.util.PersistentLogger
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * Recommendations for manual treatments, which are displayed as notifications to the user.
 */
sealed class ApsRecommendation {
    data class Carbs(val amountInGram: Int) : ApsRecommendation()
    data class Bolus(val amount: InsulinAmount) : ApsRecommendation()
}

/**
 * Represents an active lock on therapy-related operations.
 * This lock must be held and passed to critical functions in [TherapyManager].
 */
data class TreatmentLock(val tag: String)

sealed class LockResult {
    data object Success : LockResult()
    data class Busy(val owner: String) : LockResult()
}

data class DeferredBolus(
    var id: Long = de.dh.raaps.common.model.ID_UNDEFINED,
    val amount : InsulinAmount,
    val timestamp : Timestamp
)

/**
 * Central manager for all therapy-related operations and decisions in the APS system.
 *
 * This class serves as the main interface for both the automated system (APS Core) and the user
 * interface to execute treatments and manage therapy settings.
 *
 * ### Functional Areas:
 * - **Therapy Settings Management**: Access and modification of insulin profiles, factors (ISF, CR),
 *   basal rates, and blood glucose targets.
 * - **Insulin Delivery**: Execution of bolus and temporary basal rate commands via the [PumpManager].
 * - **Treatment Recommendations**: Generation of recommendations for manual carbs or bolus delivery.
 * - **Job Management**: Coordination and cleanup of pending insulin delivery tasks.
 *
 * ### Locking System (Concurrency Protection):
 * To prevent race conditions and conflicting treatments (e.g., the system setting a basal rate
 * while the user is delivering a bolus), critical functions require a [TreatmentLock].
 *
 * Callers must acquire this lock via [tryAcquire]. If successful, they receive a [TreatmentLock]
 * token that must be passed to all critical methods. These methods verify the lock's validity
 * via [checkLock] before execution.
 */
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
            currentTherapySettingsFlow.collect { settings ->
                pumpManager.issueCommand(
                    PumpCommand.SetProfile(settings.insulinProfile),
                    isCancelableAPSCommand = false
                )
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // --- Section: Therapy Settings Management ---
    // -----------------------------------------------------------------------------------------

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

    // -----------------------------------------------------------------------------------------
    // --- Section: Critical Insulin & Carb Management (Requires Lock) ---
    // -----------------------------------------------------------------------------------------

    /**
     * Verifies that the provided lock matches the current execution owner.
     * @throws IllegalStateException if the lock is invalid or not held.
     */
    private fun checkLock(treatmentLock: TreatmentLock) {
        if (currentExecutionOwner != treatmentLock.tag) {
            throw IllegalStateException("Execution lock not held by ${treatmentLock.tag} (current owner: $currentExecutionOwner)")
        }
    }

    /**
     * Triggered when the history of actual bolus and basal values was updated.
     */
    suspend fun updatePumpHistory(history: InsulinHistory) {
        val cts = getActiveTherapySettings()
        treatmentRepository.mergeInsulinHistory(history, cts.insulinProfile.insulinType)
    }

    /**
     * Initiates a bolus delivery.
     *
     * Depending on the current [ApsMode], this will either directly command the pump
     * or record a recommendation for the user.
     *
     * @param treatmentLock The lock held by the caller.
     * @param amount The amount of insulin to deliver.
     * @param handledDeferredBoluses Optional deferred boluses that were handled by this delivery.
     */
    suspend fun issueBolus(treatmentLock: TreatmentLock, amount: InsulinAmount, handledDeferredBoluses: List<DeferredBolus>? = null) {
        checkLock(treatmentLock)
        handledDeferredBoluses?.forEach {
            markDeferredBolusHandled(treatmentLock, it)
        }
        when (systemManager.apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> recommendBolus(treatmentLock, amount)
            ApsMode.AutoCorrection -> {
                scope.launch {
val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(System.currentTimeMillis()))
PersistentLogger.log("TherapyManager", "------------ issueBolus: Calling PumpManager#issueCommand to create BOLUS at $time, amount=${amount.iu}")
                    pumpManager.issueCommand(
                        PumpCommand.DeliverBolus(amount),
                        isCancelableAPSCommand = true
                    )
                }
            }
        }
    }

    /**
     * Sets a temporary basal rate on the pump.
     *
     * @param treatmentLock The lock held by the caller.
     * @param durationInHours The duration for the temporary basal rate.
     * @param unitsPerHour The absolute basal rate in units per hour.
     */
    fun setTempBasal(treatmentLock: TreatmentLock, durationInHours: Int, unitsPerHour: Double) {
        checkLock(treatmentLock)
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

    /**
     * Cancels any active temporary basal rate on the pump.
     */
    fun clearTempBasal(treatmentLock: TreatmentLock) {
        checkLock(treatmentLock)
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

    /**
     * Clears all currently active therapy recommendations.
     */
    fun clearRecommendations(treatmentLock: TreatmentLock) {
        checkLock(treatmentLock)
        _recommendations.value = emptyList()
    }

    /**
     * Records a recommendation for carb intake.
     */
    fun recommendCarbs(treatmentLock: TreatmentLock, amountInGram: Int) {
        checkLock(treatmentLock)
        _recommendations.value += ApsRecommendation.Carbs(amountInGram)
    }

    /**
     * Records a recommendation for bolus delivery.
     */
    fun recommendBolus(treatmentLock: TreatmentLock, amount: InsulinAmount) {
        checkLock(treatmentLock)
        _recommendations.value += ApsRecommendation.Bolus(amount)
    }

    suspend fun addDeferredBolus(treatmentLock: TreatmentLock, deferredBolus: DeferredBolus) {
        checkLock(treatmentLock)
        treatmentRepository.addDeferredBolus(deferredBolus)
    }

    suspend fun getDeferredBoluses(): List<DeferredBolus> {
        return treatmentRepository.getDeferredBoluses()
    }

    suspend fun markDeferredBolusHandled(treatmentLock: TreatmentLock, deferredBolus: DeferredBolus) {
        checkLock(treatmentLock)
        treatmentRepository.removeDeferredBolus(deferredBolus)
    }

    /**
     * Cancels all cancellable APS commands currently pending in the pump manager.
     */
    fun coreCancelInsulinJobs(treatmentLock: TreatmentLock) {
        checkLock(treatmentLock)
        when (systemManager.apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> return
            ApsMode.AutoCorrection -> {
                pumpManager.cancelJobs { it.isCancelableAPSCommand }
            }
        }
    }

    suspend fun waitForInsulinJobs(treatmentLock: TreatmentLock): Boolean {
        checkLock(treatmentLock)
        if (pumpManager.hasPendingJobs()) {
            pumpManager.wakeup()
            pumpManager.waitForIdle()
            delay(10.seconds)
        }
        return !pumpManager.hasPendingJobs()
    }

    /**
     * Tries to acquire a lock for a specific execution block.
     * If the lock is already held by another system part, returns [LockResult.Busy].
     * Otherwise, executes the block and returns [LockResult.Success].
     */
    suspend fun tryAcquire(tag: String, block: suspend (TreatmentLock) -> Unit): LockResult {
        if (!executionMutex.tryLock()) {
            return LockResult.Busy(currentExecutionOwner ?: "Unknown")
        }
        val treatmentLock = TreatmentLock(tag)
        currentExecutionOwner = tag
        return try {
            block(treatmentLock)
            LockResult.Success
        } finally {
            currentExecutionOwner = null
            executionMutex.unlock()
        }
    }
}
