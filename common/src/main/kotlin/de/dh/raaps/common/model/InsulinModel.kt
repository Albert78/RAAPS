package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import java.util.UUID


data class InsulinType(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val peak: Minutes,
    val dia: Minutes
)

/**
 * Historical insulin application.
 */
data class InsulinApplication(
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val insulinUnits: Double,
    val insulinType: InsulinType
)