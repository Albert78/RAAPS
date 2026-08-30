package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import java.util.UUID
import kotlin.math.abs


/**
 * Represents a specific insulin preparation (e.g., NovoRapid, Fiasp).
 * Defines its metabolic activity profile through peak time and duration of action (DIA).
 */
data class InsulinType(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val peak: Minutes,
    val dia: Minutes,
    val defaultConcentration: InsulinConcentration = InsulinConcentration.U100
)

/**
 * Represents an amount of insulin in International Units (IU).
 * This is the standard unit for therapeutic calculations (U100 equivalent).
 */
@JvmInline
value class InsulinAmount(val iu: Double): Comparable<InsulinAmount> {
    operator fun plus(amount: InsulinAmount) = InsulinAmount(iu + amount.iu)
    operator fun minus(amount: InsulinAmount) = InsulinAmount(iu - amount.iu)
    operator fun times(factor: Double) = InsulinAmount(iu * factor)
    operator fun div(factor: Double) = InsulinAmount(iu / factor)
    operator fun div(other: InsulinAmount): Double = iu / other.iu
    operator fun unaryMinus() = InsulinAmount(-iu)

    fun abs(): InsulinAmount = InsulinAmount(abs(iu))

    override operator fun compareTo(other: InsulinAmount): Int = iu.compareTo(other.iu)

    fun coerceAtLeast(minimumValue: InsulinAmount): InsulinAmount {
        return if (this < minimumValue) minimumValue else this
    }

    fun coerceAtMost(maximumValue: InsulinAmount): InsulinAmount {
        return if (this > maximumValue) maximumValue else this
    }

    fun coerceIn(minimumValue: InsulinAmount, maximumValue: InsulinAmount): InsulinAmount {
        return this.coerceAtLeast(minimumValue).coerceAtMost(maximumValue)
    }

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
        val EPSILON = InsulinAmount(0.01)

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
 * Represents a bolus that is planned relative to a meal.
 *
 * This model is primarily intended for user interaction. It serves as the basis for
 * presenting the proposed insulin plan to the user and allowing them to review
 * or adjust the timing and amounts before the plan is finalized.
 */
data class PlannedInsulin(
    val amount: InsulinAmount,
    /**
     * Time relative to the meal timestamp.
     * Can be negative (bolus before meal) or positive (bolus after meal).
     */
    val timeFromMeal: Minutes = Minutes(0),
    /**
     * Weight of this bolus in combination with other boluses, in percent.
     */
    val partWeight: Int? = null
)

enum class InsulinOrigin {
    /**
     * An insulin dose delivered by the insulin pump.
     */
    Pump,

    /**
     * An insulin dose administered manually using a pen or a syringe.
     */
    Manual
}

/**
 * Distinguishing between Basal and Bolus insulin.
 */
enum class InsulinCategory {
    Basal, Bolus
}

/**
 * Represents a historical insulin dose that is assumed to have been administered.
 * For insulin deliveries that are planned but not yet confirmed as delivered,
 * use [ScheduledPumpInsulin].
 */
data class InsulinApplication(
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val amount: InsulinAmount,
    /**
     * The type of the insulin (peak, dia etc.)
     */
    val insulinType: InsulinType,
    /**
     * If the application was injected via pump or manual.
     */
    val origin: InsulinOrigin,
    val basal: Boolean = false,
    val correction: Boolean = false,
    val meal: Boolean = false
)

/**
 * Represents an insulin dose that is scheduled to be delivered by the pump.
 *
 * This entity serves as temporary storage for delivery metadata until the pump
 * confirms the delivery. Once confirmed, the data is migrated to an [InsulinApplication].
 */
data class ScheduledPumpInsulin(
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val amount: InsulinAmount,
    val basal: Boolean = false,
    val correction: Boolean = false,
    val meal: Boolean = false
)

/**
 * Represents a bolus dose that is scheduled for future delivery by the system.
 *
 * This is typically used to handle meals with long absorption times (e.g., high-fat or
 * high-protein meals) by split-bolusing or delaying part of the insulin to match
 * the carbohydrate impact.
 */
data class DeferredBolus(
    var id: Long = ID_UNDEFINED,
    val amount: InsulinAmount,
    val timestamp: Timestamp,
    val mealId: Long? = null
)

/**
 * Snapshot of an insulin delivery point.
 */
interface InsulinHistoryPoint {
    val timestamp: Long
    val amount: InsulinAmount
    val category: InsulinCategory
}

/**
 * Compound object representing a snapshot of the pump's insulin delivery history.
 */
data class InsulinHistory(
    val from: Long,
    val to: Long,
    val points: List<InsulinHistoryPoint>
)

inline fun convertToInsulinAmountFromBgDelta(bgDelta: BgDelta, isf: BgDelta): InsulinAmount =
    InsulinAmount(bgDelta.mgdl / isf.mgdl)

inline fun convertToCarbsFromBgDelta(bgDelta: BgDelta, isf: BgDelta, cr: Double): Double =
    convertToCarbsFromUnits(convertToInsulinAmountFromBgDelta(bgDelta, isf), cr)

inline fun convertToBgDeltaFromUnits(amount: InsulinAmount, isf: BgDelta): BgDelta =
    BgDelta.fromMgDl((amount.iu * isf.mgdl).toInt())

inline fun convertToBgDeltaFromCarbs(carbs: Double, isf: BgDelta, cr: Double): BgDelta =
    BgDelta.fromMgDl((carbs / cr * isf.mgdl).toInt())

inline fun convertToInsulinAmountFromCarbs(carbs: Double, cr: Double): InsulinAmount =
    InsulinAmount(carbs / cr)

inline fun convertToCarbsFromUnits(amount: InsulinAmount, cr: Double): Double =
    amount.iu * cr