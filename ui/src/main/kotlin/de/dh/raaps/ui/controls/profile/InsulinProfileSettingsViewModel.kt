package de.dh.raaps.ui.controls.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.core.RAAPSRegistry
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.InsulinProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InsulinProfileSettingsUiState(
    val profiles: List<InsulinProfile> = emptyList(),
    val insulinTypes: List<InsulinType> = emptyList(),
    val isLoading: Boolean = false,
    val editingProfile: InsulinProfile? = null,
    val showDeleteConfirmation: InsulinProfile? = null
)

/**
 * ViewModel for managing therapy profiles (CRUD operations).
 */
class InsulinProfileSettingsViewModel(
    private val raapsRegistry: RAAPSRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsulinProfileSettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val therapyRepository = raapsRegistry.therapyRepository

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profiles = therapyRepository.getAllInsulinProfiles()
            val insulinTypes = therapyRepository.getAllInsulinTypes()
            _uiState.update { it.copy(profiles = profiles, insulinTypes = insulinTypes, isLoading = false) }
        }
    }

    fun loadInsulinProfiles() {
        loadData()
    }

    fun startEditing(profile: InsulinProfile?) {
        _uiState.update { it.copy(editingProfile = profile) }
    }

    fun copyInsulinProfile(profile: InsulinProfile, newName: String) {
        val copy = profile.copy(
            id = ID_UNDEFINED,
            name = newName
        )
        startEditing(copy)
    }

    fun isNameUnique(name: String, excludeId: Long): Boolean {
        val trimmedName = name.trim()
        return _uiState.value.profiles.none { 
            it.name.trim().equals(trimmedName, ignoreCase = true) && it.id != excludeId 
        }
    }

    fun stopEditing() {
        _uiState.update { it.copy(editingProfile = null) }
    }

    fun saveInsulinProfile(profile: InsulinProfile) {
        viewModelScope.launch {
            if (profile.id == ID_UNDEFINED) {
                therapyRepository.insertInsulinProfile(profile)
            } else {
                therapyRepository.updateInsulinProfile(profile)
            }
            loadInsulinProfiles()
            stopEditing()
        }
    }

    fun confirmDelete(profile: InsulinProfile) {
        _uiState.update { it.copy(showDeleteConfirmation = profile) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirmation = null) }
    }

    fun deleteInsulinProfile(profile: InsulinProfile) {
        viewModelScope.launch {
            therapyRepository.deleteInsulinProfile(profile)
            loadInsulinProfiles()
            cancelDelete()
        }
    }

    companion object {
        class Factory(private val registry: RAAPSRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return InsulinProfileSettingsViewModel(registry) as T
            }
        }
    }
}