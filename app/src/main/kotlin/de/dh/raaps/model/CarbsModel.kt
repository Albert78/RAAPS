package de.dh.raaps.model

import kotlin.math.abs
import kotlin.math.exp

/**
 * One gamma-shaped meal absorption component.
 *
 * Mathematical form:
 *
 * g(t) = t^alpha / (Gamma(alpha + 1) * beta^(alpha + 1)) * exp(-t / beta)
 *
 * where:
 *
 * - alpha controls the shape of the curve
 *   - larger alpha => later, wider, more symmetric peak
 *   - smaller alpha => earlier, sharper rise
 *
 * - beta is the time scale parameter in minutes
 *   - larger beta => slower and wider absorption
 *   - smaller beta => faster absorption
 *
 * Peak time is:
 *
 * peak = alpha * beta
 *
 * This implementation intentionally fixes alpha = 2.0 because it gives
 * physiologically plausible meal curves, keeps the API simple, and allows
 * a closed-form CDF for efficient COB calculation.
 *
 * Therefore:
 *
 * beta = peakMinutes / alpha
 */
data class CarbCurveComponent(
    val weight: Double,
    val peakMinutes: Double
) {
    init {
        require(weight >= 0.0) { "weight must be >= 0" }
        require(peakMinutes > 0.0) { "peakMinutes must be > 0" }
    }

    private val alpha = 2.0
    private val beta = peakMinutes / alpha

    /**
     * Normalized absorption activity at [timeSinceMealMinutes].
     * Unit: 1 / minute
     * Integral over [0, inf] = 1
     */
    fun normalizedActivity(timeSinceMealMinutes: Double): Double {
        if (timeSinceMealMinutes <= 0.0) return 0.0

        val t = timeSinceMealMinutes

        // Gamma(alpha=2) normalized PDF (Probability Density Function, here: Current absorption rate):
        // g(t) = t^2 / (2 * beta^3) * exp(-t / beta)
        return (t * t) /
                (2.0 * beta * beta * beta) *
                exp(-t / beta)
    }

    /**
     * Cumulative absorbed fraction in [0,1].
     * Closed form CDF (Cumulative Distribution Function) for alpha=2.
     */
    fun absorbedFraction(timeSinceMealMinutes: Double): Double {
        if (timeSinceMealMinutes <= 0.0) return 0.0

        val t = timeSinceMealMinutes
        val x = t / beta

        // CDF for Gamma(k=3, theta=beta)
        val cdf = 1.0 - exp(-x) * (1.0 + x + 0.5 * x * x)

        return cdf.coerceIn(0.0, 1.0)
    }
}

/**
 * Represents a meal absorption profile.
 */
data class MealType(
    val name: String,
    val components: List<CarbCurveComponent>
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

    /**
     * Current carb absorption rate for this meal type for a given time.
     * @return Carbs absorption in grams / minute for given carb amount.
     */
    fun carbAbsorption(
        carbGrams: Double,
        timeSinceMealMinutes: Double
    ): Double {
        if (timeSinceMealMinutes <= 0.0) return 0.0

        return carbGrams * components.sumOf { component ->
            component.weight *
                    component.normalizedActivity(timeSinceMealMinutes)
        }
    }

    /**
     * Remaining carbs on board for this meal type for a given time.
     * @return COB in grams.
     */
    fun cob(
        carbGrams: Double,
        timeSinceMealMinutes: Double
    ): Double {
        if (timeSinceMealMinutes <= 0.0) return carbGrams

        val absorbedFraction = components.sumOf { component ->
            component.weight *
                    component.absorbedFraction(timeSinceMealMinutes)
        }

        return (carbGrams * (1.0 - absorbedFraction))
            .coerceAtLeast(0.0)
    }
}

/**
 * Common meal profiles.
 *
 * These are deliberately approximate and intended as practical defaults.
 */
object MealTypes {
    /**
     * Juice, dextrose, fast sugar.
     * Peak ~25 min.
     */
    val FAST_CARBS = MealType(
        name = "Fast Carbs",
        components = listOf(
            CarbCurveComponent(
                weight = 1.0,
                peakMinutes = 25.0
            )
        )
    )

    /**
     * Bread, rice, pasta, normal mixed meal.
     */
    val STANDARD_MEAL = MealType(
        name = "Standard Meal",
        components = listOf(
            CarbCurveComponent(
                weight = 0.70,
                peakMinutes = 75.0
            ),
            CarbCurveComponent(
                weight = 0.30,
                peakMinutes = 150.0
            )
        )
    )

    /**
     * Pizza, burger, fatty food.
     */
    val HIGH_FAT_MEAL = MealType(
        name = "High Fat Meal",
        components = listOf(
            CarbCurveComponent(
                weight = 0.35,
                peakMinutes = 60.0
            ),
            CarbCurveComponent(
                weight = 0.65,
                peakMinutes = 240.0
            )
        )
    )

    /**
     * Protein/fat dominant, low GI.
     */
    val SLOW_MEAL = MealType(
        name = "Slow Meal",
        components = listOf(
            CarbCurveComponent(
                weight = 0.40,
                peakMinutes = 120.0
            ),
            CarbCurveComponent(
                weight = 0.60,
                peakMinutes = 300.0
            )
        )
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
    val timestampMillis: Long,
    val carbGrams: Double,
    val mealType: MealType
)

/**
 * Aggregates meal history.
 */
object CarbModel {
    /**
     * Returns the total COB at timeMillis which is expected as result of the given meal treatments.
     * @return COB in grams.
     */
    fun totalCob(
        meals: List<MealEntry>,
        timeMillis: Long
    ): Double {
        return meals.sumOf { meal ->
            val deltaMinutes =
                (timeMillis - meal.timestampMillis) / 60_000.0

            meal.mealType.cob(
                carbGrams = meal.carbGrams,
                timeSinceMealMinutes = deltaMinutes
            )
        }
    }

    /**
     * Total carb absorption rate at timeMillis which is expected as result of the given meal treatments.
     * @return Absorption rate in grams / minute.
     */
    fun totalCarbAbsorption(
        meals: List<MealEntry>,
        timeMillis: Long
    ): Double {
        return meals.sumOf { meal ->
            val deltaMinutes =
                (timeMillis - meal.timestampMillis) / 60_000.0

            meal.mealType.carbAbsorption(
                carbGrams = meal.carbGrams,
                timeSinceMealMinutes = deltaMinutes
            )
        }
    }
}