package de.dh.raaps.plugin.simbody

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver for the SimBody heartbeat alarm.
 */
class SimBodyWakeupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SimBodyHeartbeat.ACTION_TICK) {
            Log.d("SimBodyWakeupReceiver", "Tick received, waking up simulation")
            SimBodyHeartbeat.onAlarmReceived()
        }
    }
}
