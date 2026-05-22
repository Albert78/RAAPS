package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.MINUTES_PER_DAY
import de.dh.raaps.common.model.MINUTES_PER_HOUR

/**
 * A memory-efficient, type-safe representation of a small number of minutes, supported range is
 * from 0 until three weeks.
 */
@JvmInline
value class Minutes(val value: Short) : Comparable<Minutes> {
    override fun compareTo(other: Minutes): Int = value.compareTo(other.value)

    operator fun plus(other: Minutes) = Minutes((value + other.value).toShort())
    operator fun times(factor: Int): Minutes = Minutes((value * factor).toShort())

    fun inMs(): Long {
        return value * 60L * 1000L
    }

    companion object {
        val ONE_HOUR = Minutes(MINUTES_PER_HOUR.toShort())
        val ONE_DAY = Minutes(MINUTES_PER_DAY.toShort())

        fun timeDifference(ts1: Timestamp, ts2: Timestamp) =
            Minutes(((ts2.ms - ts1.ms) / 60_000.0).toInt().toShort())

        fun ofHours(hours: Int): Minutes {
            return ONE_HOUR * hours
        }

        fun ofDays(days: Int): Minutes {
            return ONE_DAY * days
        }
    }
}