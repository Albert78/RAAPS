package de.dh.raaps.common.model.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for components that need to be notified on every tick.
 */
interface TickHandler {
    suspend fun onTick(tick: Tick)
}

/**
 * Priority constants for [TickHandler] execution order.
 * Lower values are executed first.
 */
object TickPriority {
    const val SIMULATION = 100
    const val CORE = 200
    const val UI = 300
}

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

    /**
     * Registers a [TickHandler] to be called on every tick.
     * Handlers are called sequentially in order of their [priority].
     */
    fun registerTickHandler(priority: Int, handler: TickHandler)

    /**
     * Unregisters a previously registered [TickHandler].
     */
    fun unregisterTickHandler(handler: TickHandler)
}