package de.dh.raaps.core.system

import android.content.Intent
import android.util.Log
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.SystemMetricsRepository
import de.dh.raaps.core.repository.TickHandlerMetric
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.round

/**
 * Implementation of [TimeService] that synchronizes its ticking grid with an external reference.
 */
class TimeServiceImpl(
    override val tickInterval: Minutes = Timeline.DEFAULT_TICK_INTERVAL,
    private val wakeService: SystemWakeService,
    private val systemMetricsRepository: SystemMetricsRepository,
    private val scope: CoroutineScope
) : TimeService, WakeupHandler {
    override val timeline = Timeline(tickInterval)

    override var executionOffsetMs: Long = 0L
        set(value) {
            field = value
            if (firstSyncDeferred.isCompleted) {
                scheduleNextTick()
            }
        }

    private val _tickFlow = MutableStateFlow(timeline.getNowTick())
    override val tickFlow: StateFlow<Tick> = _tickFlow.asStateFlow()

    override val currentTick: Tick get() = timeline.getNowTick()
    override val currentTime: Timestamp get() = Timestamp.now()

    private data class HandlerEntry(val priority: Int, val handler: TickHandler, val name: String)
    private val handlers = CopyOnWriteArrayList<HandlerEntry>()

    private val firstSyncDeferred = CompletableDeferred<Unit>()

    override fun registerTickHandler(priority: Int, handler: TickHandler, name: String?) {
        val handlerName = name ?: handler.javaClass.simpleName.ifBlank { handler.toString() }
        handlers.add(HandlerEntry(priority, handler, handlerName))
        handlers.sortBy { it.priority }
    }

    override fun onWakeup(wakeupId: UInt?, intent: Intent?) {
        Log.d(TAG, "System wakeup received (wakeupId=$wakeupId)")

        scope.launch {
            try {
                wakeService.acquireBusyState(WAKE_TAG)
                val now = Timestamp.now()
                val tick = timeline.tick(Timestamp(now.ms - executionOffsetMs))
                Log.d(TAG, "Tick triggered via system wakeup: $tick (timelineOffset=${timeline.offsetMs}, executionOffset=$executionOffsetMs)")

                // Sequential execution of handlers
                handlers.forEach { entry ->
                    val startTime = Timestamp.now()
                    try {
                        entry.handler.onTick(tick)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in tick handler ${entry.handler}", e)
                    } finally {
                        val endTime = Timestamp.now()
                        systemMetricsRepository.saveTickMetric(
                            TickHandlerMetric(
                                tick = tick,
                                handlerName = entry.name,
                                startTime = startTime,
                                endTime = endTime
                            )
                        )
                    }
                }

                _tickFlow.value = tick
            } finally {
                // Schedule next tick before releasing busy state
                scheduleNextTick()
                wakeService.releaseBusyState(WAKE_TAG)
            }
        }
    }

    private fun scheduleNextTick() {
        val now = Timestamp.now()
        var currentTick = timeline.getNowTick()
        var nextWakeupTimeMs = timeline.timestamp(currentTick).ms + executionOffsetMs

        // Safety: If we already passed the wakeup time for currentTick, schedule for the next tick
        if (nextWakeupTimeMs <= now.ms) {
            currentTick += 1
            nextWakeupTimeMs = timeline.timestamp(currentTick).ms + executionOffsetMs
        }

        val nextTickTimestamp = Timestamp(nextWakeupTimeMs)
        wakeService.scheduleWakeup(WAKE_TAG, null, nextTickTimestamp)
        Log.d(TAG, "Scheduled next system wakeup at $nextTickTimestamp for tick $currentTick (executionOffset=$executionOffsetMs ms)")
    }

    override fun unregisterTickHandler(handler: TickHandler) {
        handlers.removeIf { it.handler == handler }
    }

    override fun synchronize(synchronizationTimestamp: Timestamp) {
        val tickSizeMs = timeline.tickSizeMs

        if (!firstSyncDeferred.isCompleted) {
            timeline.offsetMs = Math.floorMod(synchronizationTimestamp.ms, tickSizeMs)

            firstSyncDeferred.complete(Unit)
            return
        }

        val targetOffsetMs = Math.floorMod(synchronizationTimestamp.ms, tickSizeMs)

        val currentOffset = timeline.offsetMs
        var target = targetOffsetMs

        // Select the representation of target that is closest to the
        // current (possibly out-of-range) offset.
        while (target - currentOffset > tickSizeMs / 2) {
            target -= tickSizeMs
        }
        while (target - currentOffset < -tickSizeMs / 2) {
            target += tickSizeMs
        }

        val diff = target - currentOffset
        val adjustment = round(diff * SMOOTHING_FACTOR).toLong()
        timeline.offsetMs += adjustment

        /*
         * Fold only after passing the hysteresis boundary.
         */
        if (timeline.offsetMs >= tickSizeMs + HYSTERESIS_MS) {
            timeline.offsetMs -= tickSizeMs
        } else if (timeline.offsetMs < -HYSTERESIS_MS) {
            timeline.offsetMs += tickSizeMs
        }

        scheduleNextTick()
    }

    init {
        wakeService.registerHandler(WAKE_TAG, this)

        scope.launch {
            Log.d(TAG, "Waiting for initial synchronization...")
            firstSyncDeferred.await()
            Log.d(TAG, "Starting event-driven ticking cycle with offset ${timeline.offsetMs}")

            scheduleNextTick()
        }
    }

    companion object {
        private val TAG = TimeServiceImpl::class.simpleName!!
        private const val WAKE_TAG = "TIME_SERVICE"
        private const val SMOOTHING_FACTOR = 0.2
        private const val HYSTERESIS_MS = 30_000L
    }
}