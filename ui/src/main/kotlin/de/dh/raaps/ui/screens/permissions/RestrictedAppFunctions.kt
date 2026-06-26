package de.dh.raaps.ui.screens.permissions

/**
 * Enumerates the permissions used by the app that are required for specific functions.
 */
enum class NeededPermission {
    /**
     * Allows scheduling exact alarms.
     */
    SCHEDULE_EXACT_ALARMS,

    /**
     * Allows sending notifications.
     */
    POST_NOTIFICATIONS,

    /**
     * Allows displaying full-screen activities over the lock screen.
     */
    SHOW_FULLSCREEN_ACTIVITY,

    /**
     * Requests that the app be excluded from battery optimizations.
     */
    IGNORE_BATTERY_OPTIMIZATIONS,

    /**
     * Permission related to the automatic revocation of permissions.
     * If the user has "granted" this (i.e., exempted the app), the permissions will not be revoked.
     */
    MANAGE_AUTO_REVOKE,

    /**
     * Allows reading and writing calendar entries.
     */
    READ_WRITE_CALENDAR
}

/**
 * Defines a function of the app that depends on one or more permissions.
 *
 * @param neededPermissions A set of [NeededPermission] required for this function.
 * @param onPermissionsGranted A callback that is executed as soon as all required permissions have been granted.
 */
data class RestrictedAppFunction(
    val neededPermissions: Set<NeededPermission>,
    val onPermissionsGranted: () -> Unit
)

/**
 * Declares all functions of the app that depend on one or more permissions.
 */
class RestrictedAppFunctions {
    companion object {
        val Alarms = RestrictedAppFunction(
            neededPermissions = setOf(
                NeededPermission.SCHEDULE_EXACT_ALARMS,
                NeededPermission.POST_NOTIFICATIONS,
                NeededPermission.IGNORE_BATTERY_OPTIMIZATIONS
            ),
            onPermissionsGranted = { /* TODO: Implement action when alarm permissions have been granted */ }
        )

        val FullscreenAlarms = RestrictedAppFunction(
            neededPermissions = setOf(
                NeededPermission.SHOW_FULLSCREEN_ACTIVITY
            ),
            onPermissionsGranted = { /* TODO: Implement action when alarm permissions have been granted */ }
        )

        val WriteCalendar = RestrictedAppFunction(
            neededPermissions = setOf(NeededPermission.READ_WRITE_CALENDAR),
            onPermissionsGranted = { /* TODO: Implement action when calendar permissions have been granted */ }
        )

        val ManageAutoRevoke = RestrictedAppFunction(
            neededPermissions = setOf(NeededPermission.MANAGE_AUTO_REVOKE),
            onPermissionsGranted = { /* TODO: Implement action when auto-revoke permission has been granted */ }
        )

        fun getActiveAppFunctions(para1: Int, para2: Boolean): List<RestrictedAppFunction> {
            val allFunctions = listOf(
                Alarms,
                FullscreenAlarms,
                WriteCalendar,
                ManageAutoRevoke
            )

            // The following code to find active functions could be more formalized in future versions
            // or in apps with more fine-grained settings. Especially it would be nice if some custom
            // code around the functions would listen to the app settings and whatever data it needs
            // to decide if that function is active.
            val activeFunctions = allFunctions.filter { function ->
                when (function) {
                    WriteCalendar -> para1 > 5
                    FullscreenAlarms -> para2
                    else -> true
                }
            }
            return activeFunctions
        }
    }
}