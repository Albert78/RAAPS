package de.dh.raaps.common.model.mock

import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.InsulinProfile

fun mockInsulinType() = InsulinType(
    name = "Mock Insulin",
    dia = Minutes.ofHours(5),
    peak = Minutes(75)
)

fun mockSimpleInsulinProfile() =
    InsulinProfile(
        name = "Mock Profile",
        crBlocks = listOf(
            Block(
                Minutes.ONE_DAY,
                10.0
            )
        ),
        isfBlocks = listOf(
            Block(
                Minutes.ONE_DAY,
                44.0
            )
        ),
        basalBlocks = listOf(
            Block(
                Minutes.ONE_DAY,
                0.5
            )
        ),
        insulinType = mockInsulinType(),
        insulinConcentration = de.dh.raaps.common.model.InsulinConcentration.U100,
        dia = Minutes.ofHours(5),
        peak = Minutes(75)
    )
