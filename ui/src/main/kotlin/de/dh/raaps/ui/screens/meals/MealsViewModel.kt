package de.dh.raaps.ui.screens.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.dh.raaps.common.model.MEAL_EDIT_THRESHOLD_HOURS
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.core.SystemRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MealsUiState(
    val meals: List<MealEntry> = emptyList(),
    val editThresholdHours: Int = MEAL_EDIT_THRESHOLD_HOURS
)

class MealsViewModel(
    private val registry: SystemRegistry
) : ViewModel() {

    private val treatmentRepository = registry.treatmentRepository

    val uiState: StateFlow<MealsUiState> = treatmentRepository.observeMeals()
        .map { meals ->
            MealsUiState(
                meals = meals.sortedByDescending { it.timestamp },
                editThresholdHours = MEAL_EDIT_THRESHOLD_HOURS
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MealsUiState()
        )

    fun deleteMeal(meal: MealEntry) {
        viewModelScope.launch {
            treatmentRepository.removeMealEntry(meal)
        }
    }

    companion object {
        class Factory(private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MealsViewModel(registry) as T
            }
        }
    }
}