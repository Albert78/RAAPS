package de.dh.raaps.ui.screens.systemcontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.aps.AlgorithmInsight
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SystemControlUiState(
    val algorithmInsights: List<AlgorithmInsight> = emptyList()
)

class SystemControlViewModel(
    private val systemRegistry: SystemRegistry
) : ViewModel() {
    private val algorithmInsightRepository = systemRegistry.algorithmInsightRepository

    val uiState: StateFlow<SystemControlUiState> = algorithmInsightRepository.observeInsights()
        .map { insights ->
            SystemControlUiState(algorithmInsights = insights)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SystemControlUiState()
        )

    companion object {
        class Factory(private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return SystemControlViewModel(registry) as T
            }
        }
    }
}
