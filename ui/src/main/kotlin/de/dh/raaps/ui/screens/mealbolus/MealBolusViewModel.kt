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
import de.dh.raaps.common.model.PlannedInsulin
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.aps.DeferredBolus
import de.dh.raaps.core.aps.LockResult
import de.dh.raaps.core.aps.TreatmentLock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

data class MealBolusUiState(
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val originalMealTimestamp: Timestamp? = null,
    val originalMealId: Long = ID_UNDEFINED,
    val mealTimestamp: Timestamp = Timestamp.now(),
    val referenceBg: BgValue? = null,
    val referenceTimestamp: Timestamp = Timestamp.now(),
    val isProjected: Boolean = false,
    val seaMinutes: Int = 0,
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
    val insulinPlan: List<PlannedInsulin> = emptyList(),
    val isInsulinPlanExpanded: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLockAcquired: Boolean = false,
    val isBusy: Boolean = false,
    val lockBusyOwner: String? = null,
    val lockError: Boolean = false,
)

class MealBolusViewModel(
    private val registry: SystemRegistry,
    private val mealId: Long? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(MealBolusUiState())
    val uiState: StateFlow<MealBolusUiState> = _uiState.asStateFlow()

    private val therapyManager = registry.therapyManager
    private val treatmentRepository = registry.treatmentRepository

    private var treatmentLock: TreatmentLock? = null

    init {
        acquireLock()
        viewModelScope.launch {
            val now = Timestamp.now()
            val isf = therapyManager.getIsfFactor(now).mgdl.toInt()
            val cr = therapyManager.getCrFactor(now)
            val bgSettings = therapyManager.getBgSettings()
            val mealTypes = treatmentRepository.getAllMealTypes()

            val existingMeal = mealId?.let { treatmentRepository.getMeal(it) }
            val baseData = registry.systemManager.getBolusCorrectionCalculator().calculateBaseData()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEditMode = existingMeal != null,
                    originalMealTimestamp = existingMeal?.timestamp,
                    originalMealId = existingMeal?.id ?: ID_UNDEFINED,
                    mealTimestamp = if (existingMeal != null) {
                        existingMeal.timestamp
                    } else {
                        now + Minutes(max(0, baseData.suggestedSea).toShort())
                    },
                    seaMinutes = if (existingMeal == null) baseData.suggestedSea else 0,
                    carbsKe = existingMeal?.let { meal -> meal.carbGrams / 10.0 } ?: baseData.suggestedCarbsKe,
                    mealTypes = mealTypes,
                    selectedMealType = existingMeal?.mealType,
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
        }
    }

    private fun acquireLock() {
        viewModelScope.launch {
            var retryAttempt = 0
            while (retryAttempt < 2) {
                val result = therapyManager.tryAcquire("MealBolusScreen") { treatmentLock ->
                    this@MealBolusViewModel.treatmentLock = treatmentLock
                    _uiState.update { it.copy(isLockAcquired = true, isBusy = false) }
                    try {
                        awaitCancellation()
                    } finally {
                        this@MealBolusViewModel.treatmentLock = null
                    }
                }

                if (result is LockResult.Busy) {
                    if (retryAttempt == 0) {
                        _uiState.update { it.copy(isBusy = true, lockBusyOwner = result.owner) }
                        delay(3.seconds)
                        retryAttempt++
                    } else {
                        _uiState.update { it.copy(isBusy = false, lockError = true) }
                        break
                    }
                } else {
                    break
                }
            }
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
        _uiState.update { it.copy(mealTimestamp = cappedTimestamp, seaMinutes = offset) }
        calculateBolus()
    }

    fun onMealTypeChange(mealType: MealType) {
        _uiState.update { it.copy(selectedMealType = mealType) }
        calculateBolus()
    }

    fun onManualBolusChange(amount: Double) {
        if (_uiState.value.isEditMode) return // Prevent bolus change in edit mode
        _uiState.update { it.copy(manualBolus = InsulinAmount(amount).coerceAtLeast(InsulinAmount.ZERO)) }
        updateInsulinPlanTimes() // Re-calculate distribution if manual amount changes
    }

    fun onPlannedInsulinTimeChange(index: Int, newTimestamp: Timestamp) {
        val now = Timestamp.now()
        val offset = kotlin.math.round((newTimestamp.ms - now.ms) / 60000.0)
        _uiState.update { state ->
            val newPlan = state.insulinPlan.toMutableList()
            if (index in newPlan.indices) {
                newPlan[index] = newPlan[index].copy(
                    timestamp = newTimestamp,
                    offsetMinutes = offset.toInt(),
                    isUserModified = true
                )
            }
            state.copy(insulinPlan = newPlan)
        }
    }

    fun toggleInsulinPlanExpanded() {
        _uiState.update { it.copy(isInsulinPlanExpanded = !it.isInsulinPlanExpanded) }
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
                    isProjected = result.calculationTimestamp.ms > now.ms,
                    iob = result.iobPart,
                    cob = result.cobGrams,
                    projectedIob = result.iobPart,
                    projectedCob = result.cobGrams,
                    mealPart = result.mealPart,
                    correctionPart = result.correctionPart,
                    iobPart = result.iobPart,
                    cobPart = result.cobPart,
                    proposedBolus = result.totalProposed,
                    manualBolus = if (state.isEditMode) InsulinAmount.ZERO else result.totalProposed
                )
            }
            updateInsulinPlanTimes()
        }
    }

    private fun updateInsulinPlanTimes() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isEditMode) {
                _uiState.update { it.copy(insulinPlan = emptyList()) }
                return@launch
            }

            val bolusBaseTime = state.mealTimestamp - Minutes(state.seaMinutes.toShort())

            val newPlan = registry.systemManager.getBolusCorrectionCalculator().distributeInsulinPlan(
                manualBolus = state.manualBolus,
                correctionPart = state.correctionPart,
                mealType = state.selectedMealType,
                mealTimestamp = bolusBaseTime,
                existingPlan = state.insulinPlan
            )
            _uiState.update { it.copy(insulinPlan = newPlan) }
        }
    }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val lock = treatmentLock ?: throw IllegalStateException("Action attempted without holding the lock")

                // 1. Record/Update Meal
                if ((state.carbsKe > 0) && (state.selectedMealType != null)) {
                    val mealEntry = MealEntry(
                        id = if (state.isEditMode) state.originalMealId else ID_UNDEFINED,
                        timestamp = state.mealTimestamp,
                        carbGrams = state.carbsKe * 10.0,
                        mealType = state.selectedMealType
                    )
                    treatmentRepository.addMealEntry(mealEntry)

                    // Schedule reminder
                    if (!state.isEditMode) {
                        therapyManager.scheduleMealReminder(lock, state.mealTimestamp)
                    }
                }

                // 2. Deliver Insulin Plan (only if NOT editing)
                if (!state.isEditMode && state.insulinPlan.isNotEmpty()) {
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

                _uiState.update { it.copy(isSubmitting = false) }
                onSuccess()
            } catch (_: Exception) {
                // TODO: Error handling
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (true) {
                delay(5.seconds)
                val now = Timestamp.now()
                _uiState.update { state ->
                    if (state.isEditMode || state.isSubmitting) return@update state

                    val updatedMealTime = now + Minutes(state.seaMinutes.toShort())
                    val updatedPlan = state.insulinPlan.map { planItem ->
                        planItem.copy(timestamp = now + Minutes(planItem.offsetMinutes.toShort()))
                    }

                    state.copy(
                        mealTimestamp = updatedMealTime,
                        insulinPlan = updatedPlan
                    )
                }
            }
        }
    }

    companion object {
        class Factory(private val registry: SystemRegistry, private val mealId: Long? = null) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return MealBolusViewModel(registry, mealId) as T
            }
        }
    }
}
