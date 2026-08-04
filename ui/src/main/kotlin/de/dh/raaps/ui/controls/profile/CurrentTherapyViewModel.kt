package de.dh.raaps.ui.controls.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Minutes
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
    val target: BgValue,
    val lowThreshold: BgValue,
    val adjustmentPercentage: Int,
    val dia: Minutes,
    val peak: Minutes
) {
    companion object {
        fun empty() = ProfileUiState(
            name = "",
            activeProfileId = null,
            isf = BgDelta(0),
            ic = 0.0,
            basal = 0.0,
            target = BgValue.fromMgDl(0),
            lowThreshold = BgValue.fromMgDl(0),
            adjustmentPercentage = 0,
            dia = Minutes(0),
            peak = Minutes(0)
        )
    }
}

data class CurrentTherapyUiState(
    val isLoading: Boolean = true,
    val activeProfile: ProfileUiState = ProfileUiState.empty(),
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL,
    val availableProfiles: List<InsulinProfile> = emptyList(),
    val defaultBgBlocks: List<BgBlock> = emptyList()
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
            therapyManager.observeAllInsulinProfiles()
        ) { currentSettings, profiles ->
            updateState(currentSettings, profiles)
        }.launchIn(viewModelScope)
    }

    private suspend fun updateState(currentSettings: CurrentTherapySettings, profiles: List<InsulinProfile>) {
        val now = Timestamp.now()
        val isf = therapyManager.getIsfFactor(now)
        val ic = therapyManager.getIcFactor(now)
        val basal = therapyManager.getBasalPerHour(now)
        val bgSettings = therapyManager.getBgSettings()

        val activeProfileName = currentSettings.insulinProfile.id.let { pid ->
            profiles.find { it.id == pid }?.name
        } ?: "Manual Override"
        val profileUiState = ProfileUiState(
            name = activeProfileName,
            activeProfileId = currentSettings.insulinProfile.id,
            isf = isf,
            ic = ic,
            basal = basal,
            target = bgSettings.first,
            lowThreshold = bgSettings.second,
            adjustmentPercentage = currentSettings.adjustmentPercentage,
            dia = currentSettings.insulinProfile.dia,
            peak = currentSettings.insulinProfile.peak
        )

        _uiState.update {
            it.copy(
                isLoading = false,
                activeProfile = profileUiState,
                availableProfiles = profiles,
                defaultBgBlocks = currentSettings.defaultBgBlocks
            )
        }
    }

    fun updateDefaultBgBlocks(blocks: List<BgBlock>) {
        viewModelScope.launch {
            therapyManager.updateDefaultBgBlocks(blocks)
        }
    }

    fun selectInsulinProfile(profile: InsulinProfile) {
        viewModelScope.launch {
            therapyManager.selectInsulinProfile(profile)
        }
    }

    fun setAdjustmentPercentage(percentage: Int) {
        viewModelScope.launch {
            therapyManager.setAdjustmentPercentage(percentage)
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