package de.dh.raaps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.glucoseUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class GlobalViewModel(
    systemRegistry: SystemRegistry
) : ViewModel() {
    private val appPreferencesRepository = systemRegistry.appPreferencesRepository

    val glucoseUnit: StateFlow<GlucoseUnit> = appPreferencesRepository.cachedPreferences
        .map { it.glucoseUnit }
        .stateIn(
            scope = kotlinx.coroutines.MainScope(), // Use a suitable scope if needed, or viewModelScope
            started = SharingStarted.Eagerly,
            initialValue = GlucoseUnit.MG_DL
        )

    companion object {
        class Factory(private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return GlobalViewModel(registry) as T
            }
        }
    }
}
