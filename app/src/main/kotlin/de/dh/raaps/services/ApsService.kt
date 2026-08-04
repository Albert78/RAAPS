package de.dh.raaps.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import de.dh.raaps.MainApplication
import de.dh.raaps.core.aps.APS
import de.dh.raaps.notifications.ApsMainNotificationData
import de.dh.raaps.notifications.ApsNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service for the RAAPS system. Makes the RAAPS process remain active with a high priority.
 */
class ApsService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val notificationManager: ApsNotificationManager = MainApplication.instance.notificationManager

    val aps : APS = MainApplication.instance.registry.aps

    override fun onCreate() {
        super.onCreate()

        notificationManager.createNotificationChannels()
        startServiceInForeground()

        MainApplication.instance.setServiceRunning(true)

        observeRecommendations()
    }

    private fun observeRecommendations() {
        serviceScope.launch {
            aps.recommendations.collect { recommendations ->
                if (recommendations.isEmpty()) {
                    notificationManager.cancelRecommendationNotification()
                } else {
                    // Show the first one for now, or could show multiple
                    notificationManager.showRecommendationNotification(recommendations.first())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startServiceInForeground()
        return START_STICKY
    }

    private fun startServiceInForeground() {
        val apsNotificationData = ApsMainNotificationData.create(aps)
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
        serviceScope.cancel()
        super.onDestroy()
    }
}