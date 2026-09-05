package de.dh.raaps.core.system

import android.app.Notification
import de.dh.raaps.core.aps.ApsIssue
import de.dh.raaps.core.aps.ApsRecommendation
import de.dh.raaps.core.repository.GlucoseRepository

interface AndroidNotifications {
    fun createNotificationChannels()
    fun showRecommendationNotification(recommendation: ApsRecommendation)
    fun cancelRecommendationNotification()

    fun showApsIssueNotification(issue: ApsIssue)
    fun cancelApsIssueNotification()

    fun createMainAppNotification(glucoseRepository: GlucoseRepository): Notification
    fun updateMainAppNotification(glucoseRepository: GlucoseRepository)

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1
    }
}