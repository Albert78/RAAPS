package de.dh.raaps.ui.controls.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.MainApplication
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileSettingsUiState(
    val profiles: List<Profile> = emptyList(),
    val isLoading: Boolean = false,
    val editingProfile: Profile? = null,
    val showDeleteConfirmation: Profile? = null
)

class ProfileSettingsViewModel(
    val application: MainApplication
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileSettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val dataRepository = application.dataRepository

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profiles = dataRepository.getAllProfiles()
            _uiState.update { it.copy(profiles = profiles, isLoading = false) }
        }
    }

    fun startEditing(profile: Profile?) {
        _uiState.update { it.copy(editingProfile = profile) }
    }

    fun stopEditing() {
        _uiState.update { it.copy(editingProfile = null) }
    }

    fun saveProfile(profile: Profile) {
        viewModelScope.launch {
            if (profile.id == ID_UNDEFINED) {
                dataRepository.insertProfile(profile)
            } else {
                dataRepository.updateProfile(profile)
            }
            loadProfiles()
            stopEditing()
        }
    }

    fun confirmDelete(profile: Profile) {
        _uiState.update { it.copy(showDeleteConfirmation = profile) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirmation = null) }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            dataRepository.deleteProfile(profile)
            loadProfiles()
            cancelDelete()
        }
    }

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return ProfileSettingsViewModel(application as MainApplication) as T
            }
        }
    }
}