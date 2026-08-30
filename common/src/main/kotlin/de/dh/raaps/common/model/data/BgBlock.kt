package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.DEFAULT_BG_LOW_THRESHOLD_MGDL
import de.dh.raaps.common.model.DEFAULT_BG_TARGET_MGDL
import de.dh.raaps.common.model.MINUTES_PER_DAY

data class BgBlock(val duration: Minutes, val target: BgValue, val lowThreshold: BgValue)

fun List<BgBlock>.isFullDay(): Boolean =
    sumOf { it.duration.value.toInt() } == MINUTES_PER_DAY

/**
 * Returns the BG settings for the given minute since midnight.
 * Assumes the blocks cover the full day.
 */
fun List<BgBlock>.getBgForMinute(minuteSinceMidnight: Minutes): Pair<BgValue, BgValue> {
    var accumulatedMinutes = 0
    for (block in this) {
        accumulatedMinutes += block.duration.value.toInt()
        if (minuteSinceMidnight.value < accumulatedMinutes) {
            return Pair(block.target, block.lowThreshold)
        }
    }
    val last = lastOrNull()
    return if (last != null) Pair(last.target, last.lowThreshold) else Pair(
        BgValue.fromMgDl(DEFAULT_BG_TARGET_MGDL),
        BgValue.fromMgDl(DEFAULT_BG_LOW_THRESHOLD_MGDL))
}