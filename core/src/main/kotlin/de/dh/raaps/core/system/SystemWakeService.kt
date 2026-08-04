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
     * @param wakeupId The ID of the wakeup that was scheduled, or null if no ID was provided.
     * @param intent The intent that triggered the wakeup, containing potential extras.
     */
    fun onWakeup(wakeupId: UInt?, intent: Intent?)
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
     *
     * @param tag The tag associated with the handler. This tag must be unique among all
     * wakeup handlers.
     * @param wakeupId An optional non-negative ID to identify this specific wakeup.
     * @param timestamp The absolute time for the wakeup.
     */
    fun scheduleWakeup(tag: String, wakeupId: UInt?, timestamp: Timestamp)

    /**
     * Acquires a wake lock to prevent the device from sleeping.
     * The lock is reference counted globally. Each call to this method must be
     * balanced with a call to [releaseBusyState].
     *
     * @param tag Informational tag identifying the component acquiring the busy state.
     */
    fun acquireBusyState(tag: String)

    /**
     * Releases a previously acquired wake lock.
     *
     * @param tag Informational tag identifying the component releasing the busy state.
     */
    fun releaseBusyState(tag: String)

    /**
     * Dispatches an incoming wakeup intent to the appropriate handler.
     * This is intended to be called by the central [SystemWakeReceiver].
     */
    fun dispatchWakeup(intent: Intent)
}