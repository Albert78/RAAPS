package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED

data class SensorType(
    var id: Long = ID_UNDEFINED,
    val name: String
)