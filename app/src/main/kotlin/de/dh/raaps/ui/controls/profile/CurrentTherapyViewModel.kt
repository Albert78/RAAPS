package de.dh.raaps.ui.controls.profile

import android.app.Application
import android.util.Range
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.MainApplication
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.CurrentTherapyData
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CurrentTherapyUiState(
    val isLoading: Boolean = false,
    val profileName: String = "",
    val activeProfileId: Long? = null,
    val currentIsf: BgDelta? = null,
    val currentIc: Double? = null,
    val currentTarget: Range<BgValue>? = null,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL,
    val availableProfiles: List<Profile> = emptyList()
)

class CurrentTherapyViewModel(
    application: MainApplication
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CurrentTherapyUiState())
    val uiState: StateFlow<CurrentTherapyUiState> = _uiState

    private val therapyManager = application.aps.therapyManager
    private val appPreferencesRepository = application.appPreferencesRepository

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val currentData = therapyManager.getActiveTherapyData()
            val profiles = therapyManager.getAllProfiles()
            updateState(currentData, profiles)
        }
    }

    private suspend fun updateState(currentData: CurrentTherapyData?, profiles: List<Profile>) {
        val now = Timestamp.now()
        val isf = therapyManager.getIsfFactor(now)
        val ic = therapyManager.getIcFactor(now)
        val target = therapyManager.getTarget()

        val activeProfileName = currentData?.profileId?.let { pid ->
            profiles.find { it.id == pid }?.name
        } ?: "Manual Override"

        _uiState.update {
            it.copy(
                isLoading = false,
                profileName = activeProfileName,
                activeProfileId = currentData?.profileId,
                currentIsf = isf,
                currentIc = ic,
                currentTarget = target,
                availableProfiles = profiles
            )
        }
    }

    fun selectProfile(profile: Profile) {
        viewModelScope.launch {
            therapyManager.selectProfile(profile)
            loadData()
        }
    }

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return CurrentTherapyViewModel(application as MainApplication) as T
            }
        }
    }
}