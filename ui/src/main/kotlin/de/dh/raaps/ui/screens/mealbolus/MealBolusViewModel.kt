package de.dh.raaps.ui.screens.mealbolus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.DEFAULT_BG_LOW_THRESHOLD_MGDL
import de.dh.raaps.common.model.DEFAULT_BG_TARGET_MGDL
import de.dh.raaps.common.model.DEFAULT_CR_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.MS_PER_MINUTE
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.PlannedInsulin
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.aps.DeferredBolus
import de.dh.raaps.core.aps.TreatmentLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface SubmissionStatus {
    object NotSubmitted : SubmissionStatus
    object Submitting : SubmissionStatus
    object Success : SubmissionStatus
}

/**
 * UI-specific model for planned insulin that includes an absolute timestamp for display and execution.
 */
data class PlannedInsulinUiModel(
    val amount: InsulinAmount,
    val timestamp: Timestamp,
    val minutesFromMeal: Minutes,
    val description: String,
    val isUserModified: Boolean
) {
    fun toCoreModel() = PlannedInsulin(
        amount = amount,
        timeFromMeal = minutesFromMeal,
        description = description,
        isUserModified = isUserModified
    )
}

/**
 * ViewModel for the Meal Bolus screen, managing the calculation cascade and temporal dependencies.
 *
 * ## Calculation Cascade & Dependencies (Stages):
 * 1. **Initial Proposal:** Current BG -> Suggested IMI -> Suggested Meal Time (T_meal).
 * 2. **Meal Context:** T_meal -> Reference Timestamp (T_ref) for projections (IOB, COB, projected BG).
 * 3. **Bolus Suggestion:** (Carbs + Meal Type) AND (Projections @ T_ref) -> Proposed Bolus Amount.
 * 4. **Insulin Plan:** Bolus Amount + Meal Type -> Insulin Distribution Plan (Relative Offsets to T_meal).
 * 5. **Execution:** T_meal + Relative Offsets -> Absolute execution timestamps.
 *
 * ## Rules for Manual Changes:
 * - **T_meal:** Changing the meal time manually updates the IMI (offset). T_meal remains relative and slides
 *   with system time via the ticker to maintain this offset until submission. Although a change of T_meal also changes
 *   T_ref conceptually, projections are NOT automatically refreshed to ensure UI stability.
 * - **Carbs/Meal Type:** Triggers Stage 3 & 4 (recalculates bolus and plan distribution).
 * - **Bolus Amount:** Triggers Stage 4 (recalculates plan distribution based on the new amount).
 * - **Plan Offsets:** Manually changing a plan time fixes its relative offset to T_meal.
 *
 * ## Temporal Stability:
 * - Projection data (IOB, COB, BG) is kept stable until an explicit refresh is triggered (Refresh Button).
 * - The UI indicates "stale" data if the reference timestamp is more than 4 minutes old.
 */
data class MealBolusUiState(
    val isLoading: Boolean = true,
    val mealTimestamp: Timestamp = Timestamp.now(),
    val referenceBg: BgValue? = null,
    val referenceTimestamp: Timestamp = Timestamp.now(),
    val isProjected: Boolean = false,
    val isProjectionsStale: Boolean = false,
    val imi: Minutes = Minutes(0),
    val carbsKe: Double = 0.0,
    val mealTypes: List<MealType> = emptyList(),
    val selectedMealType: MealType? = null,
    val targetBg: BgValue = BgValue(DEFAULT_BG_TARGET_MGDL),
    val lowThreshold: BgValue = BgValue(DEFAULT_BG_LOW_THRESHOLD_MGDL),
    val isf: Int = DEFAULT_ISF_MGDL_PER_UNIT.toInt(),
    val cr: Double = DEFAULT_CR_GRAM_PER_UNIT,
    val iob: InsulinAmount = InsulinAmount.ZERO,
    val cob: Double = 0.0,
    val projectedIob: InsulinAmount = InsulinAmount.ZERO,
    val projectedCob: Double = 0.0,
    val mealPart: InsulinAmount = InsulinAmount.ZERO,
    val correctionPart: InsulinAmount = InsulinAmount.ZERO,
    val iobPart: InsulinAmount = InsulinAmount.ZERO,
    val cobPart: InsulinAmount = InsulinAmount.ZERO,
    val proposedBolus: InsulinAmount = InsulinAmount.ZERO,
    val manualBolus: InsulinAmount = InsulinAmount.ZERO,
    val insulinPlan: List<PlannedInsulinUiModel> = emptyList(),
    val isInsulinPlanExpanded: Boolean = false,
    val isMealReminderEnabled: Boolean = true,
    val showCloseBanner: Boolean = false,
    val submissionStatus: SubmissionStatus = SubmissionStatus.NotSubmitted
)

