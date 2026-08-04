package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinType

/**
 * Represents the current active therapy settings of the app.
 * It references the active [InsulinProfile].
 */
data class CurrentTherapySettings(
    var id: Long = ID_UNDEFINED,
    val profile: InsulinProfile,
    val insulinType: InsulinType,
    val adjustmentPercentage: Int = 0,
    val defaultBgBlocks: List<BgBlock> = emptyList()
)