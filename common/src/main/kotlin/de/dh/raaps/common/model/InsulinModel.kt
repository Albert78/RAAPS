package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import java.util.UUID


/**
 * Represents a specific insulin preparation (e.g., NovoRapid, Fiasp).
 * Defines its metabolic activity profile through peak time and duration of action (DIA).
 */
data class InsulinType(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val peak: Minutes,
    val dia: Minutes
)

/**
 * Represents an amount of insulin in International Units (IU).
 * This is the standard unit for therapeutic calculations (U100 equivalent).
 */
@JvmInline
value class InsulinAmount(val iu: Double) {
    /**
     * Calculates the physical amount that the pump must actually deliver.
     *
     * @param concentration The concentration of the insulin used.
     * @return The amount in "Pump Units" or "Concentrated Units" (cU).
     */
    fun toPumpUnits(concentration: InsulinConcentration): Double {
        return iu / concentration.factor
    }

    companion object {
        val ZERO = InsulinAmount(0.0)

        fun fromPumpUnits(value: Double, concentration: InsulinConcentration): InsulinAmount {
            return InsulinAmount(value * concentration.factor)
        }
    }
}

/**
 * Defines the insulin concentration.
 *
 * @param factor The ratio relative to U100 (e.g., 0.2 for U20, 2.0 for U200).
 */
@JvmInline
value class InsulinConcentration(val factor: Double) {
    companion object {
        val U20 = InsulinConcentration(0.2)
        val U40 = InsulinConcentration(0.4)
        val U100 = InsulinConcentration(1.0)
        val U200 = InsulinConcentration(2.0)

        /**
         * Creates a concentration based on the U-rating.
         * @param uValue The units per ml (e.g., 20 for U20).
         */
        fun fromUValue(uValue: Int): InsulinConcentration = InsulinConcentration(uValue / 100.0)
    }
}

/**
 * Historical insulin application.
 */
data class InsulinApplication(
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val insulinUnits: Double,
    val insulinType: InsulinType
)