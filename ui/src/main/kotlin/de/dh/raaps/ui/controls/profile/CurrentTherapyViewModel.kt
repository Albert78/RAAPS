package de.dh.raaps.ui.controls.profile

import android.app.Application
import android.util.Range
import androidx.lifecycle.AndroidViewModel
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

data class CurrentTherapyUiState(
    val isLoading: Boolean = false,
    val profileName: String = "",
    val activeProfileId: Long? = null,
    val currentIsf: BgDelta? = null,
    val currentIc: Double? = null,
    val currentBasal: Double? = null,
    val currentTarget: Range<BgValue>? = null,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL,
    val availableProfiles: List<Profile> = emptyList()
)

class CurrentTherapyViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val raapsRegistry = RAAPSRegistry.instance
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

        val activeProfileName = currentSettings?.profile?.id?.let { pid ->
            profiles.find { it.id == pid }?.name
        } ?: "Manual Override"

        _uiState.update {
            it.copy(
                isLoading = false,
                profileName = activeProfileName,
                activeProfileId = currentSettings?.profile?.id,
                currentIsf = isf,
                currentIc = ic,
                currentBasal = basal,
                currentTarget = target,
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
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return CurrentTherapyViewModel(application) as T
            }
        }
    }
}