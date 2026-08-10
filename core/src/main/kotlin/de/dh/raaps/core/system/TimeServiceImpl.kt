package de.dh.raaps.core.system

import android.content.Intent
import android.util.Log
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementation of [TimeService] that synchronizes its ticking grid with an external reference.
 */
class TimeServiceImpl(
    override val tickInterval: Minutes = Timeline.DEFAULT_TICK_INTERVAL,
    private val wakeService: SystemWakeService,
    private val scope: CoroutineScope
) : TimeService, WakeupHandler {
    override val timeline = Timeline(tickInterval)

    private val _tickFlow = MutableStateFlow(timeline.getNowTick())
    override val tickFlow: StateFlow<Tick> = _tickFlow.asStateFlow()

    override val currentTick: Tick get() = timeline.getNowTick()
    override val currentTime: Timestamp get() = Timestamp.now()

    private data class HandlerEntry(val priority: Int, val handler: TickHandler)
    private val handlers = CopyOnWriteArrayList<HandlerEntry>()

    private val firstSyncDeferred = CompletableDeferred<Unit>()

    override fun registerTickHandler(priority: Int, handler: TickHandler) {
        handlers.add(HandlerEntry(priority, handler))
        handlers.sortBy { it.priority }
    }

    override fun onWakeup(wakeupId: UInt?, intent: Intent?) {
        Log.d(TAG, "System wakeup received (wakeupId=$wakeupId)")
        
        scope.launch {
            try {
                wakeService.acquireBusyState(TAG)
                
                val tick = timeline.getNowTick()
                Log.d(TAG, "Tick triggered via system wakeup: $tick (timelineOffset=${timeline.offsetMs})")

                // Sequential execution of handlers
                handlers.forEach { entry ->
                    try {
                        entry.handler.onTick(tick)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in tick handler ${entry.handler}", e)
                    }
                }

                _tickFlow.value = tick
            } finally {
                // Schedule next tick before releasing busy state
                scheduleNextTick()
                wakeService.releaseBusyState(TAG)
            }
        }
    }

    private fun scheduleNextTick() {
        val now = Timestamp.now()
        val currentTick = timeline.getNowTick()

        // Calculate next scheduled tick time based on the synchronized timeline
        var nextTickTimeMs = timeline.timestamp(currentTick + 1).ms

        // Safety: If we already passed the next tick time, go to the one after
        if (nextTickTimeMs <= now.ms) {
            nextTickTimeMs += timeline.tickSizeMs
        }

        val nextTickTimestamp = Timestamp(nextTickTimeMs)
        wakeService.scheduleWakeup(TAG, null, nextTickTimestamp)
        Log.d(TAG, "Scheduled next system wakeup at $nextTickTimestamp")
    }

    override fun unregisterTickHandler(handler: TickHandler) {
        handlers.removeIf { it.handler == handler }
    }

    override fun synchronize(synchronizationTimestamp: Timestamp) {
        val tickSizeMs = timeline.tickSizeMs

        // Desired offset relative to the Unix Epoch
        val targetOffsetMs = synchronizationTimestamp.ms % tickSizeMs

        if (!firstSyncDeferred.isCompleted) {
            timeline.offsetMs = targetOffsetMs
            Log.d(TAG, "Initial sync received. Setting timeline offset to ${timeline.offsetMs} ms")
            firstSyncDeferred.complete(Unit)
        } else {
            // Gradual adjustment of the timeline offset
            var diff = targetOffsetMs - timeline.offsetMs
            if (diff > tickSizeMs / 2) diff -= tickSizeMs
            if (diff < -tickSizeMs / 2) diff += tickSizeMs

            val adjustment = (diff * 0.2).toLong()
            timeline.offsetMs += adjustment
            Log.d(TAG, "Sync adjustment: targetOffset=$targetOffsetMs, currentOffset=${timeline.offsetMs}, adj=$adjustment")
            
            // Re-schedule next tick to align with new offset
            scheduleNextTick()
        }
    }

    init {
        wakeService.registerHandler(TAG, this)

        scope.launch {
            Log.d(TAG, "Waiting for initial synchronization...")
            firstSyncDeferred.await()
            Log.d(TAG, "Starting event-driven ticking cycle with offset ${timeline.offsetMs}")
            
            scheduleNextTick()
        }
    }

    companion object {
        private const val TAG = "SynchronizedTimeService"
    }
}
