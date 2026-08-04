package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED

/**
 * Represents the current active therapy settings of the app.
 * It references the active [InsulinProfile].
 */
data class CurrentTherapySettings(
    var id: Long = ID_UNDEFINED,
    val insulinProfile: InsulinProfile,
    val defaultBgBlocks: List<BgBlock> = emptyList(),
    val adjustmentPercentage: Int = 0,
)