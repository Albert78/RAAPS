package de.dh.raaps.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import de.dh.raaps.MainApplication
import de.dh.raaps.core.aps.APS
import de.dh.raaps.notifications.ApsNotificationData
import de.dh.raaps.notifications.ApsNotificationManager

/**
 * Foreground service for the RAAPS system. Makes the RAAPS process remain active with a high priority.
 */
class ApsService : Service() {
    private val notificationManager: ApsNotificationManager = MainApplication.instance.notificationManager

    val aps : APS = MainApplication.instance.aps

    override fun onCreate() {
        super.onCreate()

        notificationManager.createNotificationChannels()
        startServiceInForeground()

        MainApplication.instance.setServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startServiceInForeground()
        return START_STICKY
    }

    private fun startServiceInForeground() {
        val apsNotificationData = ApsNotificationData.create(aps)
        val notification: Notification = notificationManager.createForegroundServiceNotification(apsNotificationData)

        startForeground(
            ApsNotificationManager.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        MainApplication.instance.setServiceRunning(false)
        super.onDestroy()
    }
}