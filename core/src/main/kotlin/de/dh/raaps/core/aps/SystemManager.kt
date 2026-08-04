package de.dh.raaps.core.aps

import android.content.Intent
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.core.repository.SettingsRepository
import de.dh.raaps.core.system.SystemWakeService
import de.dh.raaps.core.system.WakeupHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ApsIssue {
    /**
     * No recent glucose value available, the loop cannot calculate new treatments.
     */
    StaleBG,

    /**
     * Any other issue that prevents the core from working.
     */
    Other
}

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
     * Active issues of the APS.
     */
    val apsIssues: StateFlow<Set<ApsIssue>>

    /**
     * Updates the APS mode and persists the change.
     */
    fun setApsMode(mode: ApsMode)
}

/**
 * Implementation of [SystemManager].
 */
class SystemManagerImpl(
    private val glucoseSourceManager: GlucoseSourceManager,
    private val wakeService: SystemWakeService,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) : SystemManager, WakeupHandler {
    private val _apsMode = MutableStateFlow(ApsMode.Suspend)
    override val apsMode: StateFlow<ApsMode> = _apsMode.asStateFlow()

    private val _apsIssues = MutableStateFlow<Set<ApsIssue>>(emptySet())
    override val apsIssues: StateFlow<Set<ApsIssue>> = _apsIssues.asStateFlow()

    init {
        wakeService.registerHandler(WAKE_TAG, this)

        scope.launch {
            settingsRepository.observeCurrentSettings().collect { settings ->
                if (settings != null) {
                    _apsMode.value = settings.apsMode
                }
            }
        }

        scope.launch {
            glucoseSourceManager.currentBg.collect { bg ->
                if (bg != null) {
                    // Schedule stale check for the next window
                    val nextCheck = glucoseSourceManager.nextBgStaleCheckAt()
                    wakeService.scheduleWakeup(WAKE_TAG, WAKEUP_STALE_CHECK, nextCheck)
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

    override fun onWakeup(wakeupId: UInt?, intent: Intent?) {
        if (wakeupId == WAKEUP_STALE_CHECK) {
            if (glucoseSourceManager.isBgStale()) {
                addIssue(ApsIssue.StaleBG)
            } else {
                removeIssue(ApsIssue.StaleBG)
            }
        }
    }

    private fun addIssue(issue: ApsIssue) {
        if (issue !in _apsIssues.value) {
            _apsIssues.value += issue
        }
    }

    private fun removeIssue(issue: ApsIssue) {
        if (issue in _apsIssues.value) {
            _apsIssues.value -= issue
        }
    }

    companion object {
        const val WAKE_TAG = "SystemManager"
        const val WAKEUP_STALE_CHECK = 0u
    }
}