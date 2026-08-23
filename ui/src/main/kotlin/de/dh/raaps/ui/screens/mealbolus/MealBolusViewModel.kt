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
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.aps.BolusCalculationMath
import de.dh.raaps.core.aps.BolusProjections
import de.dh.raaps.core.aps.DeferredBolus
import de.dh.raaps.core.aps.TreatmentLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.round
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
    val timeFromMeal: Minutes,
    val timeFromNow: Minutes,
    val partWeight: Int?
) {
    /**
     * Updates the absolute timestamp and UI offset based on a (potentially moved) meal time.
     * Maintains the anchor to the meal but ensures the dose is not in the past.
     */
    fun updateForMealTime(newMealTimestamp: Timestamp, now: Timestamp): PlannedInsulinUiModel {
        val target = newMealTimestamp + timeFromMeal
        val coerced = if (target < now) now else target
        return copy(
            timestamp = coerced,
            timeFromNow = Minutes.timeDifference(now, coerced)
        )
    }

    /**
     * Updates the UI model based on a manual change of the absolute administration time.
     * Recalculates the anchor to the meal and the UI offset.
     */
    fun updateAbsoluteTime(newTimestamp: Timestamp, mealTimestamp: Timestamp, now: Timestamp): PlannedInsulinUiModel {
        val coerced = if (newTimestamp < now) now else newTimestamp
        return copy(
            timestamp = coerced,
            timeFromMeal = Minutes.timeDifference(mealTimestamp, coerced),
            timeFromNow = Minutes.timeDifference(now, coerced)
        )
    }

    companion object {
        /**
         * Creates a new UI model from a core planned insulin definition.
         */
        fun create(
            amount: InsulinAmount,
            timeFromMeal: Minutes,
            mealTimestamp: Timestamp,
            now: Timestamp,
            partWeight: Int? = null
        ): PlannedInsulinUiModel {
            val target = mealTimestamp + timeFromMeal
            val coerced = if (target < now) now else target
            return PlannedInsulinUiModel(
                amount = amount,
                timestamp = coerced,
                timeFromMeal = timeFromMeal,
                timeFromNow = Minutes.timeDifference(now, coerced),
                partWeight = partWeight
            )
        }
    }
}

/**
 * ViewModel for the Meal Bolus screen, managing the calculation cascade and temporal dependencies.
 *
 * ## Calculation Cascade & Dependencies (Stages):
 * 1. **Initial Proposal:** Current BG -> Suggested IMI -> Suggested Meal Time (T_meal).
 * 2. **Meal Context:** T_meal -> Projections at T_meal (IOB, COB, projected BG).
 * 3. **Bolus Suggestion:** (Carbs + Meal Type) AND (Projections @ T_meal) -> Proposed Bolus Amount.
 * 4. **Insulin Plan:** Bolus Amount + Meal Type -> Insulin Distribution Plan (Relative Offsets to T_meal).
 * 5. **Execution:** T_meal + Relative Offsets -> Absolute execution timestamps.
 *
 * ## Rules for Manual Changes:
 * - **T_meal:** Changing the meal time manually updates the IMI (offset). T_meal remains relative and slides
 *   with system time via the ticker to maintain this offset until submission. A change of T_meal
 *   conceptually also changes the projections and so all calculated boluses but for UI stability,
 *   the user must trigger the projections refresh manually.
 * - **Carbs/Meal Type:** Triggers Stage 3 & 4 (recalculates bolus and plan distribution).
 * - **Bolus Amount:** Triggers Stage 4 (recalculates plan distribution based on the new amount).
 * - **Plan Offsets:** Manually changing a plan time fixes its relative offset to T_meal.
 *
 * ## Temporal Stability:
 * - Projection data (IOB, COB, BG) is kept stable until an explicit refresh is triggered (Refresh Button).
 * - The UI indicates "stale" data if the reference timestamp differs more than 4 minutes from the
 *   new meal time.
 */
/**
 * User inputs for the meal bolus.
 */
data class MealInput(
    val carbsKe: Double = 0.0,
    val selectedMealType: MealType? = null,
    val manualBolus: InsulinAmount = InsulinAmount.ZERO,
    val mealTimestamp: Timestamp = Timestamp.now(),
    val mealTimeFromNow: Minutes = Minutes(0)
)

