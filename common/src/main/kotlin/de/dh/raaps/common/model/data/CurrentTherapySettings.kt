package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinType

/**
 * Represents the current active therapy settings of the app.
 * It references the active [Profile].
 */
data class CurrentTherapySettings(
    var id: Long = ID_UNDEFINED,
    val profile: Profile,
    val insulinType: InsulinType,
    val adjustmentPercentage: Int = 0
)