package de.dh.raaps.ui.screens.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.core.SystemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * This view model is used to show the state of needed and granted permissions to the user.
 * It can be used from multiple activities, since the information about granted permissions is needed
 * on several places in the app (permissions screen, notification messages about missing permissions
 * in other screens).
 * This view model isn't implemented in the typical layer architecture (Repository - ViewModel - View),
 * since permission management (querying and requesting permissions, observing app permissions) is a
 * subject of the UI in Android. We request the current app permissions status in method
 * [updateAppPermissions] but we need the UI to call this method when permissions have changed.
 */
class PermissionsViewModel(
    private val systemRegistry: SystemRegistry
) : ViewModel() {
    private val appStateRepository = systemRegistry.appPreferencesRepository

    private val _uiState = MutableStateFlow(PermissionsUiModel.loading())
    val uiState = _uiState.asStateFlow()

    private var functionSwitch1: Int = 5
    private var functionSwitch2: Boolean = false

    init {
        observeAppSettings()
    }

    private fun observeAppSettings() {
        appStateRepository.cachedPreferences
            .onEach { preferences ->
                // Fill functionSwitches
                functionSwitch1 = 9
                updateAppPermissions()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Updates the internal state of the system permissions with the current system settings for this app.
     */
    fun updateAppPermissions() {
        val appContext = systemRegistry.appContext
        val canPost = canPostNotifications(appContext)
        val isIgnoring = isIgnoringBatteryOptimizations(appContext)
        val isAutoRevoke = isAutoRevokePermissions(appContext)

        val activeFunctions = RestrictedAppFunctions.Companion.getActiveAppFunctions(functionSwitch1, functionSwitch2)

        val allNeededPermissions = activeFunctions.flatMap { it.neededPermissions }.toSet()

        fun getStatus(permission: NeededPermission, isGranted: Boolean): PermissionStatus {
            return if (allNeededPermissions.contains(permission)) {
                if (isGranted) PermissionStatus.Granted else PermissionStatus.Denied
            } else {
                PermissionStatus.NotNeeded
            }
        }

        val notificationPermissionStatus = getStatus(NeededPermission.POST_NOTIFICATIONS, canPost)
        val ignoreBatteryOptimizationPermissionStatus = getStatus(NeededPermission.IGNORE_BATTERY_OPTIMIZATIONS, isIgnoring)
        // For auto-revoke, the permission status is "granted" if the app is exempted, which means isAutoRevokePermissions() is false.
        val autoRevokePermissionsPermissionStatus = getStatus(NeededPermission.MANAGE_AUTO_REVOKE, !isAutoRevoke)

        updateUiModel(
            notificationPermissionStatus = notificationPermissionStatus,
            ignoreBatteryOptimizationPermissionStatus = ignoreBatteryOptimizationPermissionStatus,
            autoRevokePermissionsPermissionStatus = autoRevokePermissionsPermissionStatus
        )
    }

    private fun updateUiModel(
        notificationPermissionStatus: PermissionStatus,
        ignoreBatteryOptimizationPermissionStatus: PermissionStatus,
        autoRevokePermissionsPermissionStatus: PermissionStatus
    ) {
        _uiState.update {
            PermissionsUiModel.create(
                notificationPermissionStatus,
                ignoreBatteryOptimizationPermissionStatus,
                autoRevokePermissionsPermissionStatus,
                resources = systemRegistry.appContext.resources
            )
        }
    }

    companion object {
        class Factory(private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return PermissionsViewModel(registry) as T
            }
        }
    }
}