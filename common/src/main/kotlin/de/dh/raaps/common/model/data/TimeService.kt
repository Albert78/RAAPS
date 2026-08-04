package de.dh.raaps.common.model.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Central service for time and ticking in the system.
 */
interface TimeService {
    /**
     * The duration of a single tick.
     */
    val tickInterval: Minutes

    /**
     * The current tick based on the system time.
     */
    val currentTick: Tick

    /**
     * The current system time.
     */
    val currentTime: Timestamp

    /**
     * A [Timeline] instance based on the current [tickInterval].
     */
    val timeline: Timeline

    /**
     * A flow that emits the new [Tick] whenever a tick boundary is crossed.
     */
    val tickFlow: StateFlow<Tick>
}