package de.dh.raaps.ui.controls.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean,
    val isError: Boolean,
    val readings: List<BgReading> = listOf(),
    val insulinApplications: List<InsulinApplication> = listOf(),
    val meals: List<MealEntry> = listOf()
)

/**
 * ViewModel for blood glucose history.
 */
class HistoryViewModel(
    private val systemRegistry: SystemRegistry
) : ViewModel() {
    private val _historyUiState = MutableStateFlow(HistoryUiState(isLoading = true, isError = false))
    val historyUiState = _historyUiState.asStateFlow()

    private val glucoseRepository = systemRegistry.glucoseRepository
    private val treatmentRepository = systemRegistry.treatmentRepository

    init {
        viewModelScope.launch {
            combine(
                glucoseRepository.observeBgReadings(),
                treatmentRepository.observeInsulinApplications(),
                treatmentRepository.observeMeals()
            ) { args ->
                val readings = args[0] as List<BgReading>
                val insulin = args[1] as List<InsulinApplication>
                val meals = args[2] as List<MealEntry>

                val historyLimit = Timestamp.now().minusHours(25)
                val filteredReadings = readings.filter { it.timestamp >= historyLimit }
                val filteredInsulin = insulin.filter { it.timestamp >= historyLimit }
                val filteredMeals = meals.filter { it.timestamp >= historyLimit }

                Log.d(TAG, "Updating history data with ${filteredReadings.size} readings, ${filteredInsulin.size} insulin apps, ${filteredMeals.size} meals")
                _historyUiState.update {
                    HistoryUiState(
                        isLoading = false,
                        isError = false,
                        readings = filteredReadings,
                        insulinApplications = filteredInsulin,
                        meals = filteredMeals
                    )
                }
            }.collect { }
        }
    }

    companion object {
        val TAG = HistoryViewModel::class.simpleName

        class Factory(
            private val registry: SystemRegistry,
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return HistoryViewModel(registry) as T
            }
        }
    }
}