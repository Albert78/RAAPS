package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.MINUTES_PER_DAY

data class Block(val duration: Minutes, val amount: Double)

fun List<Block>.isFullDay(): Boolean =
    sumOf { it.duration.value.toInt() } == MINUTES_PER_DAY