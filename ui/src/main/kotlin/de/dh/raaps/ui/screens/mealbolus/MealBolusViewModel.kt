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

data class MealBolusUiState(
    val isLoading: Boolean = true,
    val mealTimestamp: Timestamp = Timestamp.now(),
    val referenceBg: BgValue? = null,
    val referenceTimestamp: Timestamp = Timestamp.now(),
    val isProjected: Boolean = false,
    val seaMinutes: Minutes = Minutes(0),
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
                    mealTimestamp = now + Minutes(max(0, baseData.suggestedSea.value.toInt()).toShort()),
                    seaMinutes = baseData.suggestedSea,
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
        val offset = kotlin.math.round((timestamp.ms - now.ms) / 60000.0).toInt().coerceIn(-120, 60)
        val cappedTimestamp = now + Minutes(offset.toShort())
        _uiState.update { it.copy(mealTimestamp = cappedTimestamp) }
        calculateBolus()
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
                suggestedSea = state.seaMinutes,
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

                    // We shouldn't continuously reset mealTimestamp to now + seaMinutes,
                    // as that undoes the user's manual changes.
                    // If we want the time to "roll forward" if the user hasn't touched it,
                    // we would need an `isMealTimeUserModified` flag.
                    // For now, let's just let the time stay as is once calculated.
                    state
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