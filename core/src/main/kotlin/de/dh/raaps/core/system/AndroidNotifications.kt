package de.dh.raaps.core.system

import android.app.Notification
import de.dh.raaps.core.aps.ApsRecommendation
import de.dh.raaps.core.aps.GlucoseSourceManager

interface AndroidNotifications {
    fun createNotificationChannels()
    fun showRecommendationNotification(recommendation: ApsRecommendation)
    fun cancelRecommendationNotification()

    fun createMainAppNotification(glucoseSourceManager: GlucoseSourceManager): Notification
    fun updateMainAppNotification(glucoseSourceManager: GlucoseSourceManager)

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1
    }
}