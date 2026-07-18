package de.dh.raaps.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.core.RAAPSRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val apsMode: ApsMode = ApsMode.Manual
)

/**
 * ViewModel for the main dashboard screen.
 */
class DashboardViewModel(
    private val raapsRegistry: RAAPSRegistry
) : ViewModel() {
    private val aps = raapsRegistry.aps
    private val _uiState = MutableStateFlow(DashboardUiState())

    val uiState: StateFlow<DashboardUiState> = combine(
        _uiState,
        aps.apsMode
    ) { state, mode ->
        state.copy(apsMode = mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private val glucoseRepository = raapsRegistry.glucoseRepository
    private val treatmentRepository = raapsRegistry.treatmentRepository

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            reload_suspend()
        }
    }

    private suspend fun reload_suspend() {
        _uiState.update { it.copy(isLoading = true) }
        try {
            updateUiModel()
            _uiState.update { it.copy(isLoading = false, isError = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, isError = true) }
        }
    }

    private fun updateUiModel() {
        // Logic to update the dashboard based on repository data
    }

    fun setApsMode(mode: ApsMode) {
        aps.setApsMode(mode)
    }

    companion object {
        class Factory(private val registry: RAAPSRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return DashboardViewModel(registry) as T
            }
        }
    }
}