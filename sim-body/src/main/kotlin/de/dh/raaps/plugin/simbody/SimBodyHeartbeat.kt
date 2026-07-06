package de.dh.raaps.plugin.simbody

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import de.dh.raaps.common.model.data.Timestamp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Handles the periodic wakeup of the SimBody simulation.
 * Decouples the simulation triggers from the CGM source flow.
 */
object SimBodyHeartbeat {
    private const val TAG = "SimBodyHeartbeat"
    const val ACTION_TICK = "de.dh.raaps.plugin.simbody.ACTION_TICK"
    private const val REQUEST_CODE = 555

    private val _ticks = MutableSharedFlow<Timestamp>(extraBufferCapacity = 1)
    val ticks: SharedFlow<Timestamp> = _ticks.asSharedFlow()

    private var activeBodyModel: BodyModel? = null
    private var activePumpDevice: SimBodyPumpDevice? = null

    fun start(context: Context, bodyModel: BodyModel, pumpDevice: SimBodyPumpDevice) {
        activeBodyModel = bodyModel
        activePumpDevice = pumpDevice

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SimBodyWakeupReceiver::class.java).apply {
            action = ACTION_TICK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMs = 5 * 60 * 1000L
        // We use setRepeating here. Note: On Android 4.4+, this is inexact.
        // For simulation purposes, this is usually acceptable and better for battery.
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + intervalMs,
            intervalMs,
            pendingIntent
        )
        
        Log.d(TAG, "SimBody Heartbeat started (5 min interval)")
    }

    fun stop(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SimBodyWakeupReceiver::class.java).apply {
            action = ACTION_TICK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        activeBodyModel = null
        activePumpDevice = null
        Log.d(TAG, "SimBody Heartbeat stopped")
    }

    fun onAlarmReceived() {
        val now = Timestamp.now()
        // Perform the simulation tick
        activePumpDevice?.advanceToTick(now)
        activeBodyModel?.advanceToTick(now)
        
        // Notify observers (like SimBodyCgmSource)
        _ticks.tryEmit(now)
    }
}
