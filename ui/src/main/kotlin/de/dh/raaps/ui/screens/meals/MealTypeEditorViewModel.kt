package de.dh.raaps.ui.screens.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.core.SystemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

data class MealTypeEditorUiState(
    val id: String? = null,
    val name: String = "",
    val cat: String = "180",
    val components: List<CarbCurveComponentData> = listOf(CarbCurveComponentData(100, Minutes(30))),
    val isSaving: Boolean = false,
    val isLoading: Boolean = false
) {
    val isValid: Boolean
        get() {
            val nameValid = name.isNotBlank()
            val catValid = (cat.toIntOrNull() ?: 0) > 0
            val componentsValid = components.isNotEmpty()
            val weightSum = components.sumOf { it.weight }
            val sumValid = abs(weightSum - 100) == 0
            return nameValid && catValid && componentsValid && sumValid
        }
}

class MealTypeEditorViewModel(
    private val registry: SystemRegistry,
    private val mealTypeId: String?
) : ViewModel() {

    private val treatmentRepository = registry.treatmentRepository
    private val _uiState = MutableStateFlow(MealTypeEditorUiState())
    val uiState: StateFlow<MealTypeEditorUiState> = _uiState.asStateFlow()

    init {
        if (mealTypeId != null) {
            loadMealType(mealTypeId)
        }
    }

    private fun loadMealType(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val mealType = treatmentRepository.getAllMealTypes().find { it.id == id }
            if (mealType != null) {
                _uiState.update {
                    it.copy(
                        id = mealType.id,
                        name = mealType.name,
                        cat = mealType.cat.value.toString(),
                        components = mealType.components,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onCatChange(cat: String) {
        _uiState.update { it.copy(cat = cat) }
    }

    fun onComponentsChange(components: List<CarbCurveComponentData>) {
        _uiState.update { it.copy(components = components) }
    }

    fun save(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.isValid) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val mealType = MealType(
                id = state.id ?: UUID.randomUUID().toString(),
                name = state.name,
                cat = Minutes((state.cat.toIntOrNull() ?: 180).toShort()),
                components = state.components
            )
            treatmentRepository.insertMealType(mealType)
            onSuccess()
        }
    }

    companion object {
        class Factory(private val registry: SystemRegistry, private val mealTypeId: String?) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return MealTypeEditorViewModel(registry, mealTypeId) as T
            }
        }
    }
}