package de.dh.raaps.core.system

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real-time implementation of [TimeService] that emits ticks based on the system clock.
 */
class SystemTimeService(
    override val tickInterval: Minutes = Timeline.DEFAULT_TICK_INTERVAL,
    scope: CoroutineScope
) : TimeService {

    override val timeline = Timeline(tickInterval)

    private val _tickFlow = MutableStateFlow(timeline.getNowTick())
    override val tickFlow: StateFlow<Tick> = _tickFlow.asStateFlow()

    override val currentTick: Tick get() = timeline.getNowTick()
    override val currentTime: Timestamp get() = Timestamp.now()

    init {
        scope.launch {
            while (true) {
                val now = Timestamp.now()
                val nextTick = timeline.tick(now) + 1
                val nextTickStart = timeline.timestamp(nextTick)
                val delayMs = nextTickStart.ms - now.ms

                if (delayMs > 0) {
                    delay(delayMs)
                }

                _tickFlow.value = timeline.getNowTick()
            }
        }
    }
}