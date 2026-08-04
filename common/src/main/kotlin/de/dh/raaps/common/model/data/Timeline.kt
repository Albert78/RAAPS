package de.dh.raaps.common.model.data

/**
 * Manages the conversion between absolute [Timestamp]s and discrete [Tick]s
 * based on a fixed interval.
 */
class Timeline(val tickDuration: Minutes) {
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

    fun ticksPerHour(): Int {
        return 60 / tickDuration.value
    }

    fun inTicks(minutes: Minutes): Int {
        return minutes.value / tickDuration.value
    }

    companion object {
        val DEFAULT_TICK_INTERVAL = Minutes(5)
    }
}