package de.dh.raaps.core.system

import android.app.Notification
import de.dh.raaps.core.aps.GlucoseSourceManager
import de.dh.raaps.core.aps.TherapyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * High-Level notification manager for all kinds of APS user notifications.
 * TODO: Create user notifications, depending on APS mode
 */
class NotificationManager(
    val androidNotifications: AndroidNotifications
) {
    private lateinit var glucoseSourceManager: GlucoseSourceManager
    private lateinit var therapyManager: TherapyManager

    fun startInitialization(scope: CoroutineScope, glucoseSourceManager: GlucoseSourceManager, therapyManager: TherapyManager) {
        this.glucoseSourceManager = glucoseSourceManager
        this.therapyManager = therapyManager
        androidNotifications.createNotificationChannels()

        scope.launch {
            therapyManager.recommendations.collect { recommendations ->
                if (recommendations.isEmpty()) {
                    androidNotifications.cancelRecommendationNotification()
                } else {
                    androidNotifications.showRecommendationNotification(recommendations.first())
                }
            }
        }

        scope.launch {
            glucoseSourceManager.lastDataTime.collect { _ ->
                androidNotifications.updateMainAppNotification(glucoseSourceManager)
            }
        }
    }

    fun createForegroundServiceNotification(): Notification {
        return androidNotifications.createMainAppNotification(glucoseSourceManager)
    }
}