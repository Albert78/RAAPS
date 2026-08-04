package de.dh.raaps.core.system

import android.app.Notification
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TickPriority
import de.dh.raaps.common.model.data.TimeService
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
) : TickHandler {
    private lateinit var glucoseSourceManager: GlucoseSourceManager
    private lateinit var therapyManager: TherapyManager

    fun startInitialization(
        scope: CoroutineScope,
        glucoseSourceManager: GlucoseSourceManager,
        therapyManager: TherapyManager,
        timeService: TimeService
    ) {
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

        timeService.registerTickHandler(TickPriority.UI, this)
    }

    override suspend fun onTick(tick: Tick) {
        androidNotifications.updateMainAppNotification(glucoseSourceManager)
    }

    fun createForegroundServiceNotification(): Notification {
        return androidNotifications.createMainAppNotification(glucoseSourceManager)
    }
}