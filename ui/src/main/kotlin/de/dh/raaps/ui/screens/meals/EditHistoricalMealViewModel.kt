package de.dh.raaps.ui.screens.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.MEAL_ADD_THRESHOLD_MINUTES
import de.dh.raaps.common.model.MEAL_EDIT_THRESHOLD_HOURS
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditHistoricalMealUiState(
    val isLoading: Boolean = true,
    val isAddMode: Boolean = false,
    val meal: MealEntry? = null,
    val editedCarbsKe: Double = 0.0,
    val editedTimestamp: Timestamp = Timestamp.now(),
    val editedMealType: MealType? = null,
    val mealTypes: List<MealType> = emptyList(),
    val isSaving: Boolean = false,
    val isFormValid: Boolean = false
)

class EditHistoricalMealViewModel(
    private val registry: SystemRegistry,
    private val mealId: Long
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditHistoricalMealUiState())
    val uiState: StateFlow<EditHistoricalMealUiState> = _uiState.asStateFlow()

    private val treatmentRepository = registry.treatmentRepository
    private val isAddMode = mealId == ID_UNDEFINED

    init {
        loadMeal()
    }

    private fun loadMeal() {
        viewModelScope.launch {
            val meal = if (isAddMode) null else treatmentRepository.getMeal(mealId)
            val mealTypes = treatmentRepository.getAllMealTypes()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isAddMode = isAddMode,
                    meal = meal,
                    mealTypes = mealTypes,
                    editedCarbsKe = meal?.let { m -> m.carbGrams / 10.0 } ?: 0.0,
                    editedTimestamp = meal?.timestamp ?: (Timestamp.now() - Minutes(15)),
                    editedMealType = meal?.mealType
                )
            }
            validateForm()
        }
    }

    fun onCarbsChange(ke: Double) {
        _uiState.update { it.copy(editedCarbsKe = ke) }
        validateForm()
    }

    fun onTimestampChange(timestamp: Timestamp) {
        val now = Timestamp.now()
        val thresholdMinutes = if (isAddMode) MEAL_ADD_THRESHOLD_MINUTES else (MEAL_EDIT_THRESHOLD_HOURS * 60)
        val minTime = now - Minutes(thresholdMinutes.toShort())
        val maxTime = now
        
        val cappedTimestamp = if (timestamp < minTime) minTime else if (timestamp > maxTime) maxTime else timestamp
        _uiState.update { it.copy(editedTimestamp = cappedTimestamp) }
        validateForm()
    }

    fun onMealTypeChange(mealType: MealType) {
        _uiState.update { it.copy(editedMealType = mealType) }
        validateForm()
    }

    private fun validateForm() {
        _uiState.update { 
            it.copy(isFormValid = it.editedCarbsKe > 0.0 && it.editedMealType != null)
        }
    }

    fun saveChanges(onSuccess: () -> Unit) {
        val state = _uiState.value
        val mealType = state.editedMealType ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val mealToSave = if (isAddMode) {
                MealEntry(
                    id = ID_UNDEFINED,
                    timestamp = state.editedTimestamp,
                    carbGrams = state.editedCarbsKe * 10.0,
                    mealType = mealType
                )
            } else {
                state.meal?.copy(
                    carbGrams = state.editedCarbsKe * 10.0,
                    timestamp = state.editedTimestamp,
                    mealType = mealType
                ) ?: return@launch
            }
            
            treatmentRepository.addMealEntry(mealToSave)
            _uiState.update { 
                it.copy(
                    isSaving = false, 
                    meal = if (isAddMode) mealToSave else it.meal
                ) 
            }
            onSuccess()
        }
    }

    fun deleteMeal(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value.meal?.let {
                treatmentRepository.removeMealEntry(it)
                onSuccess()
            }
        }
    }

    companion object {
        class Factory(private val registry: SystemRegistry, private val mealId: Long) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return EditHistoricalMealViewModel(registry, mealId) as T
            }
        }
    }
}