package de.dh.raaps.core.system

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.SystemMetricsRepository
import de.dh.raaps.core.repository.WakeupMetric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implementation of the [SystemWakeService] using Android's [AlarmManager] and [PowerManager].
 * It manages a single shared [PowerManager.WakeLock] while maintaining a reference count
 * per tag to ensure the device stays awake as long as any component is busy.
 */
class SystemWakeServiceImpl(
    private val context: Context,
    private val systemMetricsRepository: SystemMetricsRepository,
    private val scope: CoroutineScope
) : SystemWakeService {

    private val handlers = ConcurrentHashMap<String, WakeupHandler>()
    private val busyCount = AtomicInteger(0)

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "raaps:GlobalWakeLock")
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun registerHandler(tag: String, handler: WakeupHandler) {
        handlers[tag] = handler
    }

    @SuppressLint("ObsoleteSdkInt", "MissingPermission")
    override fun scheduleWakeup(tag: String, wakeupId: UInt?, timestamp: Timestamp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "Permission for exact alarms is missing, cannot schedule wakeup for $tag")
                return
            }
        }

        val internalWakeupId = wakeupId?.toInt() ?: -1

        val intent = Intent(context, SystemWakeReceiver::class.java).apply {
            action = ACTION_WAKEUP
            putExtra(EXTRA_TAG, tag)
            putExtra(EXTRA_WAKEUP_ID, internalWakeupId)
            putExtra(EXTRA_SCHEDULED_TIME, timestamp.ms)
        }

        // Create a unique requestCode combining tag hash and wakeupId to avoid collisions
        val requestCode = (tag.hashCode() xor internalWakeupId)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timestamp.ms,
                pendingIntent
            )
            Log.d(TAG, "Scheduled system wakeup for $tag at $timestamp with ID $internalWakeupId")
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to schedule exact alarm for $tag", e)
        }
    }

    override fun acquireBusyState(tag: String) {
        val count = busyCount.incrementAndGet()
        Log.v(TAG, "BusyState acquired for $tag (global count: $count)")

        synchronized(wakeLock) {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(30_000) // Default timeout as safety net
            }
        }
    }

    override fun releaseBusyState(tag: String) {
        val count = busyCount.decrementAndGet()
        Log.v(TAG, "BusyState released for $tag (global count: $count)")

        if (count < 0) {
            Log.e(TAG, "Detected unbalanced busy state release for tag '$tag'! Global busyCount reached $count. Check for missing acquireBusyState calls. Resetting to 0.")
            busyCount.set(0)
        }

        checkAndReleaseWakeLock()
    }

    private fun checkAndReleaseWakeLock() {
        if (busyCount.get() <= 0) {
            synchronized(wakeLock) {
                if (wakeLock.isHeld) {
                    try {
                        wakeLock.release()
                    } catch (_: RuntimeException) {
                        // Already released
                    }
                }
            }
        }
    }

    override fun dispatchWakeup(intent: Intent) {
        if (intent.action != ACTION_WAKEUP) return

        val dispatchTime = Timestamp.now()
        val tag = intent.getStringExtra(EXTRA_TAG) ?: return
        val internalWakeupId = intent.getIntExtra(EXTRA_WAKEUP_ID, -1)
        val wakeupId = if (internalWakeupId == -1) null else internalWakeupId.toUInt()
        val scheduledTimeMs = intent.getLongExtra(EXTRA_SCHEDULED_TIME, -1)
        val scheduledTime = if (scheduledTimeMs == -1L) Timestamp.INVALID else Timestamp(scheduledTimeMs)

        var onWakeupStartTime: Timestamp? = null
        var onWakeupEndTime: Timestamp? = null

        // Temporarily acquire wake lock to ensure handler has time to process
        acquireBusyState("DISPATCH_WAKEUP_$tag")
        try {
            onWakeupStartTime = Timestamp.now()
            handlers[tag]?.onWakeup(wakeupId, intent)
            onWakeupEndTime = Timestamp.now()
        } finally {
            releaseBusyState("DISPATCH_WAKEUP_$tag")

            scope.launch {
                systemMetricsRepository.saveWakeupMetric(
                    WakeupMetric(
                        tag = tag,
                        wakeupId = wakeupId,
                        scheduledTime = scheduledTime,
                        dispatchTime = dispatchTime,
                        onWakeupStartTime = onWakeupStartTime,
                        onWakeupEndTime = onWakeupEndTime
                    )
                )
            }
        }
    }

    companion object {
        private const val TAG = "SystemWakeService"
        private const val ACTION_WAKEUP = "de.dh.raaps.core.system.ACTION_WAKEUP"
        private const val EXTRA_TAG = "extra_tag"
        private const val EXTRA_WAKEUP_ID = "extra_wakeup_id"
        private const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    }
}