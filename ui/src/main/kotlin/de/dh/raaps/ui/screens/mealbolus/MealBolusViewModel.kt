package de.dh.raaps.ui.screens.mealbolus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.DEFAULT_CR_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.ID_MEAL_STANDARD
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
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
import kotlin.math.round
import kotlin.time.Duration.Companion.seconds

data class MealBolusUiState(
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val originalMealTimestamp: Timestamp? = null,
    val originalMealId: Long = ID_UNDEFINED,
    val carbsKe: Double = 0.0,
    val mealTypes: List<MealType> = emptyList(),
    val selectedMealType: MealType? = null,
    val currentBg: Int? = null,
    val targetBg: Int = 100,
    val isf: Int = 50,
    val cr: Double = 10.0,
    val iob: Double = 0.0,
    val cob: Double = 0.0,
    val mealPart: Double = 0.0,
    val correctionPart: Double = 0.0,
    val iobPart: Double = 0.0,
    val cobPart: Double = 0.0,
    val proposedBolus: Double = 0.0,
    val manualBolus: Double = 0.0,
    val isAutomaticMode: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLockAcquired: Boolean = false,
    val isBusy: Boolean = false,
    val lockBusyOwner: String? = null,
    val lockError: Boolean = false,
)

class MealBolusViewModel(
    private val systemRegistry: SystemRegistry,
    private val mealId: Long? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(MealBolusUiState())
    val uiState: StateFlow<MealBolusUiState> = _uiState.asStateFlow()

    private val therapyManager = systemRegistry.therapyManager
    private val glucoseRepository = systemRegistry.glucoseRepository
    private val treatmentRepository = systemRegistry.treatmentRepository
    private val calculationModel = systemRegistry.carbsInsulinCalculationModel

    private var treatmentLock: TreatmentLock? = null

    init {
        acquireLock()
        viewModelScope.launch {
            val now = Timestamp.now()
            val therapySettings = therapyManager.getActiveTherapySettings()
            val isf = therapyManager.getIsfFactor(now).mgdl.toInt()
            val cr = therapyManager.getCrFactor(now)
            val bgSettings = therapyManager.getBgSettings()
            val mealTypes = treatmentRepository.getAllMealTypes()

            val lastReading = glucoseRepository.loadBgReadings(now - Minutes(30)).lastOrNull()
            val currentBg = lastReading?.value?.mgdl?.toInt()

            val historyLimit = now.minusHours(25)
            val insulinHistory = treatmentRepository.getInsulinApplications(from = historyLimit)
            val mealsHistory = treatmentRepository.getMeals(from = historyLimit)

            val iob = calculationModel.iob(insulinHistory, now, therapySettings.insulinProfile.dia, therapySettings.insulinProfile.peak)
            val cob = calculationModel.cob(mealsHistory, now)

            val existingMeal = mealId?.let { treatmentRepository.getMeal(it) }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEditMode = existingMeal != null,
                    originalMealTimestamp = existingMeal?.timestamp,
                    originalMealId = existingMeal?.id ?: ID_UNDEFINED,
                    carbsKe = existingMeal?.let { meal -> meal.carbGrams / 10.0 } ?: 0.0,
                    mealTypes = mealTypes,
                    selectedMealType = existingMeal?.mealType ?: mealTypes.find { it.id == ID_MEAL_STANDARD } ?: mealTypes.firstOrNull(),
                    currentBg = currentBg,
                    targetBg = bgSettings.first.mgdl.toInt(),
                    isf = if (isf == 0) DEFAULT_ISF_MGDL_PER_UNIT.toInt() else isf,
                    cr = if (cr == 0.0) DEFAULT_CR_GRAM_PER_UNIT else cr,
                    iob = iob,
                    cob = cob
                )
            }
            calculateBolus()
        }

        viewModelScope.launch {
            systemRegistry.systemManager.apsMode.collect { mode ->
                _uiState.update { it.copy(isAutomaticMode = mode == ApsMode.AutoCorrection) }
                calculateBolus()
            }
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

    fun onMealTypeChange(mealType: MealType) {
        _uiState.update { it.copy(selectedMealType = mealType) }
    }

    fun onManualBolusChange(amount: Double) {
        if (_uiState.value.isEditMode) return // Prevent bolus change in edit mode
        _uiState.update { it.copy(manualBolus = max(0.0, amount)) }
    }

    private fun calculateBolus() {
        val state = _uiState.value
        val carbsGrams = state.carbsKe * 10.0
        val mealPart = carbsGrams / state.cr

        val currentBg = state.currentBg ?: state.targetBg
        val bgDiff = currentBg - state.targetBg
        val correctionPart = if (bgDiff > 0) bgDiff.toDouble() / state.isf else 0.0

        val iobPart = state.iob
        val cobPart = state.cob / state.cr

        val total = max(0.0, (mealPart + correctionPart - iobPart) + cobPart)

        // Round to 2 decimal places
        val roundedTotal = round(total * 100.0) / 100.0

        _uiState.update {
            it.copy(
                mealPart = mealPart,
                correctionPart = correctionPart,
                iobPart = iobPart,
                cobPart = cobPart,
                proposedBolus = roundedTotal,
                manualBolus = if (state.isEditMode) 0.0 else roundedTotal
            )
        }
    }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val now = Timestamp.now()

                // 1. Record/Update Meal
                if (state.carbsKe > 0 && state.selectedMealType != null) {
                    val mealEntry = MealEntry(
                        id = if (state.isEditMode) state.originalMealId else ID_UNDEFINED,
                        timestamp = if (state.isEditMode) (state.originalMealTimestamp ?: now) else now,
                        carbGrams = state.carbsKe * 10.0,
                        mealType = state.selectedMealType
                    )
                    treatmentRepository.addMealEntry(mealEntry)
                }

                // 2. Deliver Bolus (only if NOT editing)
                if (!state.isEditMode && state.manualBolus > 0.0) {
                    val amount = InsulinAmount(state.manualBolus)
                    val lock = treatmentLock ?: throw IllegalStateException("Bolus delivery attempted without holding the lock")
                    therapyManager.issueBolus(lock, amount)

                    // Add to history manually for immediate feedback
                    val insulinType = therapyManager.getPumpInsulinType()
                    val application = InsulinApplication(
                        timestamp = now,
                        amount = state.manualBolus,
                        insulinType = insulinType,
                        origin = InsulinOrigin.Manual,
                        provisional = true
                    )
                    treatmentRepository.addInsulinApplication(application)
                }

                _uiState.update { it.copy(isSubmitting = false) }
                onSuccess()
            } catch (_: Exception) {
                // TODO: Error handling
                _uiState.update { it.copy(isSubmitting = false) }
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