package de.dh.raaps.model

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp

/**
 * Manages the conversion between absolute [Timestamp]s and discrete [Tick]s
 * based on a fixed interval.
 */
class ApsTimeline(val tickDuration: Minutes) {
    val tickSizeMs: Long = tickDuration.value.toLong() * 60 * 1000

    /**
     * Converts a [Timestamp] to a [Tick].
     */
    fun tick(timestamp: Timestamp): Tick {
        return Tick((timestamp.ms / tickSizeMs).toInt())
    }

    /**
     * Converts a [Tick] to its starting [Timestamp].
     */
    fun timestamp(tick: Tick): Timestamp {
        return Timestamp(tick.value.toLong() * tickSizeMs)
    }

    /**
     * Returns the [Tick] corresponding to the current time.
     */
    fun getNowTick(): Tick {
        return tick(Timestamp.now())
    }
}