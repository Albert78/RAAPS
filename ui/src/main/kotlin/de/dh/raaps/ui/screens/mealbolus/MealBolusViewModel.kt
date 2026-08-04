package de.dh.raaps.ui.screens.mealbolus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.DEFAULT_CR_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.RAAPSRegistry
import de.dh.raaps.core.pump.PumpCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

data class MealBolusUiState(
    val isLoading: Boolean = true,
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
    val isSubmitting: Boolean = false
)

class MealBolusViewModel(
    private val raapsRegistry: RAAPSRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(MealBolusUiState())
    val uiState: StateFlow<MealBolusUiState> = _uiState.asStateFlow()

    private val therapyManager = raapsRegistry.therapyManager
    private val glucoseRepository = raapsRegistry.glucoseRepository
    private val treatmentRepository = raapsRegistry.treatmentRepository
    private val pumpManager = raapsRegistry.pumpManager
    private val calculationModel = raapsRegistry.carbsInsulinCalculationModel

    init {
        viewModelScope.launch {
            val now = Timestamp.now()
            val therapySettings = therapyManager.getActiveTherapySettings()
            val isf = therapyManager.getIsfFactor(now).mgdl.toInt()
            val cr = therapyManager.getCrFactor(now)
            val bgSettings = therapyManager.getBgSettings()
            val mealTypes = treatmentRepository.getAllMealTypes()

            val lastReading = glucoseRepository.loadBgReadings(now.minus(de.dh.raaps.common.model.data.Minutes(30))).lastOrNull()
            val currentBg = lastReading?.value?.mgdl?.toInt()

            val historyLimit = now.minusHours(25)
            val insulinHistory = treatmentRepository.getInsulinApplications(from = historyLimit)
            val mealsHistory = treatmentRepository.getMeals(from = historyLimit)

            val iob = calculationModel.iob(insulinHistory, now, therapySettings.insulinProfile.dia, therapySettings.insulinProfile.peak)
            val cob = calculationModel.cob(mealsHistory, now)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    mealTypes = mealTypes,
                    selectedMealType = mealTypes.firstOrNull(),
                    currentBg = currentBg,
                    targetBg = bgSettings.first.mgdl.toInt(),
                    isf = if (isf == 0) DEFAULT_ISF_MGDL_PER_UNIT.toInt() else isf, // Fallback
                    cr = if (cr == 0.0) 10.0 else DEFAULT_CR_GRAM_PER_UNIT, // Fallback
                    iob = iob,
                    cob = cob
                )
            }
            calculateBolus()
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

        val total = max(0.0, mealPart + correctionPart - iobPart + cobPart)

        // Round to 2 decimal places
        val roundedTotal = Math.round(total * 100.0) / 100.0

        _uiState.update {
            it.copy(
                mealPart = mealPart,
                correctionPart = correctionPart,
                iobPart = iobPart,
                cobPart = cobPart,
                proposedBolus = roundedTotal,
                manualBolus = roundedTotal
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

                // 1. Record Meal
                if (state.carbsKe > 0 && state.selectedMealType != null) {
                    val mealEntry = MealEntry(
                        timestamp = now,
                        carbGrams = state.carbsKe * 10.0,
                        mealType = state.selectedMealType
                    )
                    treatmentRepository.addMealEntry(mealEntry)
                }

                // 2. Deliver Bolus
                if (state.manualBolus > 0.0) {
                    val amount = InsulinAmount(state.manualBolus)
                    pumpManager.issueCommand(PumpCommand.DeliverBolus(amount), isCancelableAPSCommand = false)

                    // Add to history manually for immediate feedback
                    val insulinType = therapyManager.getPumpInsulinType()
                    val application = InsulinApplication(
                        timestamp = now,
                        amount = state.manualBolus,
                        insulinType = insulinType,
                        origin = InsulinOrigin.Manual
                    )
                    treatmentRepository.addInsulinApplication(application)
                }

                onSuccess()
            } catch (e: Exception) {
                // TODO: Error handling
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    companion object {
        class Factory(private val registry: RAAPSRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return MealBolusViewModel(registry) as T
            }
        }
    }
}