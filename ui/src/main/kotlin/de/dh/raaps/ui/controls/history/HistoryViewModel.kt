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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

data class HistoryUiState(
    val isLoading: Boolean,
    val isError: Boolean,
    val readings: List<BgReading> = listOf(),
    val insulinApplications: List<InsulinApplication> = listOf(),
    val meals: List<MealEntry> = listOf()
)

private data class RawHistoryData(
    val readings: List<BgReading>,
    val insulinApplications: List<InsulinApplication>,
    val meals: List<MealEntry>
)

/**
 * ViewModel for blood glucose history.
 */
@OptIn(FlowPreview::class)
class HistoryViewModel(
    systemRegistry: SystemRegistry
) : ViewModel() {
    private val glucoseRepository = systemRegistry.glucoseRepository
    private val treatmentRepository = systemRegistry.treatmentRepository

    private var lastReadings: List<BgReading>? = null
    private var cachedFilteredReadings: List<BgReading> = emptyList()

    private var lastInsulin: List<InsulinApplication>? = null
    private var cachedFilteredInsulin: List<InsulinApplication> = emptyList()

    private var lastMeals: List<MealEntry>? = null
    private var cachedFilteredMeals: List<MealEntry> = emptyList()

    val historyUiState: StateFlow<HistoryUiState> = combine(
        glucoseRepository.observeBgReadings().distinctUntilChanged(),
        treatmentRepository.observeInsulinApplications().distinctUntilChanged(),
        treatmentRepository.observeMeals().distinctUntilChanged()
    ) { readings, insulin, meals ->
        RawHistoryData(readings, insulin, meals)
    }
    .debounce(300.milliseconds)
    .map { (readings, insulin, meals) ->
        val historyLimit = Timestamp.now().minusHours(25)

        val filteredReadings = if (readings === lastReadings) {
            cachedFilteredReadings
        } else {
            lastReadings = readings
            readings.filterSince(historyLimit) { it.timestamp }.also { cachedFilteredReadings = it }
        }

        val filteredInsulin = if (insulin === lastInsulin) {
            cachedFilteredInsulin
        } else {
            lastInsulin = insulin
            insulin.filterSince(historyLimit) { it.timestamp }.also { cachedFilteredInsulin = it }
        }

        val filteredMeals = if (meals === lastMeals) {
            cachedFilteredMeals
        } else {
            lastMeals = meals
            meals.filterSince(historyLimit) { it.timestamp }.also { cachedFilteredMeals = it }
        }

        Log.d(TAG, "Updating history data with ${filteredReadings.size} readings, ${filteredInsulin.size} insulin apps, ${filteredMeals.size} meals")

        HistoryUiState(
            isLoading = false,
            isError = false,
            readings = filteredReadings,
            insulinApplications = filteredInsulin,
            meals = filteredMeals
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true, isError = false)
    )

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

private inline fun <T> List<T>.filterSince(limit: Timestamp, crossinline getTimestamp: (T) -> Timestamp): List<T> {
    if (isEmpty()) return emptyList()
    val index = binarySearch { getTimestamp(it).compareTo(limit) }
    val startIndex = if (index >= 0) {
        var start = index
        while (start > 0 && getTimestamp(this[start - 1]) >= limit) {
            start--
        }
        start
    } else {
        -index - 1
    }
    return if (startIndex < size) subList(startIndex, size) else emptyList()
}