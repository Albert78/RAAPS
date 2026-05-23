package de.dh.raaps.model

import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
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
    val peakMinutes: Double
) {
    init {
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
 * Samples the carbs absorbed fraction functions for all discrete time intervals
 * in the carbs absorption time for different meal types and caches the calculation vectors to avoid repeating the
 * expensive calculations.
 */
class SampledCarbsCalculationCache(
    val sampleIntervalSize: Minutes
) {
    /**
     * Cached sampled cumulative absorbed fraction values at each interval start, per meal type.
     * The length of the arrays are different and depend on the declared carbs absorption time
     * for each meal type.
     */
    val absorbedFractionSamples: MutableMap<MealType, DoubleArray> = mutableMapOf()

    /**
     * Samples the absorbed fraction of carbs for the given meal type to an array of activity values per interval.
     */
    private fun sampleCarbsAbsorbedFraction(mealType: MealType): DoubleArray {
        val numSamples = mealType.cat.value / sampleIntervalSize.value
        return DoubleArray(numSamples) { index ->
            val intervalStartTime = index * sampleIntervalSize.value
            var result = 0.0
            for (cccd in mealType.components) {
                val ccc = CarbCurveComponent(cccd.peakMinutes.value.toDouble())
                result += ccc.absorbedFraction(intervalStartTime.toDouble()) * cccd.weight
            }
            result
        }
    }

    private fun getOrCreateSampledCarbsAbsorbedFraction(mealType: MealType): DoubleArray {
        return absorbedFractionSamples.computeIfAbsent(
            mealType,
            { mealType -> sampleCarbsAbsorbedFraction(mealType) }
        )
    }

    /**
     * Clears all cached samples.
     */
    fun clearCache() {
        absorbedFractionSamples.clear()
    }

    /**
     * Removes the cached data for the given meal type.
     */
    fun dropMealType(mealType: MealType) {
        absorbedFractionSamples.remove(mealType)
    }

    /**
     * Pre-calculates and caches the absorbed fraction samples for a meal type.
     * @param forceRefresh If true, existing cached values will be removed and recalculated.
     */
    fun calculateForMealType(mealType: MealType, forceRefresh: Boolean = false) {
        if (forceRefresh) {
            dropMealType(mealType)
        }
        getOrCreateSampledCarbsAbsorbedFraction(mealType)
    }

    /**
     * Pre-calculates and caches the absorbed fraction samples for the given meal types.
     * @param forceRefresh If true, existing cached values will be removed and recalculated.
     */
    fun calculateForMealTypes(mealTypes: Set<MealType>, forceRefresh: Boolean = false) {
        for (mealType in mealTypes) {
            calculateForMealType(mealType, forceRefresh)
        }
    }

    /**
     * Current carb absorption rate for the given meal.
     * @return Carbs absorption in grams / interval for given meal.
     */
    fun carbAbsorption(
        carbGrams: Double,
        mealType: MealType,
        intervalsSinceMeal: Int
    ): Double {
        if (intervalsSinceMeal <= 0.0) return 0.0

        val samples = getOrCreateSampledCarbsAbsorbedFraction(mealType)
        if (intervalsSinceMeal >= samples.size - 1) return 0.0
        val absorbedFractionAtIntervalStart = samples[intervalsSinceMeal]
        val absorbedFractionAtIntervalEnd = samples[intervalsSinceMeal + 1]
        return carbGrams * (absorbedFractionAtIntervalEnd - absorbedFractionAtIntervalStart)
    }

    /**
     * Currently remaining carbs on board for the given meal.
     * @return COB in grams.
     */
    fun remainingCarbs(
        carbGrams: Double,
        mealType: MealType,
        intervalsSinceMeal: Int
    ): Double {
        if (intervalsSinceMeal <= 0.0) return 0.0

        val samples = getOrCreateSampledCarbsAbsorbedFraction(mealType)
        if (intervalsSinceMeal >= samples.size - 1) return 0.0
        val absorbedFractionAtIntervalStart = samples[intervalsSinceMeal]
        val absorbedFractionAtIntervalEnd = samples[intervalsSinceMeal + 1]
        return carbGrams * (absorbedFractionAtIntervalStart + absorbedFractionAtIntervalEnd) / 2.0
    }
}