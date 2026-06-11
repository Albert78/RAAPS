package de.dh.raaps.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.MainApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isError: Boolean = false
)

class DashboardViewModel(
    val application: MainApplication
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    private val glucoseRepository = application.glucoseRepository
    private val treatmentRepository = application.treatmentRepository

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
            // Simulate heavy loading or actual data fetching
            delay(500)
            updateUiModel()
            _uiState.update { it.copy(isLoading = false, isError = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, isError = true) }
        }
    }

    private fun updateUiModel() {
        // Logic to update the dashboard based on repository data
    }

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return DashboardViewModel(application as MainApplication) as T
            }
        }
    }
}