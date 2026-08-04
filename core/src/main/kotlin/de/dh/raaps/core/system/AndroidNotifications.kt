package de.dh.raaps.core.system

import android.app.Notification
import de.dh.raaps.core.aps.APS
import de.dh.raaps.core.aps.ApsRecommendation

interface AndroidNotifications {
    fun createNotificationChannels()
    fun showRecommendationNotification(recommendation: ApsRecommendation)
    fun cancelRecommendationNotification()

    fun createMainAppNotification(aps: APS): Notification
    fun updateMainAppNotification(aps: APS)

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1
    }
}