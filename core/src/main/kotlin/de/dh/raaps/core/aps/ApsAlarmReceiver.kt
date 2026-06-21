package de.dh.raaps.core.aps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver for system alarms to wake up the APS core.
 */
class ApsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        APS.handleWakeup(context, intent)
    }
}
