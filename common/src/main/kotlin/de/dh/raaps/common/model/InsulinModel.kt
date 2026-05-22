package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp


data class InsulinType(
    val name: String,
    val dia: Minutes,
    val peak: Minutes
)

object InsulinTypes {
    /**
     * Rapid acting insulin.
     * Example: NovoRapid.
     */
    val ASPART = InsulinType(
        name = "Aspart",
        dia = Minutes.ONE_HOUR * 5,
        peak = Minutes(75)
    )

    /**
     * Ultra rapid insulin.
     */
    val FIASP = InsulinType(
        name = "Fiasp",
        dia = Minutes.ONE_HOUR * 4,
        peak = Minutes(55)
    )
}

/**
 * Historical insulin application.
 */
data class InsulinEntry(
    val timestamp: Timestamp,
    val insulinUnits: Double,
    val type: InsulinType
)