package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.DeferredBolus
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinHistory
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.ToDo
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
     * Wires up the therapy manager with external components, sync history.
     */
    fun startInitialization() {
        pumpManager.setOnHistoryUpdateListener { history ->
            updatePumpHistory(history)
        }

        pumpManager.issueCommand(PumpCommand.SyncHistory)

        scope.launch {
            currentTherapySettingsFlow.collect { settings ->
                pumpManager.issueCommand(
                    PumpCommand.SetProfile(settings.insulinProfile)
                )
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // --- Section: Therapy Settings Management ---
    // -----------------------------------------------------------------------------------------

    suspend fun getCurrentTherapySettings(): CurrentTherapySettings = therapyRepository.getCurrentTherapySettings()

    /**
     * Gets the planned basal rate at the given timestamp.
     */
    suspend fun getBasalPerHour(timestamp: Timestamp): InsulinAmount {
        val settings = getCurrentTherapySettings()
        val profile = settings.insulinProfile
        val baseBasal = profile.basalBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.insulinAdjustmentPercentage) / 100.0
        return InsulinAmount(baseBasal * factor)
    }

    /**
     * Gets the carbohydrate to insulin ratio to be used for calculations at the given timestamp.
     * The CR is a measure of how many grams of carbohydrates are covered by one unit of insulin.
     * Unit: Grams of carbs.
     */
    suspend fun getCrFactor(timestamp: Timestamp): Double {
        val settings = getCurrentTherapySettings()
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
        val settings = getCurrentTherapySettings()
        val profile = settings.insulinProfile
        val amount = profile.isfBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.insulinAdjustmentPercentage) / 100.0
        return BgDelta.fromMgDl((amount / factor).toInt())
    }

    suspend fun getBgSettings(timestamp: Timestamp = Timestamp.now()): Pair<BgValue, BgValue> {
        val settings = getCurrentTherapySettings()
        val defaultBg = settings.defaultBgBlocks.getBgForMinute(timestamp.minutesSinceMidnight())
        return Pair(
            settings.targetBgOverride ?: defaultBg.first,
            settings.lowThresholdOverride ?: defaultBg.second
        )
    }

    suspend fun updateDefaultBgBlocks(blocks: List<BgBlock>) {
        mutex.withLock {
            val currentSettings = getCurrentTherapySettings()
            val newSettings = currentSettings.copy(
                defaultBgBlocks = blocks
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun getPumpInsulinType(): InsulinType {
        val currentSettings = getCurrentTherapySettings()
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
            val currentSettings = getCurrentTherapySettings()

            val newSettings = currentSettings.copy(
                insulinProfile = profile
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setTherapyAdjustment(percentage: Int, targetBg: BgValue?, lowThreshold: BgValue?, adjustmentHint: String?) {
        mutex.withLock {
            val currentSettings = getCurrentTherapySettings()
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
            val currentSettings = getCurrentTherapySettings()
            val newSettings = currentSettings.copy(
                insulinAdjustmentPercentage = percentage
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setTargetBgOverride(target: BgValue?) {
        mutex.withLock {
            val currentSettings = getCurrentTherapySettings()
            val newSettings = currentSettings.copy(
                targetBgOverride = target
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setLowThresholdOverride(threshold: BgValue?) {
        mutex.withLock {
            val currentSettings = getCurrentTherapySettings()
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
        val cts = getCurrentTherapySettings()
        // Update history. I don't think it makes sense to wait for the treatmentLock;
        // If the pump history sync happens during MealCorrectionBolusScreen or during core calculation,
        // something is wrong. In normal operation, all pump commands should have been executed
        // before we acquire the lock.
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
    suspend fun issueBolus(
        treatmentLock: TreatmentLock,
        amount: InsulinAmount,
        meal: MealEntry? = null,
        handledDeferredBoluses: List<DeferredBolus>? = null,
        containsCorrectionPart: Boolean = false,
        containsBasalPart: Boolean = false
    ) {
        checkLock(treatmentLock)

        // Record insulin administration in meals
        val administeredMealIds: MutableSet<Long> = mutableSetOf()
        meal?.id?.let { administeredMealIds.add(it) }
        handledDeferredBoluses?.forEach {
            val mealId = it.mealId
            if (mealId != null) {
                administeredMealIds.add(mealId)
            }
        }
        treatmentRepository.setInsulinAdministered(administeredMealIds)
        // Remove deferred boluses
        handledDeferredBoluses?.let {
            treatmentRepository.removeDeferredBoluses(it)
        }
        // Preserve delivery metadata until history sync
        val cts = getCurrentTherapySettings()
        treatmentRepository.addScheduledPumpInsulinEntry(
            timestamp = Timestamp.now(),
            amount = amount,
            insulinType = cts.insulinProfile.insulinType,
            basal = containsBasalPart,
            correction = containsCorrectionPart,
            meal = administeredMealIds.isNotEmpty()
        )

        val minBolusIncrement = pumpManager.insulinPump?.pumpCapabilities?.value?.minBolusIncrement
        if (minBolusIncrement != null && amount < minBolusIncrement) {
            Log.i(TAG, "Skipping bolus which is too low for pump (amount=$amount, minBolusIncrement=$minBolusIncrement)")
            return
        }
        when (systemManager.apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> recommendBolus(treatmentLock, amount)
            ApsMode.AutoCorrection -> {
                scope.launch {
                    pumpManager.issueCommand(PumpCommand.DeliverBolus(amount))
                }
            }
        }
    }

    /**
     * Sets a temporary basal rate on the pump.
     *
     * @param treatmentLock The lock held by the caller.
     * @param durationInHours The duration for the temporary basal rate.
     * @param percent The relative basal rate in percent.
     */
    fun setTempBasal(treatmentLock: TreatmentLock, durationInHours: Int, percent: Int) {
        checkLock(treatmentLock)
        when (systemManager.apsMode.value) {
            ApsMode.Suspend -> return
            ApsMode.BasalOnly -> return
            ApsMode.AutoCorrection -> {
                scope.launch {
                    pumpManager.issueCommand(
                        PumpCommand.SetTempBasal(
                            percent = percent,
                            durationHours = durationInHours
                        )
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
                        PumpCommand.CancelTempBasal
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

    /**
     * Schedules a reminder for the user to eat their meal.
     *
     * @param mealTimestamp The time when the meal is planned to be eaten.
     */
    fun scheduleMealReminder(mealTimestamp: Timestamp) {
        ToDo.toBeImplemented("Schedule meal reminder")
        // TODO: Implement meal reminder notification logic
        Log.i(TAG, "Scheduled meal reminder for $mealTimestamp")
    }

    suspend fun addDeferredBolus(treatmentLock: TreatmentLock, deferredBolus: DeferredBolus) {
        checkLock(treatmentLock)
        treatmentRepository.addDeferredBolus(deferredBolus)
    }

    suspend fun getDeferredBoluses(): List<DeferredBolus> {
        return treatmentRepository.getDeferredBoluses()
    }

    suspend fun updateDeferredBolus(treatmentLock: TreatmentLock, deferredBolus: DeferredBolus) {
        checkLock(treatmentLock)
        treatmentRepository.updateDeferredBolus(deferredBolus)
    }

    suspend fun removeDeferredBolus(treatmentLock: TreatmentLock, deferredBolus: DeferredBolus) {
        checkLock(treatmentLock)
        treatmentRepository.removeDeferredBolus(deferredBolus)
    }

    suspend fun applyDeferredBolusUpdates(treatmentLock: TreatmentLock, updates: List<DeferredBolusUpdate>) {
        checkLock(treatmentLock)
        val minBolusIncrement = pumpManager.insulinPump?.pumpCapabilities?.value?.minBolusIncrement ?: InsulinAmount.ZERO
        val allDeferred = treatmentRepository.getDeferredBoluses()

        for (update in updates) {
            val bolus = allDeferred.find { it.id == update.id } ?: continue
            if (update.newAmount > minBolusIncrement) {
                treatmentRepository.updateDeferredBolus(bolus.copy(amount = update.newAmount))
            } else {
                treatmentRepository.removeDeferredBolus(bolus)
            }
        }
    }

    /**
     * Causes the insulin pump to execute its pending jobs and to do a history sync.
     * @return Number of pending jobs.
     */
    suspend fun waitForPumpSync(treatmentLock: TreatmentLock): Int {
        checkLock(treatmentLock)
        pumpManager.issueCommand(command = PumpCommand.SyncHistory)
        pumpManager.waitForJobsOrError()
        return pumpManager.getPendingJobsCount()
    }

    /**
     * Tries to acquire a lock for a specific execution block.
     * The lock must be acquired, if either
     * - the following process needs consistent data for the ongoing process (e.g. MealCorrectionBolus screen,
     *   where the bolus decision needs a stable carbs and insulin situation until commit)
     * or
     * - the following process needs to change critical data which might interfere with a
     *   potential ongoing other process (like issuing a bolus will interfere with an ongoing
     *   core calculation).
     * Most of the critical functions require the lock in their method signature.
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

    companion object {
        val TAG = TherapyManager::class.simpleName
    }
}