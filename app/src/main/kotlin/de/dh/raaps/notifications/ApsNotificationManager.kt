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
import de.dh.raaps.MainActivity
import de.dh.raaps.R
import de.dh.raaps.common.model.ToDo
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.core.aps.ApsRecommendation
import de.dh.raaps.ui.screens.permissions.canPostNotifications
import java.util.Locale
import de.dh.raaps.ui.R as UiR

class ApsNotificationManager(
    val context: Context
) {
    private val manager = context.getSystemService<NotificationManager>()!!

    fun createNotificationChannels() {
        // Channel for the foreground service (BG values)
        val serviceName = context.getString(UiR.string.aps_service_notification_channel_name)
        val serviceImportance = NotificationManager.IMPORTANCE_HIGH
        val serviceChannel = NotificationChannel(CHANNEL_ID, serviceName, serviceImportance)
        serviceChannel.setShowBadge(false)

        // Channel for recommendations (User interaction required)
        val recName = context.getString(UiR.string.recommendation_notification_channel_name)
        val recImportance = NotificationManager.IMPORTANCE_HIGH
        val recChannel = NotificationChannel(RECOMMENDATION_CHANNEL_ID, recName, recImportance)
        recChannel.enableVibration(true)
        recChannel.setShowBadge(true)

        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(recChannel)
    }

    fun getBgValueString(sample: BgValue?, forceSign: Boolean): String? {
        if (sample == null) return null
        return if (forceSign) {
            String.format(Locale.getDefault(), "%+d mg/dl", sample.mgdl)
        } else {
            "${sample.mgdl} mg/dl"
        }
    }

    fun getBgDeltaString(delta: BgValue?): String? {
        val bgDeltaStr = getBgValueString(delta, true)
        return if (bgDeltaStr == null) null else "Delta: $bgDeltaStr"
    }

    fun createForegroundServiceNotification(data: ApsMainNotificationData): Notification {
        Log.d(TAG, "Build notification for ${data.lastBgSample}")
        ToDo.toBeImplemented("Take glucose unit from preferences")
        val bgValueStr = getBgValueString(data.lastBgSample?.value, false)
        val title = bgValueStr ?: context.getString(UiR.string.aps_service_notification_content_no_value_yet)
        val details = getBgDeltaString(data.getBgDelta())

        val dashboardIntent = MainActivity.createStartDashboardIntent(context)
        val goToEventPendingIntent = PendingIntent.getActivity(
            context, 0,
            dashboardIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
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

    fun updateNotification(data: ApsMainNotificationData) {
        val notification: Notification = createForegroundServiceNotification(data)
        notify(NOTIFICATION_ID, notification)
    }

    fun showRecommendationNotification(recommendation: ApsRecommendation) {
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

        val notification = NotificationCompat.Builder(context, RECOMMENDATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()

        // Use a different ID for each type or just one for all recommendations?
        // Let's use one ID for now to avoid flooding, or use hash of recommendation
        notify(RECOMMENDATION_NOTIFICATION_ID, notification)
    }

    fun cancelRecommendationNotification() {
        manager.cancel(RECOMMENDATION_NOTIFICATION_ID)
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
        val TAG = ApsNotificationManager::class.simpleName
        const val NOTIFICATION_ID = 1
        const val RECOMMENDATION_NOTIFICATION_ID = 2
        const val CHANNEL_ID = "aps_service_channel"
        const val RECOMMENDATION_CHANNEL_ID = "aps_recommendation_channel"
    }
}