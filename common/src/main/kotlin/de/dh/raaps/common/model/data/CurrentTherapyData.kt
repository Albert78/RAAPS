package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinType

/**
 * Represents the current active therapy data of the app.
 * It references an active [Profile] and holds its own [TherapyData] which might
 * deviate from the profile's data.
 */
data class CurrentTherapyData(
    val id: Long = ID_UNDEFINED,
    val profileId: Long?,
    val therapyData: TherapyData,
    val insulinType: InsulinType
)