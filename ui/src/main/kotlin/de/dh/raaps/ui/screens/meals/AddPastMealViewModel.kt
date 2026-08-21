package de.dh.raaps.ui.screens.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddPastMealUiState(
    val isLoading: Boolean = true,
    val carbsKe: Double = 0.0,
    val selectedMealType: MealType? = null,
    val mealTimestamp: Timestamp = Timestamp.now().minusMinutes(30),
    val mealTypes: List<MealType> = emptyList(),
    val isSubmitting: Boolean = false,
    val isTimeValid: Boolean = true,
)

class AddPastMealViewModel(
    registry: SystemRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddPastMealUiState())
    val uiState: StateFlow<AddPastMealUiState> = _uiState.asStateFlow()

    private val treatmentRepository = registry.treatmentRepository

    init {
        viewModelScope.launch {
            val mealTypes = treatmentRepository.getAllMealTypes()
            _uiState.update { it.copy(
                isLoading = false,
                mealTypes = mealTypes,
                selectedMealType = mealTypes.firstOrNull()
            ) }
            validateTime()
        }
    }

    fun onCarbsChange(ke: Double) {
        _uiState.update { it.copy(carbsKe = ke.coerceAtLeast(0.0)) }
    }

    fun onMealTimeChange(timestamp: Timestamp) {
        _uiState.update { it.copy(mealTimestamp = timestamp) }
        validateTime()
    }

    fun onMealTypeChange(mealType: MealType) {
        _uiState.update { it.copy(selectedMealType = mealType) }
    }

    private fun validateTime() {
        val now = Timestamp.now()
        val diffMinutes = (now.ms - _uiState.value.mealTimestamp.ms) / (60 * 1000)
        _uiState.update { it.copy(isTimeValid = diffMinutes >= 25) }
    }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting || (!state.isTimeValid) || (state.selectedMealType == null) || (state.carbsKe <= 0)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val mealEntry = MealEntry(
                    timestamp = state.mealTimestamp,
                    carbGrams = state.carbsKe * 10.0,
                    mealType = state.selectedMealType
                )
                treatmentRepository.addMealEntry(mealEntry)
                onSuccess()
            } catch (_: Exception) {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    companion object {
        class Factory(private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return AddPastMealViewModel(registry) as T
            }
        }
    }
}