package de.dh.raaps.core.system

import android.app.Notification
import de.dh.raaps.core.aps.APS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * High-Level notification manager for all kinds of APS user notifications.
 * TODO: Create user notifications, depending on APS mode
 */
class NotificationManager(
    val androidNotifications: AndroidNotifications
) {
    private lateinit var aps: APS

    fun startInitialization(scope: CoroutineScope, aps: APS) {
        this.aps = aps
        androidNotifications.createNotificationChannels()

        scope.launch {
            aps.recommendations.collect { recommendations ->
                if (recommendations.isEmpty()) {
                    androidNotifications.cancelRecommendationNotification()
                } else {
                    androidNotifications.showRecommendationNotification(recommendations.first())
                }
            }
        }

        scope.launch {
            aps.lastDataTime.collect { _ ->
                androidNotifications.updateMainAppNotification(aps)
            }
        }
    }

    fun createForegroundServiceNotification(): Notification {
        return androidNotifications.createMainAppNotification(aps)
    }
}