class MealBolusViewModel(
    private val registry: SystemRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(MealBolusUiState())
    val uiState: StateFlow<MealBolusUiState> = _uiState.asStateFlow()

    private val therapyManager = registry.therapyManager
    private val treatmentRepository = registry.treatmentRepository

    init {
        viewModelScope.launch {
            val now = Timestamp.now()
            val isf = therapyManager.getIsfFactor(now).mgdl.toInt()
            val cr = therapyManager.getCrFactor(now)
            val bgSettings = therapyManager.getBgSettings()
            val mealTypes = treatmentRepository.getAllMealTypes()

            val baseData = registry.systemManager.getBolusCorrectionCalculator().calculateBaseData()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    mealTimestamp = now + Minutes(max(0, baseData.suggestedImi.value.toInt()).toShort()),
                    imi = baseData.suggestedImi,
                    carbsKe = baseData.suggestedCarbsKe,
                    mealTypes = mealTypes,
                    referenceBg = baseData.referenceBg,
                    referenceTimestamp = baseData.referenceTimestamp,
                    isProjected = baseData.referenceTimestamp.ms > now.ms,
                    targetBg = bgSettings.first,
                    lowThreshold = bgSettings.second,
                    isf = if (isf == 0) DEFAULT_ISF_MGDL_PER_UNIT.toInt() else isf,
                    cr = if (cr == 0.0) DEFAULT_CR_GRAM_PER_UNIT else cr,
                )
            }
            calculateBolus()
            startTicker()
            startCloseBannerTimer()
        }
    }

    fun onCarbsChange(ke: Double) {
        _uiState.update { it.copy(carbsKe = max(0.0, ke)) }
        calculateBolus()
    }

    fun onMealTimeChange(timestamp: Timestamp) {
        val now = Timestamp.now()
        val offsetMinutes = kotlin.math.round((timestamp.ms - now.ms) / 60000.0).toInt().coerceIn(-120, 60)
        _uiState.update { it.copy(
            mealTimestamp = now + Minutes(offsetMinutes.toShort()),
            imi = Minutes(offsetMinutes.toShort())
        ) }
        calculateBolus()
    }

    fun onRefreshProjections() {
        viewModelScope.launch {
            val state = _uiState.value
            val now = Timestamp.now()

            // Re-calculate the reference timestamp: use current meal timestamp for projection if future, else now.
            val newRefTime = if (state.mealTimestamp > now) state.mealTimestamp else now

            val result = registry.systemManager.getBolusCorrectionCalculator().calculateBolusParts(
                carbsKe = state.carbsKe,
                mealTimestamp = state.mealTimestamp,
                referenceTimestamp = newRefTime
            )

            _uiState.update {
                it.copy(
                    referenceBg = result.calculationBg,
                    referenceTimestamp = result.calculationTimestamp,
                    isProjectionsStale = false,
                    isProjected = result.calculationTimestamp.ms > now.ms + 4 * MS_PER_MINUTE,
                    iob = result.iobPart,
                    cob = result.cobGrams,
                    projectedIob = result.iobPart,
                    projectedCob = result.cobGrams,
                    mealPart = result.mealPart,
                    correctionPart = result.correctionPart,
                    iobPart = result.iobPart,
                    cobPart = result.cobPart,
                    proposedBolus = result.totalProposed,
                    manualBolus = result.totalProposed
                )
            }
            updateInsulinPlanTimes()
        }
    }

    fun onMealTypeChange(mealType: MealType) {
        _uiState.update { it.copy(selectedMealType = mealType) }
        calculateBolus()
    }

    fun onManualBolusChange(amount: Double) {
        _uiState.update { it.copy(manualBolus = InsulinAmount(amount).coerceAtLeast(InsulinAmount.ZERO)) }
        updateInsulinPlanTimes() // Re-calculate distribution if manual amount changes
    }

    fun onPlannedInsulinTimeChange(index: Int, newTimestamp: Timestamp) {
        val state = _uiState.value
        val offset = Minutes.timeDifference(state.mealTimestamp, newTimestamp)
        _uiState.update { s ->
            val newPlan = s.insulinPlan.toMutableList()
            if (index in newPlan.indices) {
                newPlan[index] = newPlan[index].copy(
                    timestamp = newTimestamp,
                    minutesFromMeal = offset,
                    isUserModified = true
                )
            }
            s.copy(insulinPlan = newPlan)
        }
    }

    fun toggleInsulinPlanExpanded() {
        _uiState.update { it.copy(isInsulinPlanExpanded = !it.isInsulinPlanExpanded) }
    }

    fun onToggleMealReminder() {
        _uiState.update { it.copy(isMealReminderEnabled = !it.isMealReminderEnabled) }
    }

    private fun calculateBolus() {
        viewModelScope.launch {
            val state = _uiState.value
            val now = Timestamp.now()

            val result = registry.systemManager.getBolusCorrectionCalculator().calculateBolusParts(
                carbsKe = state.carbsKe,
                mealTimestamp = state.mealTimestamp,
                referenceTimestamp = state.referenceTimestamp
            )

            _uiState.update {
                it.copy(
                    referenceBg = result.calculationBg,
                    referenceTimestamp = result.calculationTimestamp,
                    isProjected = result.calculationTimestamp.ms > now.ms + 4 * MS_PER_MINUTE,
                    iob = result.iobPart,
                    cob = result.cobGrams,
                    projectedIob = result.iobPart,
                    projectedCob = result.cobGrams,
                    mealPart = result.mealPart,
                    correctionPart = result.correctionPart,
                    iobPart = result.iobPart,
                    cobPart = result.cobPart,
                    proposedBolus = result.totalProposed,
                    manualBolus = result.totalProposed
                )
            }
            updateInsulinPlanTimes()
        }
    }

    private fun updateInsulinPlanTimes() {
        viewModelScope.launch {
            val state = _uiState.value

            val newPlan = registry.systemManager.getBolusCorrectionCalculator().distributeInsulinPlan(
                manualBolus = state.manualBolus,
                correctionPart = state.correctionPart,
                mealType = state.selectedMealType,
                suggestedImi = state.imi,
                existingPlan = state.insulinPlan.map { it.toCoreModel() }
            )

            val uiPlan = newPlan.map { core ->
                PlannedInsulinUiModel(
                    amount = core.amount,
                    timestamp = state.mealTimestamp + core.timeFromMeal,
                    minutesFromMeal = core.timeFromMeal,
                    description = core.description,
                    isUserModified = core.isUserModified
                )
            }
            _uiState.update { it.copy(insulinPlan = uiPlan) }
        }
    }

    fun submit(lock: TreatmentLock, onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.submissionStatus != SubmissionStatus.NotSubmitted) return

        viewModelScope.launch {
            _uiState.update { it.copy(submissionStatus = SubmissionStatus.Submitting) }
            try {
                // 1. Record Meal
                if ((state.carbsKe > 0) && (state.selectedMealType != null)) {
                    val mealEntry = MealEntry(
                        timestamp = state.mealTimestamp,
                        carbGrams = state.carbsKe * 10.0,
                        mealType = state.selectedMealType
                    )
                    treatmentRepository.addMealEntry(mealEntry)

                    // Schedule reminder
                    if (state.isMealReminderEnabled) {
                        therapyManager.scheduleMealReminder(state.mealTimestamp)
                    }
                }

                // 2. Deliver Insulin Plan
                if (state.insulinPlan.isNotEmpty()) {
                    val now = Timestamp.now()
                    val immediateBolus = state.insulinPlan.filter { it.timestamp <= now + Minutes(1) }
                        .fold(InsulinAmount.ZERO) { acc, next -> acc + next.amount }

                    val deferredBoluses = state.insulinPlan.filter { it.timestamp > now + Minutes(1) }
                        .map { DeferredBolus(id = ID_UNDEFINED, amount = it.amount, timestamp = it.timestamp) }

                    if (immediateBolus > InsulinAmount.ZERO) {
                        therapyManager.issueBolus(lock, immediateBolus)
                    }

                    deferredBoluses.forEach {
                        therapyManager.addDeferredBolus(lock, it)
                    }
                }

                _uiState.update { it.copy(submissionStatus = SubmissionStatus.Success) }
                onSuccess()
            } catch (_: Exception) {
                // TODO: Error handling
                _uiState.update { it.copy(submissionStatus = SubmissionStatus.NotSubmitted) }
            }
        }
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (true) {
                delay(5.seconds)
                val now = Timestamp.now()
                _uiState.update { state ->
                    if (state.submissionStatus != SubmissionStatus.NotSubmitted) return@update state

                    // 1. Let meal time "slide" with now (keeping IMI constant)
                    val newMealTimestamp = now + state.imi

                    // 2. Check for stale projections (> 4 mins old)
                    val isStale = (now.ms - state.referenceTimestamp.ms) > 4 * MS_PER_MINUTE

                    // 3. Update absolute times in insulin plan (based on sliding meal time)
                    val updatedPlan = state.insulinPlan.map { item ->
                        item.copy(timestamp = newMealTimestamp + item.minutesFromMeal)
                    }

                    state.copy(
                        mealTimestamp = newMealTimestamp,
                        isProjectionsStale = isStale,
                        insulinPlan = updatedPlan
                    )
                }
            }
        }
    }

    private fun startCloseBannerTimer() {
        viewModelScope.launch {
            delay(5.minutes)
            _uiState.update { it.copy(showCloseBanner = true) }
        }
    }

    companion object {
        class Factory(private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return MealBolusViewModel(registry) as T
            }
        }
    }
}