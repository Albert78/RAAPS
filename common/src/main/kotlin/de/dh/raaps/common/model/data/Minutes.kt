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

    fun inMs(): Long {
        return value * 60L * 1000L
    }

    companion object {
        val ONE_HOUR = Minutes(MINUTES_PER_HOUR.toShort())
        val ONE_DAY = Minutes(MINUTES_PER_DAY.toShort())
    }
}