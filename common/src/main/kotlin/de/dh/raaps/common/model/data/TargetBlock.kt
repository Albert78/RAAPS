package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.MINUTES_PER_DAY

data class TargetBlock(val duration: Minutes, val lowTarget: BgValue, val highTarget: BgValue)

fun List<TargetBlock>.isFullDay(): Boolean =
    sumOf { it.duration.value.toInt() } == MINUTES_PER_DAY

/**
 * Returns the target range for the given minute since midnight.
 * Assumes the blocks cover the full day.
 */
fun List<TargetBlock>.getTargetForMinute(minuteSinceMidnight: Minutes): Pair<BgValue, BgValue> {
    var accumulatedMinutes = 0
    for (block in this) {
        accumulatedMinutes += block.duration.value.toInt()
        if (minuteSinceMidnight.value < accumulatedMinutes) {
            return Pair(block.lowTarget, block.highTarget)
        }
    }
    val last = lastOrNull()
    return if (last != null) Pair(last.lowTarget, last.highTarget) else Pair(BgValue(80), BgValue(120))
}