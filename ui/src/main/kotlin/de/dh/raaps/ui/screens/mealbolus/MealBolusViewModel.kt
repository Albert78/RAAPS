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
    val timeFromMeal: Minutes,
    val coercedTimeFromMeal: Minutes,
    val description: String
)

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
/**
 * User inputs for the meal bolus.
 */
data class MealInput(
    val carbsKe: Double = 0.0,
    val selectedMealType: MealType? = null,
    val manualBolus: InsulinAmount = InsulinAmount.ZERO,
    val mealTimestamp: Timestamp = Timestamp.now(),
    val imi: Minutes = Minutes(0)
)

/**
 * System projections for the reference timestamp (Stage 2).
 */
data class BolusProjections(
    val bg: BgValue? = null,
    val timestamp: Timestamp = Timestamp.now(),
    val iob: InsulinAmount = InsulinAmount.ZERO,
    val cob: Double = 0.0,
    val isProjected: Boolean = false,
    val isStale: Boolean = false
)

/**
 * Detailed bolus calculation breakdown (Stage 3).
 */
data class BolusCalculationDetails(
    val mealPart: InsulinAmount = InsulinAmount.ZERO,
    val correctionPart: InsulinAmount = InsulinAmount.ZERO,
    val iobPart: InsulinAmount = InsulinAmount.ZERO,
    val cobPart: InsulinAmount = InsulinAmount.ZERO,
    val proposedTotal: InsulinAmount = InsulinAmount.ZERO
)

