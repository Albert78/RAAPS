package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.BgDelta
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

enum class InsulinOrigin {
    Pump,
    Manual
}

/**
 * Historical insulin application (Bolus or Basal).
 */
data class InsulinApplication(
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val amount: Double,
    val insulinType: InsulinType,
    val origin: InsulinOrigin,
    val provisional: Boolean = false
)

/**
 * Snapshot of an insulin delivery point.
 */
interface InsulinHistoryPoint {
    val timestamp: Long
    val amount: Double
}

/**
 * Compound object representing a snapshot of the pump's insulin delivery history.
 */
data class InsulinHistory(
    val from: Long,
    val to: Long,
    val points: List<InsulinHistoryPoint>
)

inline fun convertToCarbsFromBgDelta(bgDelta: BgDelta, isf: BgDelta, cr: Double): Double =
    bgDelta.mgdl.toDouble() / isf.mgdl.toDouble() * cr

inline fun convertToUnitsFromBgDelta(bgDelta: BgDelta, isf: BgDelta): Double =
    bgDelta.mgdl.toDouble() / isf.mgdl.toDouble()

inline fun convertToBgDeltaFromUnits(units: Double, isf: BgDelta): BgDelta =
    BgDelta.fromMgDl((units * isf.mgdl.toDouble()).toInt())

inline fun convertToBgDeltaFromCarbs(carbs: Double, isf: BgDelta, cr: Double): BgDelta =
    BgDelta.fromMgDl((carbs / cr * isf.mgdl.toDouble()).toInt())

inline fun convertToUnitsFromCarbs(carbs: Double, cr: Double): Double =
    carbs / cr

inline fun convertToCarbsFromUnits(units: Double, cr: Double): Double =
    units * cr