package de.dh.raaps.ui.screens.permissions

import android.content.res.Resources
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.DisplayTextUtils

sealed class PermissionStatus {
    /**
     * The permission is needed and granted for this app.
     */
    object Granted : PermissionStatus()

    /**
     * The permission is currently not necessary for this app (e.g. calendar sync not configured).
     */
    object NotNeeded : PermissionStatus()

    /**
     * The permission is necessary but not granted for this app.
     */
    object Denied : PermissionStatus()

    /**
     * Returns if the state of this permission is ok, i.e. granted or not needed.
     */
    fun isSatisfied(): Boolean = this is Granted || this is NotNeeded

    companion object {
        fun create(
            isGranted: Boolean,
            isNeeded: Boolean
        ): PermissionStatus = when {
            isGranted -> Granted
            isNeeded -> NotNeeded
            else -> Denied
        }
    }
}

data class PermissionsUiModel(
    val isLoading: Boolean,
    val notificationPermissionStatus: PermissionStatus,
    val ignoreBatteryOptimizationPermissionStatus: PermissionStatus,
    val autoRevokePermissionsPermissionStatus: PermissionStatus,
    val numPermissionsMissing: Int,
    val permissionsMissingText: String
) {
    val isPermissionsConfigComplete: Boolean = numPermissionsMissing == 0

    companion object {
        fun create(
            notificationPermissionStatus: PermissionStatus,
            ignoreBatteryOptimizationPermissionStatus: PermissionStatus,
            autoRevokePermissionsPermissionStatus: PermissionStatus,
            resources: Resources
        ): PermissionsUiModel {
            val numMissing = getNumPermissionsMissing(
                notificationPermissionStatus,
                ignoreBatteryOptimizationPermissionStatus,
                autoRevokePermissionsPermissionStatus
            )

            val permissionsText = DisplayTextUtils.getQuantityStringZero(
                resources,
                R.plurals.permissions_activity_start_permissions_missing,
                R.string.permissions_activity_start_all_permissions_granted,
                numMissing,
                numMissing
            )

            return PermissionsUiModel(
                isLoading = false,
                notificationPermissionStatus = notificationPermissionStatus,
                ignoreBatteryOptimizationPermissionStatus = ignoreBatteryOptimizationPermissionStatus,
                autoRevokePermissionsPermissionStatus = autoRevokePermissionsPermissionStatus,
                numPermissionsMissing = numMissing,
                permissionsMissingText = permissionsText
            )
        }

        fun loading(): PermissionsUiModel {
            return PermissionsUiModel(
                isLoading = true,
                notificationPermissionStatus = PermissionStatus.NotNeeded,
                ignoreBatteryOptimizationPermissionStatus = PermissionStatus.NotNeeded,
                autoRevokePermissionsPermissionStatus = PermissionStatus.NotNeeded,
                numPermissionsMissing = 0,
                permissionsMissingText = ""
            )
        }

        fun allMissing(
            resources: Resources
        ): PermissionsUiModel {
            return create(
                notificationPermissionStatus = PermissionStatus.Denied,
                ignoreBatteryOptimizationPermissionStatus = PermissionStatus.Denied,
                autoRevokePermissionsPermissionStatus = PermissionStatus.Denied,
                resources = resources
            )
        }

        fun allGranted(
            resources: Resources
        ): PermissionsUiModel {
            return create(
                notificationPermissionStatus = PermissionStatus.Granted,
                ignoreBatteryOptimizationPermissionStatus = PermissionStatus.Granted,
                autoRevokePermissionsPermissionStatus = PermissionStatus.Granted,
                resources = resources
            )
        }

        private fun getNumPermissionsMissing(
            notificationPermissionStatus: PermissionStatus,
            ignoreBatteryOptimizationPermissionStatus: PermissionStatus,
            autoRevokePermissionsPermissionStatus: PermissionStatus
        ): Int {
            val permissions = listOf(
                notificationPermissionStatus,
                ignoreBatteryOptimizationPermissionStatus,
                autoRevokePermissionsPermissionStatus
            )
            return permissions.count { !it.isSatisfied() }
        }
    }
}
