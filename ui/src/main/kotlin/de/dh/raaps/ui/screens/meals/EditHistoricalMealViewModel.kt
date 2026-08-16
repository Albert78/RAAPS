package de.dh.raaps.ui.screens.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
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
    val meal: MealEntry? = null,
    val editedCarbsKe: Double = 0.0,
    val editedTimestamp: Timestamp = Timestamp.now(),
    val editedMealType: MealType? = null,
    val mealTypes: List<MealType> = emptyList(),
    val isSaving: Boolean = false
)

class EditHistoricalMealViewModel(
    private val registry: SystemRegistry,
    private val mealId: Long
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditHistoricalMealUiState())
    val uiState: StateFlow<EditHistoricalMealUiState> = _uiState.asStateFlow()

    private val treatmentRepository = registry.treatmentRepository

    init {
        loadMeal()
    }

    private fun loadMeal() {
        viewModelScope.launch {
            val meal = treatmentRepository.getMeal(mealId)
            val mealTypes = treatmentRepository.getAllMealTypes()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    meal = meal,
                    mealTypes = mealTypes,
                    editedCarbsKe = meal?.let { m -> m.carbGrams / 10.0 } ?: 0.0,
                    editedTimestamp = meal?.timestamp ?: Timestamp.now(),
                    editedMealType = meal?.mealType
                )
            }
        }
    }

    fun onCarbsChange(ke: Double) {
        _uiState.update { it.copy(editedCarbsKe = ke) }
    }

    fun onTimestampChange(timestamp: Timestamp) {
        val now = Timestamp.now()
        val minTime = now - Minutes(120)
        val maxTime = now + Minutes(30)
        
        val cappedTimestamp = if (timestamp < minTime) minTime else if (timestamp > maxTime) maxTime else timestamp
        _uiState.update { it.copy(editedTimestamp = cappedTimestamp) }
    }

    fun onMealTypeChange(mealType: MealType) {
        _uiState.update { it.copy(editedMealType = mealType) }
    }

    fun saveChanges(onSuccess: () -> Unit) {
        val state = _uiState.value
        val meal = state.meal ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val updatedMeal = meal.copy(
                carbGrams = state.editedCarbsKe * 10.0,
                timestamp = state.editedTimestamp,
                mealType = state.editedMealType ?: meal.mealType
            )
            treatmentRepository.addMealEntry(updatedMeal)
            _uiState.update { 
                it.copy(
                    isSaving = false, 
                    meal = updatedMeal
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