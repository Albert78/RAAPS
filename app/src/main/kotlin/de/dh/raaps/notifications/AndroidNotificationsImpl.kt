package de.dh.raaps.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import de.dh.raaps.R
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.core.aps.ApsIssue
import de.dh.raaps.core.aps.ApsRecommendation
import de.dh.raaps.core.aps.CoreIssue
import de.dh.raaps.core.aps.STALE_BG_THRESHOLD
import de.dh.raaps.core.pump.PumpIssue
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.system.AndroidNotifications
import de.dh.raaps.core.system.RegistryProvider
import de.dh.raaps.ui.activities.MainActivity
import de.dh.raaps.ui.screens.permissions.canPostNotifications
import de.dh.raaps.common.R as CommonR
import de.dh.raaps.ui.R as UiR

/**
 * Low-level notification manager for communicating with the Android system.
 */
class AndroidNotificationsImpl(
    val context: Context
): AndroidNotifications {
    private val manager = context.getSystemService<NotificationManager>()!!

    override fun createNotificationChannels() {
        // Channel for the foreground service (BG values)
        val serviceName = context.getString(UiR.string.aps_service_notification_channel_name)
        val serviceImportance = NotificationManager.IMPORTANCE_HIGH
        val serviceChannel = NotificationChannel(SERVICE_CHANNEL_ID, serviceName, serviceImportance)
        serviceChannel.setShowBadge(false)

        // Channel for recommendations (User interaction required)
        val recName = context.getString(UiR.string.recommendation_notification_channel_name)
        val recImportance = NotificationManager.IMPORTANCE_HIGH
        val recChannel = NotificationChannel(RECOMMENDATION_CHANNEL_ID, recName, recImportance)
        recChannel.enableVibration(true)
        recChannel.setShowBadge(true)

        // Channel for algorithm issues (Manual intervention required)
        val issueName = context.getString(UiR.string.algorithm_issue_notification_channel_name)
        val issueImportance = NotificationManager.IMPORTANCE_HIGH
        val issueChannel = NotificationChannel(ALGORITHM_ISSUE_CHANNEL_ID, issueName, issueImportance)
        issueChannel.enableVibration(true)
        issueChannel.setShowBadge(true)

        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(recChannel)
        notificationManager.createNotificationChannel(issueChannel)
    }

    private fun getGlucoseUnit(): GlucoseUnit {
        val registry = (context.applicationContext as? RegistryProvider)?.registry
        return registry?.appPreferencesRepository?.glucoseUnit?.value ?: GlucoseUnit.MG_DL
    }

    fun getBgValueString(sample: BgValue?, unit: GlucoseUnit, forceSign: Boolean): String? {
        if (sample == null) return null
        val valStr = sample.toString(unit)
        val unitStr = when (unit) {
            GlucoseUnit.MG_DL -> context.getString(CommonR.string.glucose_unit_mgdl)
            GlucoseUnit.MMOL -> context.getString(CommonR.string.glucose_unit_mmol)
        }
        return if (forceSign) {
            val sign = if (sample.mgdl > 0) "+" else ""
            "$sign$valStr $unitStr"
        } else {
            "$valStr $unitStr"
        }
    }

    fun getBgDeltaString(delta: BgValue?, unit: GlucoseUnit): String? {
        if (delta == null) return null
        val deltaValue = BgDelta.fromMgDl(delta.mgdl)
        val valStr = deltaValue.toDiff(unit)
        val unitStr = when (unit) {
            GlucoseUnit.MG_DL -> context.getString(CommonR.string.glucose_unit_mgdl)
            GlucoseUnit.MMOL -> context.getString(CommonR.string.glucose_unit_mmol)
        }
        return context.getString(UiR.string.bg_delta_format, valStr, unitStr)
    }

    override fun createMainAppNotification(glucoseRepository: GlucoseRepository): Notification {
        val data = MainAppNotificationData.create(glucoseRepository)
        Log.d(TAG, "Build notification for ${data.lastBgSample}")
        val unit = getGlucoseUnit()
        val bgValueStr = getBgValueString(data.lastBgSample?.value, unit, false)
        val title = bgValueStr ?: context.getString(UiR.string.aps_service_notification_content_no_value_yet)
        val details = getBgDeltaString(data.getBgDelta(), unit)

        val dashboardIntent = MainActivity.createStartDashboardIntent(context)
        val goToEventPendingIntent = PendingIntent.getActivity(
            context, 0,
            dashboardIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(details)
            .setSmallIcon(R.mipmap.ic_launcher) // Use app icon for now
            .setContentIntent(goToEventPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAllowSystemGeneratedContextualActions(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    override fun updateMainAppNotification(glucoseRepository: GlucoseRepository) {
        val notification: Notification = createMainAppNotification(glucoseRepository)
        notify(AndroidNotifications.FOREGROUND_NOTIFICATION_ID, notification)
    }

    override fun showRecommendationNotification(recommendation: ApsRecommendation) {
        val title = when (recommendation) {
            is ApsRecommendation.Carbs -> context.getString(UiR.string.recommendation_title_carbs)
            is ApsRecommendation.Bolus -> context.getString(UiR.string.recommendation_title_bolus)
        }
        val text = when (recommendation) {
            is ApsRecommendation.Carbs -> context.getString(
                UiR.string.recommendation_text_carbs,
                recommendation.amountInGram
            )
            is ApsRecommendation.Bolus -> context.getString(
                UiR.string.recommendation_text_bolus,
                recommendation.amount.iu
            )
        }

        val dashboardIntent = MainActivity.createStartDashboardIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            dashboardIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // TODO: Add content click handler which goes to add meal/insulin screen with prefilled value
        val notification = NotificationCompat.Builder(context, RECOMMENDATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()

        // Use the same ID for all recommendations, we only support one at a time
        notify(RECOMMENDATION_NOTIFICATION_ID, notification)
    }

    override fun cancelRecommendationNotification() {
        manager.cancel(RECOMMENDATION_NOTIFICATION_ID)
    }

    override fun showApsIssueNotification(issues: Set<ApsIssue>) {
        if (issues.isEmpty()) {
            cancelApsIssueNotification()
            return
        }

        val title = if (issues.size == 1) {
            when (issues.first()) {
                is ApsIssue.Core -> context.getString(UiR.string.core_issue_title)
                is ApsIssue.Pump -> context.getString(UiR.string.pump_issue_title)
                ApsIssue.StaleBG -> context.getString(UiR.string.core_issue_title)
                is ApsIssue.Other -> context.getString(UiR.string.core_issue_title)
            }
        } else {
            context.getString(UiR.string.multiple_issues_title, issues.size)
        }

        val messages = issues.map { getIssueMessage(it) }
        val contentText = if (messages.size == 1) {
            messages.first()
        } else {
            messages.joinToString("; ")
        }

        val bigText = if (messages.size == 1) {
            messages.first()
        } else {
            messages.joinToString("\n") { "• $it" }
        }

        val dashboardIntent = MainActivity.createStartDashboardIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            dashboardIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALGORITHM_ISSUE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false) // Keep it until resolved
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        notify(ALGORITHM_ISSUE_NOTIFICATION_ID, notification)
    }

    private fun getIssueMessage(issue: ApsIssue): String = when (issue) {
        ApsIssue.StaleBG -> context.getString(
            UiR.string.core_issue_no_recent_values,
            STALE_BG_THRESHOLD.value.toInt()
        )
        is ApsIssue.Core -> when (val coreIssue = issue.issue) {
            is CoreIssue.NoRecentValues -> context.getString(
                UiR.string.core_issue_no_recent_values,
                coreIssue.minutes
            )
            is CoreIssue.NoisyValues -> context.getString(UiR.string.core_issue_noisy_values)
            is CoreIssue.InternalError -> context.getString(
                UiR.string.core_issue_internal_error,
                coreIssue.message ?: context.getString(UiR.string.unknown_label)
            )
            CoreIssue.TherapyLockBusy -> context.getString(UiR.string.core_issue_therapy_lock_busy)
            is CoreIssue.NoPumpConnection -> context.getString(UiR.string.core_issue_no_pump_connection)
        }
        is ApsIssue.Pump -> when (issue.issue) {
            PumpIssue.ConnectionMissing -> context.getString(UiR.string.pump_issue_connection_missing)
            PumpIssue.Inoperative -> context.getString(UiR.string.pump_issue_inoperative)
            is PumpIssue.CommandFailed -> context.getString(UiR.string.pump_issue_command_failed)
            PumpIssue.Other -> context.getString(UiR.string.pump_issue_other)
        }
        is ApsIssue.Other -> issue.message ?: context.getString(UiR.string.unknown_label)
    }

    override fun cancelApsIssueNotification() {
        manager.cancel(ALGORITHM_ISSUE_NOTIFICATION_ID)
    }

    private fun notify(notificationId: Int, notification: Notification) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "Missing permissions to show notification")
            return
        }
        try {
            manager.cancel(notificationId) // To force the update of the notification
            manager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Fallback to log message below
        }
    }

    companion object {
        val TAG = AndroidNotificationsImpl::class.simpleName
        const val RECOMMENDATION_NOTIFICATION_ID = 2
        const val ALGORITHM_ISSUE_NOTIFICATION_ID = 3
        const val SERVICE_CHANNEL_ID = "aps_service_channel"
        const val RECOMMENDATION_CHANNEL_ID = "aps_recommendation_channel"
        const val ALGORITHM_ISSUE_CHANNEL_ID = "aps_algorithm_issue_channel"
    }
}