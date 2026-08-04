package de.dh.raaps.core.aps

import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.core.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages the application mode, specifically the [ApsMode].
 * Handles persistence and provides a reactive state for other components to observe.
 */
interface SystemManager {
    /**
     * The current APS mode.
     */
    val apsMode: StateFlow<ApsMode>

    /**
     * Updates the APS mode and persists the change.
     */
    fun setApsMode(mode: ApsMode)
}

/**
 * Implementation of [SystemManager].
 */
class SystemManagerImpl(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) : SystemManager {
    private val _apsMode = MutableStateFlow(ApsMode.Suspend)
    override val apsMode: StateFlow<ApsMode> = _apsMode.asStateFlow()

    init {
        scope.launch {
            settingsRepository.observeCurrentSettings().collect { settings ->
                if (settings != null) {
                    _apsMode.value = settings.apsMode
                }
            }
        }
    }

    override fun setApsMode(mode: ApsMode) {
        _apsMode.value = mode
        scope.launch {
            val currentSettings = settingsRepository.getCurrentSettings()
            if (currentSettings != null) {
                settingsRepository.updateCurrentSettings(currentSettings.copy(apsMode = mode))
            }
        }
    }
}