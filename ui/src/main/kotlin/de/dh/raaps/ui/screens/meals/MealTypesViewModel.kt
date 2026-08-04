package de.dh.raaps.ui.screens.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.dh.raaps.common.model.MealType
import de.dh.raaps.core.SystemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MealTypesUiState(
    val mealTypes: List<MealType> = emptyList(),
    val isLoading: Boolean = false
)

class MealTypesViewModel(
    private val registry: SystemRegistry
) : ViewModel() {

    private val treatmentRepository = registry.treatmentRepository
    private val _uiState = MutableStateFlow(MealTypesUiState())
    val uiState: StateFlow<MealTypesUiState> = _uiState.asStateFlow()

    init {
        loadMealTypes()
    }

    private fun loadMealTypes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val types = treatmentRepository.getAllMealTypes()
            _uiState.value = MealTypesUiState(mealTypes = types, isLoading = false)
        }
    }

    fun deleteMealType(mealType: MealType) {
        viewModelScope.launch {
            treatmentRepository.deleteMealType(mealType)
            loadMealTypes()
        }
    }

    companion object {
        class Factory(private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MealTypesViewModel(registry) as T
            }
        }
    }
}