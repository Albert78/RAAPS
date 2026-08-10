package de.dh.raaps.ui.screens.systemcontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.aps.AlgorithmInsight
import de.dh.raaps.glucoseUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SystemControlUiState(
    val algorithmInsights: List<AlgorithmInsight> = emptyList(),
    val glucoseSourceName: String? = null,
    val sensorTypeName: String? = null,
    val readingsInterval: BgReadingsInterval? = null,
    val lastBgReading: BgReading? = null,
    val nextPredictedTimestamp: Timestamp? = null,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL
)

class SystemControlViewModel(
    private val systemRegistry: SystemRegistry
) : ViewModel() {
    private val algorithmInsightRepository = systemRegistry.algorithmInsightRepository
    private val glucoseSourceManager = systemRegistry.glucoseSourceManager
    private val appPreferencesRepository = systemRegistry.appPreferencesRepository

    val uiState: StateFlow<SystemControlUiState> = combine(
        algorithmInsightRepository.observeInsights(),
        glucoseSourceManager.activeGlucoseSource,
        glucoseSourceManager.currentBg,
        appPreferencesRepository.cachedPreferences
    ) { insights, source, currentBg, preferences ->
        SystemControlUiState(
            algorithmInsights = insights,
            glucoseSourceName = source?.name,
            sensorTypeName = source?.getSensorTypeName(),
            readingsInterval = source?.readingsInterval,
            lastBgReading = currentBg,
            nextPredictedTimestamp = if (source != null) glucoseSourceManager.predictNextValueTimestamp() else null,
            glucoseUnit = preferences.glucoseUnit
        )
    }.stateIn(
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
