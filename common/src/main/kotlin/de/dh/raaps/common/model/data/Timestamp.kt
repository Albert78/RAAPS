package de.dh.raaps.common.model.data

import kotlin.time.Instant

/**
 * A memory-efficient, type-safe representation of a timestamp in Milliseconds since
 * the Unix Epoch (January 1, 1970).
 */
@JvmInline
value class Timestamp(val ms: Long) : Comparable<Timestamp> {
    override fun compareTo(other: Timestamp): Int = ms.compareTo(other.ms)

    operator fun minus(other: Timestamp): Long = ms - other.ms

    fun toInstant(): Instant = Instant.fromEpochMilliseconds(ms)
    fun plusSeconds(sec: Int) = Timestamp(ms + sec * 1000)
    fun minusSeconds(sec: Int) = Timestamp(ms - sec * 1000)
    fun plusMinutes(min: Int) = Timestamp(ms + min * 60 * 1000)
    fun minusMinutes(min: Int) = Timestamp(ms - min * 60 * 1000)
    fun plusHours(hours: Int) = Timestamp(ms + hours * 60 * 60 * 1000)
    fun minusHours(hours: Int) = Timestamp(ms - hours * 60 * 60 * 1000)

    companion object {
        fun now(): Timestamp  = Timestamp(System.currentTimeMillis())
    }
}