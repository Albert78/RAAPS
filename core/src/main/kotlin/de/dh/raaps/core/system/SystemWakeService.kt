package de.dh.raaps.core.system

import android.content.Intent
import de.dh.raaps.common.model.data.Timestamp

/**
 * Interface for components that want to be notified when a system wakeup occurs.
 */
interface WakeupHandler {
    /**
     * Called when a system wakeup for the registered tag occurs.
     *
     * @param wakeupId The ID of the wakeup that was scheduled.
     * @param intent The intent that triggered the wakeup, containing potential extras.
     */
    fun onWakeup(wakeupId: Int, intent: Intent?)
}

/**
 * Service to manage system wakeups and wake locks across the application.
 * This ensures that the device stays awake when performing critical background tasks
 * and handles the scheduling of future wakeups.
 */
interface SystemWakeService {
    /**
     * Registers a handler for a specific tag.
     */
    fun registerHandler(tag: String, handler: WakeupHandler)

    /**
     * Schedules a system wakeup at the given timestamp for a specific tag.
     */
    fun scheduleWakeup(tag: String, wakeupId: Int, timestamp: Timestamp)

    /**
     * Acquires a wake lock for the given tag to prevent the device from sleeping.
     * The lock is reference counted per tag.
     */
    fun acquireBusyState(tag: String)

    /**
     * Releases a previously acquired wake lock for the given tag.
     */
    fun releaseBusyState(tag: String)

    /**
     * Dispatches an incoming wakeup intent to the appropriate handler.
     * This is intended to be called by the central [SystemWakeReceiver].
     */
    fun dispatchWakeup(intent: Intent)
}