package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.MINUTES_PER_DAY

data class Block(val duration: Minutes, val amount: Double)

fun List<Block>.isFullDay(): Boolean =
    sumOf { it.duration.value.toInt() } == MINUTES_PER_DAY

/**
 * Returns the amount for the given minute since midnight.
 * Assumes the blocks cover the full day.
 */
fun List<Block>.getAmountForMinute(minuteSinceMidnight: Minutes): Double {
    var accumulatedMinutes = 0
    for (block in this) {
        accumulatedMinutes += block.duration.value.toInt()
        if (minuteSinceMidnight.value < accumulatedMinutes) {
            return block.amount
        }
    }
    return lastOrNull()?.amount ?: 0.0
}
