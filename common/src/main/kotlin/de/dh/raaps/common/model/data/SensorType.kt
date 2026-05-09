package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED

data class SensorType(
    val id: Long = ID_UNDEFINED,
    val name: String
)