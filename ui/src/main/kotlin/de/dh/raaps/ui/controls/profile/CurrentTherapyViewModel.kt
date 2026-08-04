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
import de.dh.raaps.glucoseUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InsulinProfileUiState(
    val name: String,
    val activeProfileId: Long?,
    val currentIsf: BgDelta,
    val currentCr: Double,
    val currentBasal: Double,
    val isfRange: String,
    val crRange: String,
    val basalRange: String,
    val target: BgValue,
    val lowThreshold: BgValue,
    val insulinAdjustmentPercentage: Int,
    val targetBgOverride: BgValue?,
    val lowThresholdOverride: BgValue?,
    val adjustmentHint: String?,
    val dia: Minutes,
    val peak: Minutes
) {
    companion object {
        fun empty() = InsulinProfileUiState(
            name = "",
            activeProfileId = null,
            currentIsf = BgDelta(0),
            currentCr = 0.0,
            currentBasal = 0.0,
            isfRange = "",
            crRange = "",
            basalRange = "",
            target = BgValue.fromMgDl(0),
            lowThreshold = BgValue.fromMgDl(0),
            insulinAdjustmentPercentage = 0,
            targetBgOverride = null,
            lowThresholdOverride = null,
            adjustmentHint = null,
            dia = Minutes(0),
            peak = Minutes(0)
        )
    }
}

data class TherapyAdjustment(
    val name: String,
    val percentage: Int = 0,
    val targetBgMgDl: Short? = null,
    val lowThresholdMgDl: Short? = null
)

data class CurrentTherapyUiState(
    val isLoading: Boolean = true,
    val activeProfile: InsulinProfileUiState = InsulinProfileUiState.empty(),
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL,
    val availableProfiles: List<InsulinProfile> = emptyList(),
    val defaultBgBlocks: List<BgBlock> = emptyList(),
    val therapyAdjustmentPresets: List<TherapyAdjustment> = emptyList()
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

    // Hardcoded presets for now.
    // TODO: Make these user-editable in the future (e.g. via a database table or preferences).
    private val hardcodedPresets = listOf(
        TherapyAdjustment("Neutral"),
        TherapyAdjustment("Fahrrad fahren", percentage = -30, targetBgMgDl = 150, lowThresholdMgDl = 100),
        TherapyAdjustment("Klettern", percentage = -40, targetBgMgDl = 160, lowThresholdMgDl = 110),
        TherapyAdjustment("Alkohol", percentage = -15, targetBgMgDl = 120, lowThresholdMgDl = 80),
        TherapyAdjustment("Krank", percentage = 30, targetBgMgDl = 100, lowThresholdMgDl = 70),
        TherapyAdjustment("Stress", percentage = 20, targetBgMgDl = 115, lowThresholdMgDl = 75)
    )

    init {
        val glucoseUnitFlow = appPreferencesRepository.cachedPreferences.map { it.glucoseUnit }
        combine(
            therapyManager.currentTherapySettingsFlow,
            therapyManager.observeAllInsulinProfiles(),
            glucoseUnitFlow
        ) { currentSettings, profiles, unit ->
            updateState(currentSettings, profiles, unit)
        }.launchIn(viewModelScope)
    }

    private suspend fun updateState(currentSettings: CurrentTherapySettings, profiles: List<InsulinProfile>, unit: GlucoseUnit) {
        val now = Timestamp.now()
        val isf = therapyManager.getIsfFactor(now)
        val cr = therapyManager.getCrFactor(now)
        val basal = therapyManager.getBasalPerHour(now)
        val bgSettings = therapyManager.getBgSettings()

        val basalValues = currentSettings.insulinProfile.basalBlocks.map { it.amount }
        val crValues = currentSettings.insulinProfile.crBlocks.map { it.amount }
        val isfValues = currentSettings.insulinProfile.isfBlocks.map { it.amount }

        val activeProfileName = currentSettings.insulinProfile.id.let { pid ->
            profiles.find { it.id == pid }?.name
        } ?: "Manual Override"
        val profileUiState = InsulinProfileUiState(
            name = activeProfileName,
            activeProfileId = currentSettings.insulinProfile.id,
            currentIsf = isf,
            currentCr = cr,
            currentBasal = basal,
            isfRange = formatIsfRange(isfValues, unit),
            crRange = formatRange(crValues, "%.1f"),
            basalRange = formatRange(basalValues, "%.2f"),
            target = bgSettings.first,
            lowThreshold = bgSettings.second,
            insulinAdjustmentPercentage = currentSettings.insulinAdjustmentPercentage,
            targetBgOverride = currentSettings.targetBgOverride,
            lowThresholdOverride = currentSettings.lowThresholdOverride,
            adjustmentHint = currentSettings.adjustmentHint,
            dia = currentSettings.insulinProfile.dia,
            peak = currentSettings.insulinProfile.peak
        )

        _uiState.update {
            it.copy(
                isLoading = false,
                activeProfile = profileUiState,
                glucoseUnit = unit,
                availableProfiles = profiles,
                defaultBgBlocks = currentSettings.defaultBgBlocks,
                therapyAdjustmentPresets = hardcodedPresets
            )
        }
    }

    private fun formatRange(values: List<Double>, format: String): String {
        if (values.isEmpty()) return "-"
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        return if (min == max) {
            String.format(java.util.Locale.getDefault(), format, min)
        } else {
            val fMin = String.format(java.util.Locale.getDefault(), format, min)
            val fMax = String.format(java.util.Locale.getDefault(), format, max)
            "$fMin – $fMax"
        }
    }

    private fun formatIsfRange(values: List<Double>, unit: GlucoseUnit): String {
        if (values.isEmpty()) return "-"
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0

        fun formatVal(v: Double): String {
            return BgDelta(v.toInt().toShort()).toString(unit)
        }

        return if (min == max) {
            formatVal(min)
        } else {
            "${formatVal(min)} – ${formatVal(max)}"
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

    fun setTherapyAdjustment(percentage: Int, targetBg: BgValue?, lowThreshold: BgValue?, adjustmentHint: String?) {
        viewModelScope.launch {
            therapyManager.setTherapyAdjustment(percentage, targetBg, lowThreshold, adjustmentHint)
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