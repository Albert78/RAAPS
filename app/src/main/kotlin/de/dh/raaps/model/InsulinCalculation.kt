package de.dh.raaps.model

import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.Minutes
import kotlin.math.exp

/**
 * One gamma-shaped insulin activity curve.
 *
 * Mathematical form:
 *
 * k(t) = t^alpha / (Gamma(alpha + 1) * beta^(alpha + 1)) * exp(-t / beta)
 *
 * where:
 * - alpha controls curve shape
 * - beta controls time scale
 *
 * Peak time:
 *
 * peak = alpha * beta
 *
 * The curve is normalized:
 *
 * integral(k(t), 0..∞) = 1
 *
 * Therefore:
 *
 * insulinUnits * k(t)
 *
 * directly yields the insulin activity at time t.
 *
 * Alpha is intentionally fixed to 2.0 for stability and
 * a closed-form CDF.
 */
data class InsulinCurve(
    val diaMinutes: Double,
    val peakMinutes: Double
) {

    init {
        require(diaMinutes > 0.0)
        require(peakMinutes > 0.0)
        require(peakMinutes < diaMinutes)
    }

    private val alpha = 2.0
    private val beta = peakMinutes / alpha

    /**
     * Normalized insulin activity.
     * Unit: 1/min
     */
    fun normalizedActivity(timeSinceApplicationMinutes: Double): Double {
        if (timeSinceApplicationMinutes <= 0.0) return 0.0
        if (timeSinceApplicationMinutes >= diaMinutes) return 0.0

        val t = timeSinceApplicationMinutes

        return (t * t) /
                (2.0 * beta * beta * beta) *
                exp(-t / beta)
    }

    /**
     * Fraction already spent in [0,1].
     */
    fun spentFraction(timeSinceApplicationMinutes: Double): Double {
        if (timeSinceApplicationMinutes <= 0.0) return 0.0
        if (timeSinceApplicationMinutes >= diaMinutes) return 1.0

        val x = timeSinceApplicationMinutes / beta

        val cdf = 1.0 - exp(-x) * (1.0 + x + 0.5 * x * x)

        val diaCdf = 1.0 - exp(-diaMinutes / beta) * (
                1.0 + diaMinutes / beta +
                        0.5 * (diaMinutes / beta) * (diaMinutes / beta)
                )

        return (cdf / diaCdf).coerceIn(0.0, 1.0)
    }

    /**
     * Remaining active fraction (IOB fraction).
     */
    fun remainingFraction(timeSinceApplicationMinutes: Double): Double {
        return (1.0 - spentFraction(timeSinceApplicationMinutes))
            .coerceIn(0.0, 1.0)
    }
}

/**
 * Samples the insulin activity function for all discrete time intervals
 * in the duration of insulin action (DIA) for different insulin types and caches the calculation
 * vectors to avoid repeating the expensive calculations.
 */
class SampledInsulinCalculationCache(
    val sampleIntervalSize: Minutes
) {
    /**
     * Cached sampled cumulative remaining insulin fraction values at each interval start, per insulin type.
     * The length of the arrays are different and depend on the declared dia time for each insulin type.
     */
    val remainingFractionSamples: MutableMap<InsulinType, DoubleArray> = mutableMapOf()

    /**
     * Samples the remaining fraction of insulin for the given insulin type to an array of activity values per interval.
     */
    private fun sampleInsulinAbsorbedFraction(insulinType: InsulinType): DoubleArray {
        val numSamples = insulinType.dia.value / sampleIntervalSize.value
        return DoubleArray(numSamples) { index ->
            val intervalStartTime = index * sampleIntervalSize.value
            val curve = InsulinCurve(insulinType.dia.value.toDouble(), insulinType.peak.value.toDouble())
            curve.remainingFraction(intervalStartTime.toDouble())
        }
    }

    private fun getOrCreateSampledInsulinRemainingFraction(insulinType: InsulinType): DoubleArray {
        return remainingFractionSamples.computeIfAbsent(
            insulinType,
            { insulinType -> sampleInsulinAbsorbedFraction(insulinType) }
        )
    }

    /**
     * Clears all cached samples.
     */
    fun clearCache() {
        remainingFractionSamples.clear()
    }

    /**
     * Removes the cached data for the given insulin type.
     */
    fun dropMealType(insulinType: InsulinType) {
        remainingFractionSamples.remove(insulinType)
    }

    /**
     * Pre-calculates and caches the remaining fraction samples for an insulin type.
     * @param forceRefresh If true, existing cached values will be removed and recalculated.
     */
    fun calculateForInsulinType(insulinType: InsulinType, forceRefresh: Boolean = false) {
        if (forceRefresh) {
            dropMealType(insulinType)
        }
        getOrCreateSampledInsulinRemainingFraction(insulinType)
    }

    /**
     * Pre-calculates and caches the remaining fraction samples for the given insulin types.
     * @param forceRefresh If true, existing cached values will be removed and recalculated.
     */
    fun calculateForMealTypes(insulinTypes: Set<InsulinType>, forceRefresh: Boolean = false) {
        for (insulinType in insulinTypes) {
            calculateForInsulinType(insulinType, forceRefresh)
        }
    }

    /**
     * Current insulin activity for the given insulin application.
     * Unit: Unit / interval
     */
    fun effectiveInsulin(
        insulinUnits: Double,
        insulinType: InsulinType,
        intervalsSinceApplication: Int
    ): Double {
        if (intervalsSinceApplication <= 0.0) return 0.0
        val samples = getOrCreateSampledInsulinRemainingFraction(insulinType)
        if (intervalsSinceApplication >= samples.size - 1) return 0.0
        val remainingFractionAtIntervalStart = samples[intervalsSinceApplication]
        val remainingFractionAtIntervalEnd = samples[intervalsSinceApplication + 1]
        return insulinUnits * (remainingFractionAtIntervalEnd - remainingFractionAtIntervalStart)
    }

    /**
     * Currently remaining fraction of insulin on board for the given insulin application.
     * Unit: Units
     */
    fun remainingInsulin(
        insulinUnits: Double,
        insulinType: InsulinType,
        intervalsSinceApplication: Int
    ): Double {
        if (intervalsSinceApplication <= 0.0) return 0.0
        val samples = getOrCreateSampledInsulinRemainingFraction(insulinType)
        if (intervalsSinceApplication >= samples.size - 1) return 0.0
        val remainingFractionAtIntervalStart = samples[intervalsSinceApplication]
        val remainingFractionAtIntervalEnd = samples[intervalsSinceApplication + 1]
        return insulinUnits * (remainingFractionAtIntervalEnd + remainingFractionAtIntervalStart) / 2.0
    }
}