data class MealBolusUiState(
    val isLoading: Boolean = true,
    val input: MealInput = MealInput(),
    val projections: BolusProjections = BolusProjections(),
    val calculation: BolusCalculationDetails = BolusCalculationDetails(),
    val insulinPlan: List<PlannedInsulinUiModel> = emptyList(),
    val mealTypes: List<MealType> = emptyList(),
    val targetBg: BgValue = BgValue(DEFAULT_BG_TARGET_MGDL),
    val lowThreshold: BgValue = BgValue(DEFAULT_BG_LOW_THRESHOLD_MGDL),
    val isf: Int = DEFAULT_ISF_MGDL_PER_UNIT.toInt(),
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

    private var calculationJob: kotlinx.coroutines.Job? = null

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
                    input = MealInput(
                        mealTimestamp = now + Minutes(max(0, baseData.suggestedImi.value.toInt()).toShort()),
                        imi = baseData.suggestedImi,
                        carbsKe = baseData.suggestedCarbsKe,
                    ),
                    mealTypes = mealTypes,
                    projections = BolusProjections(
                        bg = baseData.referenceBg,
                        timestamp = baseData.referenceTimestamp,
                        isProjected = baseData.referenceTimestamp.ms > now.ms,
                    ),
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
        _uiState.update { it.copy(input = it.input.copy(carbsKe = max(0.0, ke))) }
        calculateBolus()
    }

    fun onMealTimeChange(timestamp: Timestamp) {
        val now = Timestamp.now()
        val offsetMinutes = kotlin.math.round((timestamp.ms - now.ms) / 60000.0).toInt().coerceIn(-120, 60)
        val newMealTimestamp = now + Minutes(offsetMinutes.toShort())

        _uiState.update { state ->
            val updatedInsulinPlan = state.insulinPlan.map { insulin ->
                // When the meal slides, we keep the relative 'timeFromMeal' and shift the absolute timestamp.
                val (coercedTime, coercedOffset) = coerceInsulinTime(newMealTimestamp + insulin.timeFromMeal, newMealTimestamp, now)
                insulin.copy(
                    timestamp = coercedTime,
                    coercedTimeFromMeal = coercedOffset
                )
            }

            state.copy(
                input = state.input.copy(
                    mealTimestamp = newMealTimestamp,
                    imi = Minutes(offsetMinutes.toShort())
                ),
                insulinPlan = updatedInsulinPlan
            )
        }
        // Recalculate bolus projections for the new meal time, but avoid re-distributing if only the time shifted
        calculateBolus(redistributePlan = false)
    }

    fun onRefreshProjections() {
        viewModelScope.launch {
            val state = _uiState.value
            val now = Timestamp.now()

            // Re-calculate the reference timestamp: use current meal timestamp for projection if future, else now.
            val newRefTime = if (state.input.mealTimestamp > now) state.input.mealTimestamp else now

            val result = registry.systemManager.getBolusCorrectionCalculator().calculateBolusParts(
                carbsKe = state.input.carbsKe,
                mealTimestamp = state.input.mealTimestamp,
                referenceTimestamp = newRefTime
            )

            _uiState.update {
                it.copy(
                    projections = BolusProjections(
                        bg = result.calculationBg,
                        timestamp = result.calculationTimestamp,
                        isStale = false,
                        isProjected = result.calculationTimestamp.ms > now.ms + 4 * MS_PER_MINUTE,
                        iob = result.iobPart,
                        cob = result.cobGrams,
                    ),
                    calculation = BolusCalculationDetails(
                        mealPart = result.mealPart,
                        correctionPart = result.correctionPart,
                        iobPart = result.iobPart,
                        cobPart = result.cobPart,
                        proposedTotal = result.totalProposed,
                    ),
                    input = it.input.copy(
                        manualBolus = result.totalProposed
                    )
                )
            }
            recalculateInsulinPlanTimes()
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
        val state = _uiState.value
        val now = Timestamp.now()
        val (coercedTimestamp, coercedOffset) = coerceInsulinTime(newTimestamp, state.input.mealTimestamp, now)
        val offset = Minutes.timeDifference(state.input.mealTimestamp, newTimestamp)

        _uiState.update { s ->
            val newPlan = s.insulinPlan.toMutableList()
            if (index in newPlan.indices) {
                newPlan[index] = newPlan[index].copy(
                    timestamp = coercedTimestamp,
                    timeFromMeal = offset,
                    coercedTimeFromMeal = coercedOffset
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

    private fun calculateBolus(redistributePlan: Boolean = true) {
        calculationJob?.cancel()
        calculationJob = viewModelScope.launch {
            val state = _uiState.value
            val now = Timestamp.now()

            val result = registry.systemManager.getBolusCorrectionCalculator().calculateBolusParts(
                carbsKe = state.input.carbsKe,
                mealTimestamp = state.input.mealTimestamp,
                referenceTimestamp = state.projections.timestamp
            )

            _uiState.update {
                it.copy(
                    projections = it.projections.copy(
                        bg = result.calculationBg,
                        timestamp = result.calculationTimestamp,
                        isProjected = result.calculationTimestamp.ms > now.ms + 4 * MS_PER_MINUTE,
                        iob = result.iobPart,
                        cob = result.cobGrams,
                    ),
                    calculation = BolusCalculationDetails(
                        mealPart = result.mealPart,
                        correctionPart = result.correctionPart,
                        iobPart = result.iobPart,
                        cobPart = result.cobPart,
                        proposedTotal = result.totalProposed,
                    ),
                    input = it.input.copy(
                        manualBolus = result.totalProposed
                    )
                )
            }
            if (redistributePlan) {
                recalculateInsulinPlanTimes()
            }
        }
    }

    private fun coerceInsulinTime(
        targetTimestamp: Timestamp,
        mealTimestamp: Timestamp,
        now: Timestamp = Timestamp.now()
    ): Pair<Timestamp, Minutes> {
        val coercedTimestamp = if (targetTimestamp < now) now else targetTimestamp
        return coercedTimestamp to Minutes.timeDifference(mealTimestamp, coercedTimestamp)
    }

    private fun recalculateInsulinPlanTimes() {
        viewModelScope.launch {
            val state = _uiState.value
            val now = Timestamp.now()

            val newPlan = registry.systemManager.getBolusCorrectionCalculator().distributeInsulinPlan(
                manualBolus = state.input.manualBolus,
                correctionPart = state.calculation.correctionPart,
                mealType = state.input.selectedMealType,
                suggestedImi = state.input.imi
            )

            val uiPlan = newPlan.map { core ->
                val (coercedTime, coercedOffset) = coerceInsulinTime(state.input.mealTimestamp + core.timeFromMeal, state.input.mealTimestamp, now)

                PlannedInsulinUiModel(
                    amount = core.amount,
                    timestamp = coercedTime,
                    timeFromMeal = core.timeFromMeal,
                    coercedTimeFromMeal = coercedOffset,
                    description = core.description
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
                    val newMealTimestamp = now + state.input.imi

                    // 2. Check for stale projections (> 4 mins old)
                    val isStale = (now.ms - state.projections.timestamp.ms) > 4 * MS_PER_MINUTE

                    // 3. Update absolute times in insulin plan (based on sliding meal time)
                    val updatedPlan = state.insulinPlan.map { item ->
                        val (coercedTime, coercedOffset) = coerceInsulinTime(newMealTimestamp + item.timeFromMeal, newMealTimestamp, now)
                        item.copy(
                            timestamp = coercedTime,
                            coercedTimeFromMeal = coercedOffset
                        )
                    }

                    state.copy(
                        input = state.input.copy(mealTimestamp = newMealTimestamp),
                        projections = state.projections.copy(isStale = isStale),
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