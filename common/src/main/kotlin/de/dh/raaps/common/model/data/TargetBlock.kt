package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.MINUTES_PER_DAY

data class TargetBlock(val duration: Minutes, val lowTarget: BgValue, val highTarget: BgValue)

fun List<TargetBlock>.isFullDay(): Boolean =
    sumOf { it.duration.value.toInt() } == MINUTES_PER_DAY