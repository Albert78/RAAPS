package de.dh.raaps.ui.controls.profile

import android.util.Range
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.RAAPSRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String,
    val activeProfileId: Long?,
    val isf: BgDelta,
    val ic: Double,
    val basal: Double,
    val target: Range<BgValue>
)

data class CurrentTherapyUiState(
    val isLoading: Boolean = false,
    val activeProfile: ProfileUiState? = null,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL,
    val availableProfiles: List<Profile> = emptyList()
)

/**
 * ViewModel for viewing and selecting the current therapy settings.
 */
class CurrentTherapyViewModel(
    private val raapsRegistry: RAAPSRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(CurrentTherapyUiState())
    val uiState: StateFlow<CurrentTherapyUiState> = _uiState

    private val therapyManager = raapsRegistry.therapyManager
    private val appPreferencesRepository = raapsRegistry.appPreferencesRepository

    init {
        combine(
            therapyManager.currentTherapySettingsFlow,
            therapyManager.observeAllProfiles()
        ) { currentSettings, profiles ->
            updateState(currentSettings, profiles)
        }.launchIn(viewModelScope)
    }

    private suspend fun updateState(currentSettings: CurrentTherapySettings?, profiles: List<Profile>) {
        val now = Timestamp.now()
        val isf = therapyManager.getIsfFactor(now)
        val ic = therapyManager.getIcFactor(now)
        val basal = therapyManager.getBasalPerHour(now)
        val target = therapyManager.getTarget()

        val profileUiState = if (currentSettings != null) {
            val activeProfileName = currentSettings.profile.id.let { pid ->
                profiles.find { it.id == pid }?.name
            } ?: "Manual Override"
            ProfileUiState(
                name = activeProfileName,
                activeProfileId = currentSettings.profile.id,
                isf = isf,
                ic = ic,
                basal = basal,
                target = target
            )
        } else null

        _uiState.update {
            it.copy(
                isLoading = false,
                activeProfile = profileUiState,
                availableProfiles = profiles
            )
        }
    }

    fun selectProfile(profile: Profile) {
        viewModelScope.launch {
            therapyManager.selectProfile(profile)
        }
    }

    companion object {
        class Factory(private val registry: RAAPSRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return CurrentTherapyViewModel(registry) as T
            }
        }
    }
}