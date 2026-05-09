package de.dh.raaps.services

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import de.dh.raaps.common.model.ToDo

/**
 * This receiver is called when the device is rebooted.
 * We disable this receiver by default and enable it as soon as there is a pending alarm available.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ToDo.toBeImplemented("Boot completed actions")
        }
    }

    companion object {
        fun enableBootReceiver(context: Context) {
            val receiver = ComponentName(context, BootReceiver::class.java)
            val pm = context.packageManager

            pm.setComponentEnabledSetting(
                receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        fun disableBootReceiver(context: Context) {
            val receiver = ComponentName(context, BootReceiver::class.java)
            val pm = context.packageManager

            pm.setComponentEnabledSetting(
                receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}