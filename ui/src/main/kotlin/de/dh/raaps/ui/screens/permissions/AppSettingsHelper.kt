package de.dh.raaps.ui.screens.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.dh.raaps.ui.R

fun canScheduleExactAlarms(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService<AlarmManager>()
        alarmManager?.canScheduleExactAlarms() == true
    } else {
        // Prior to Android 12 (S), no explicit permission was necessary
        true
    }
}

/**
 * Opens the special screen for "Alarms & Reminders" permission on Android 12+.
 */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }
}

fun canPostNotifications(context: Context): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Checks whether the app is configured to let the system automatically revoke the app permissions.
 */
fun isAutoRevokePermissions(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val pm = context.packageManager
        return !pm.isAutoRevokeWhitelisted
    }
    return false
}

/**
 * Opens the app settings screen of the current app in a new activity.
 */
fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, R.string.error_app_settings_could_not_be_opened_message, Toast.LENGTH_LONG).show()
    }
}

fun canShowFullscreenActivity(context: Context): Boolean {
    val sysManager = context.getSystemService<NotificationManager>()
    return sysManager?.canUseFullScreenIntent() ?: false
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService<PowerManager>()
    return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
}

/**
 * Opens the general app battery optimization settings screen in a new activity.
 * That screen shows a list of all apps where the user must first choose the current app to exclude from battery optimization.
 * See [requestIgnoreBatteryOptimizations], which is more user-friendly but requires a special permission.
 */
fun openBatteryOptimizationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, R.string.error_battery_optimization_settings_could_not_be_opened_message, Toast.LENGTH_LONG).show()
    }
}

/**
 * Prompts the user directly via a system dialog to exclude the app from battery optimization.
 * Requires: <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
 */
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fall back to the general list, in case the special dialog is blocked
            openBatteryOptimizationSettings(context)
        }
    }
}

/**
 * Opens the app auto revoke settings screen in a new activity.
 */
fun openAutoRevokeSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            // Fallback: App Settings
            openAppSettings(context)
        }
    } catch (e: Exception) {
        Toast.makeText(context, R.string.error_app_settings_could_not_be_opened_message, Toast.LENGTH_LONG).show()
    }
}

fun openNotificationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, R.string.error_app_settings_could_not_be_opened_message, Toast.LENGTH_LONG).show()
    }
}

fun canReadWriteCalendar(context: Context): Boolean {
    // We need both permissions
    val hasReadPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    val hasWritePermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    return hasReadPermission && hasWritePermission
}

/**
 * Use this to check if it is necessary to open permissions.
 */
fun isPermissionsMissing(context: Context): Boolean {
    return !canPostNotifications(context) ||
            isAutoRevokePermissions(context)
}