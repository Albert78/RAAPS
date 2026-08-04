package de.dh.raaps.common.model.mock

import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TherapyData

fun mockSimpleTherapyData() =
    TherapyData(
        icBlocks = listOf(
            Block(
                Minutes.Companion.ONE_DAY,
                10.0
            )
        ),
        isfBlocks = listOf(
            Block(
                Minutes.Companion.ONE_DAY,
                44.0
            )
        ),
        basalBlocks = listOf(
            Block(
                Minutes.Companion.ONE_DAY,
                0.5
            )
        )
    )