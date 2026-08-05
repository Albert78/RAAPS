package de.dh.raaps.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import de.dh.raaps.MainApplication
import de.dh.raaps.core.system.AndroidNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground service for the RAAPS system. Makes the RAAPS process remain active with a high priority.
 */
class ApsService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val systemManager = MainApplication.instance.registry.systemManager

    override fun onCreate() {
        super.onCreate()

        startServiceInForeground()

        MainApplication.instance.setServiceRunning(true)
    }

    private fun startServiceInForeground() {
        val notification: Notification = systemManager.createForegroundServiceNotification()

        startForeground(
            AndroidNotifications.FOREGROUND_NOTIFICATION_ID,
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