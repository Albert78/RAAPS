package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import java.util.UUID
import kotlin.math.abs

/**
 * Curve parameters for a carbs component activity curve.
 * For calculations, we model the carbs activity of a meal as the sum of multiple
 * component activity curves.
 */
data class CarbCurveComponentData(
    /**
     * Weight of this component, the sum of all weights for a meal must be 1.
     */
    val weight: Int,

    /**
     * The peak of the carb activity for this component.
     */
    val peakMinutes: Minutes,
) {
    init {
        require(weight >= 0.0) { "weight must be >= 0" }
    }
}

/**
 * Represents a meal absorption profile.
 */
data class MealType(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val components: List<CarbCurveComponentData>,

    /**
     * Carbs absorption time; maximum duration when the carbs are completely absorbed.
     */
    val cat: Minutes
) {
    init {
        require(components.isNotEmpty()) {
            "MealType must contain at least one component"
        }

        val sum = components.sumOf { it.weight }
        require(abs(sum - 1.0) < 1e-6) {
            "Component weights must sum to 1.0"
        }
    }
}

/**
 * Common meal profiles.
 *
 * These are deliberately approximate and intended as practical defaults. The system/user can provide
 * its own/adapted set of meal types, if needed.
 */
object MealTypes {
    /**
     * Juice, dextrose, fast sugar.
     * Peak ~25 min.
     */
    val FAST_CARBS = MealType(
        name = "Fast Carbs",
        components = listOf(
            CarbCurveComponentData(
                weight = 100,
                peakMinutes = Minutes(25)
            )
        ),
        cat = Minutes(90)
    )

    /**
     * Bread, rice, pasta, normal mixed meal.
     */
    val STANDARD_MEAL = MealType(
        name = "Standard Meal",
        components = listOf(
            CarbCurveComponentData(
                weight = 70,
                peakMinutes = Minutes(75)
            ),
            CarbCurveComponentData(
                weight = 30,
                peakMinutes = Minutes(150)
            )
        ),
        cat = Minutes.ofHours(4)
    )

    /**
     * Pizza, burger, fatty food.
     */
    val HIGH_FAT_MEAL = MealType(
        name = "High Fat Meal",
        components = listOf(
            CarbCurveComponentData(
                weight = 35,
                peakMinutes = Minutes(60)
            ),
            CarbCurveComponentData(
                weight = 65,
                peakMinutes = Minutes(240)
            )
        ),
        cat = Minutes.ofHours(6)
    )

    /**
     * Protein/fat dominant, low GI.
     */
    val SLOW_MEAL = MealType(
        name = "Slow Meal",
        components = listOf(
            CarbCurveComponentData(
                weight = 40,
                peakMinutes = Minutes(120)
            ),
            CarbCurveComponentData(
                weight = 60,
                peakMinutes = Minutes(300)
            )
        ),
        cat = Minutes.ofHours(8)
    )

    val DEFAULTS = listOf(
        FAST_CARBS,
        STANDARD_MEAL,
        HIGH_FAT_MEAL,
        SLOW_MEAL
    )
}

/**
 * A historical meal event.
 */
data class MealEntry(
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val carbGrams: Double,
    val mealType: MealType
)