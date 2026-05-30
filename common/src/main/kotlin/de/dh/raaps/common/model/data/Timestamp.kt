package de.dh.raaps.common.model.data

import java.util.Calendar
import kotlin.math.max
import kotlin.time.Instant

/**
 * A memory-efficient, type-safe representation of a timestamp in Milliseconds since
 * the Unix Epoch (January 1, 1970).
 */
@JvmInline
value class Timestamp(val ms: Long): Comparable<Timestamp> {
    override fun compareTo(other: Timestamp): Int = ms.compareTo(other.ms)

    operator fun minus(other: Timestamp): Long = ms - other.ms
    operator fun minus(minutes: Minutes) = minusMinutes(minutes.value.toInt())

    fun toInstant(): Instant = Instant.fromEpochMilliseconds(ms)
    fun plusSeconds(sec: Int) = Timestamp(ms + sec * 1000)
    fun plusSeconds(sec: Double) = Timestamp(ms + (sec * 1000).toLong())
    fun minusSeconds(sec: Int) = Timestamp(ms - sec * 1000)
    fun minusSeconds(sec: Double) = Timestamp(ms - (sec * 1000).toLong())
    fun plusMinutes(min: Int) = Timestamp(ms + min * 60 * 1000)
    fun plusMinutes(min: Double) = Timestamp(ms + (min * 60 * 1000).toLong())
    fun minusMinutes(min: Int) = Timestamp(ms - min * 60 * 1000)
    fun minusMinutes(min: Double) = Timestamp(ms - (min * 60 * 1000).toLong())
    fun plusHours(hours: Int) = Timestamp(ms + hours * 60 * 60 * 1000)
    fun plusHours(hours: Double) = Timestamp(ms + (hours * 60 * 60 * 1000).toLong())
    fun minusHours(hours: Int) = Timestamp(ms - hours * 60 * 60 * 1000)
    fun minusHours(hours: Double) = Timestamp(ms - (hours * 60 * 60 * 1000).toLong())
    fun minusMs(ms: Long) = Timestamp(this.ms - ms)
    fun plusMs(ms: Long) = Timestamp(this.ms + ms)

    /**
     * Returns the minutes since midnight for this timestamp in the local timezone.
     */
    fun minutesSinceMidnight(): Minutes {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = ms
        return Minutes((calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)).toShort())
    }

    companion object {
        fun now(): Timestamp  = Timestamp(System.currentTimeMillis())
    }
}

fun max(ts1: Timestamp, ts2: Timestamp) = Timestamp(max(ts1.ms, ts2.ms))