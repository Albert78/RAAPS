package de.dh.raaps.ui.screens.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.MainApplication
import de.dh.raaps.common.ui.ThemeMode
import de.dh.raaps.setThemeMode
import de.dh.raaps.themeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents the UI state for the preferences screen.
 */
data class PreferencesUiState(
    val isLoading: Boolean,
    val isError: Boolean,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

class PreferencesViewModel(
    private val application: MainApplication,
    private val appPreferencesRepository: AppPreferencesRepository = application.mAppPreferencesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreferencesUiState(isLoading = true, isError = false))
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            try {
                appPreferencesRepository.cachedPreferences.collect {
                    preferences -> updateUiModel(preferences)
                }
            } catch (_: Exception) {
                updateUiModel(null)
            }
        }
    }

    private fun updateUiModel(preferences: Preferences?) {
        if (preferences == null) {
            _uiState.update { PreferencesUiState(isLoading = true, isError = false) }
            return
        }
        val themeMode = preferences.themeMode

        _uiState.update { PreferencesUiState(
            isLoading = false,
            isError = false,
            themeMode = themeMode,
        ) }
    }

    /**
     * Updates the theme mode in the repository.
     * The application will react automatically to this change.
     */
    fun setThemeMode(newMode: ThemeMode) {
        viewModelScope.launch {
            appPreferencesRepository.setThemeMode(newMode)
        }
    }

    companion object {
        class Factory(
            private val application: MainApplication
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appStateRepository = MainApplication.instance.mAppPreferencesRepository
                return PreferencesViewModel(application, appPreferencesRepository = appStateRepository) as T
            }
        }
    }
}