/**
 * Detailed bolus calculation breakdown (Stage 3).
 */
data class BolusCalculationDetails(
    val mealPart: InsulinAmount = InsulinAmount.ZERO,
    val correctionPart: InsulinAmount = InsulinAmount.ZERO,
    val iobPart: InsulinAmount = InsulinAmount.ZERO,
    val cobPart: InsulinAmount = InsulinAmount.ZERO,
    val deferredBolusPart: InsulinAmount = InsulinAmount.ZERO,
    val proposedTotal: InsulinAmount = InsulinAmount.ZERO
)

data class MealBolusUiState(
    val isLoading: Boolean = true,
    val input: MealInput = MealInput(),
    val projections: BolusProjections = BolusProjections(),
    val isProjectionsStale: Boolean = false,
    val calculation: BolusCalculationDetails = BolusCalculationDetails(),
    val insulinPlan: List<PlannedInsulinUiModel> = emptyList(),
    val mealTypes: List<MealType> = emptyList(),
    val targetBg: BgValue = BgValue(DEFAULT_BG_TARGET_MGDL),
    val lowThreshold: BgValue = BgValue(DEFAULT_BG_LOW_THRESHOLD_MGDL),
    val isf: BgDelta = BgDelta.fromMgDl(DEFAULT_ISF_MGDL_PER_UNIT.toInt()),
    val cr: Double = DEFAULT_CR_GRAM_PER_UNIT,
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
    private val bolusCorrectionCalculator = registry.systemManager.getBolusCorrectionCalculator()

    private var calculationJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            val now = Timestamp.now()
            val isf = therapyManager.getIsfFactor(now)
            val cr = therapyManager.getCrFactor(now)
            val bgSettings = therapyManager.getBgSettings()
            val targetBg = bgSettings.first
            val mealTypes = treatmentRepository.getAllMealTypes()

            val projections = bolusCorrectionCalculator.calculateBolusProjections(now)
            val projectedBg = projections.bg
            val suggestedImi = BolusCalculationMath.calculateSuggestedImi(projectedBg, therapyManager)
            val suggestedCarbsKe = BolusCalculationMath.calculateSuggestedCarbsKe(projectedBg, targetBg, isf, cr)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    input = MealInput(
                        mealTimestamp = now + Minutes(max(0, suggestedImi.value.toInt()).toShort()),
                        mealTimeFromNow = suggestedImi,
                        carbsKe = suggestedCarbsKe,
                    ),
                    mealTypes = mealTypes,
                    projections = projections,
                    targetBg = targetBg,
                    lowThreshold = bgSettings.second,
                    isf = isf,
                    cr = if (cr == 0.0) DEFAULT_CR_GRAM_PER_UNIT else cr,
                )
            }
            calculateBolus()
            startTicker()
            startCloseBannerTimer()
        }
    }

    fun onCarbsChange(ke: Double) {
        _uiState.update { it.copy(input = it.input.copy(carbsKe = max(0.0, ke))) }
        calculateBolus()
    }

    fun onMealTimeChange(timestamp: Timestamp) {
        val now = Timestamp.now()
        val offsetMinutes = round((timestamp.ms - now.ms) / 60000.0).toInt().coerceIn(-30, 60)
        val newMealTimestamp = now + Minutes(offsetMinutes.toShort())

        viewModelScope.launch {
            val projections = bolusCorrectionCalculator.calculateBolusProjections(newMealTimestamp)

            _uiState.update { state ->
                state.copy(
                    input = state.input.copy(
                        mealTimestamp = newMealTimestamp,
                        mealTimeFromNow = Minutes(offsetMinutes.toShort())
                    ),
                    projections = projections,
                )
            }
            calculateBolus()
        }
    }

    fun onRefreshProjections() {
        viewModelScope.launch {
            val state = _uiState.value
            val projections = bolusCorrectionCalculator.calculateBolusProjections(state.input.mealTimestamp)

            _uiState.update {
                it.copy(
                    projections = projections,
                )
            }
            calculateBolus()
        }
    }

    fun onMealTypeChange(mealType: MealType) {
        _uiState.update { it.copy(input = it.input.copy(selectedMealType = mealType)) }
        calculateBolus()
    }

    fun onManualBolusChange(amount: Double) {
        _uiState.update { it.copy(input = it.input.copy(manualBolus = InsulinAmount(amount).coerceAtLeast(InsulinAmount.ZERO))) }
        recalculateInsulinPlanTimes() // Re-calculate distribution if manual amount changes
    }

    fun onPlannedInsulinTimeChange(index: Int, newTimestamp: Timestamp) {
        val now = Timestamp.now()

        _uiState.update { s ->
            val newPlan = s.insulinPlan.toMutableList()
            if (index in newPlan.indices) {
                newPlan[index] = newPlan[index].updateAbsoluteTime(newTimestamp, s.input.mealTimestamp, now)
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
        calculationJob?.cancel()
        calculationJob = viewModelScope.launch {
            val state = _uiState.value

            val result = bolusCorrectionCalculator.calculateBolusParts(
                carbsKe = state.input.carbsKe,
                mealTimestamp = state.input.mealTimestamp,
                projectedBg = state.projections.bg,
                projectedIob = state.projections.iob,
                projectedCob = state.projections.cob,
                deferredBolusAmount = state.projections.deferredBolusAmount
            )

            _uiState.update {
                it.copy(
                    calculation = BolusCalculationDetails(
                        mealPart = result.mealPart,
                        correctionPart = result.correctionPart,
                        iobPart = result.iobPart,
                        cobPart = result.cobPart,
                        deferredBolusPart = result.deferredBolusPart,
                        proposedTotal = result.totalProposed,
                    ),
                    input = it.input.copy(
                        manualBolus = result.totalProposed
                    )
                )
            }
            recalculateInsulinPlanTimesS()
        }
    }

    private fun recalculateInsulinPlanTimes() {
        viewModelScope.launch {
            recalculateInsulinPlanTimesS()
        }
    }

    private suspend fun recalculateInsulinPlanTimesS() {
        val state = _uiState.value
        val now = Timestamp.now()

        val projectedBg = state.projections.bg
        val suggestedImi = BolusCalculationMath.calculateSuggestedImi(projectedBg, therapyManager)

        val newPlan = bolusCorrectionCalculator.distributeInsulinPlan(
            manualBolus = state.input.manualBolus,
            correctionPart = state.calculation.correctionPart,
            mealType = state.input.selectedMealType,
            suggestedImi = suggestedImi
        )

        val uiPlan = newPlan.map { core ->
            PlannedInsulinUiModel.create(
                amount = core.amount,
                timeFromMeal = core.timeFromMeal,
                mealTimestamp = state.input.mealTimestamp,
                now = now,
                partWeight = core.partWeight
            )
        }
        _uiState.update { it.copy(insulinPlan = uiPlan) }
    }

    fun submit(lock: TreatmentLock, onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.submissionStatus != SubmissionStatus.NotSubmitted) return

        viewModelScope.launch {
            _uiState.update { it.copy(submissionStatus = SubmissionStatus.Submitting) }
            try {
                // 1. Record Meal
                if ((state.input.carbsKe > 0) && (state.input.selectedMealType != null)) {
                    val mealEntry = MealEntry(
                        timestamp = state.input.mealTimestamp,
                        carbGrams = state.input.carbsKe * 10.0,
                        mealType = state.input.selectedMealType
                    )
                    treatmentRepository.addMealEntry(mealEntry)

                    // Schedule reminder
                    if (state.isMealReminderEnabled) {
                        therapyManager.scheduleMealReminder(state.input.mealTimestamp)
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
                    val newMealTimestamp = now + state.input.mealTimeFromNow

                    // 2. Check for stale projections (> 4 mins old)
                    val isStale = (newMealTimestamp - state.projections.timestamp) > Minutes(4).inMs()

                    // 3. Update absolute times in insulin plan (based on sliding meal time)
                    val updatedPlan = state.insulinPlan.map { item ->
                        item.updateForMealTime(newMealTimestamp, now)
                    }

                    state.copy(
                        input = state.input.copy(mealTimestamp = newMealTimestamp),
                        insulinPlan = updatedPlan,
                        isProjectionsStale = isStale
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