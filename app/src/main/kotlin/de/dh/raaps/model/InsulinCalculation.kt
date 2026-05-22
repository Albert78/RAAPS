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
 * in the duration of insulin action (DIA) for an insulin and caches the calculation vector to avoid
 * repeating the expensive calculations.
 */
class SampledInsulinCalculationCache(
    val sampleIntervalSize: Minutes,
    val insulinType: InsulinType
) {
    /**
     * Cached sampled, normalized effective insulin values for each interval.
     * Each element represents the activity rate for a [sampleIntervalSize] duration,
     * starting from the time of insulin application.
     * Unit: fraction of total insulin which is effective per interval.
     */
    val intervalEffectiveInsulin = DoubleArray(insulinType.dia.value / sampleIntervalSize.value)

    /**
     * Cached sampled cumulative remaining insulin fraction values.
     * Each element contains the average remaining fraction (0.0 to 1.0) up to that interval.
     */
    val remainingFractionSamples = DoubleArray(insulinType.dia.value / sampleIntervalSize.value)

    init {
        recalculate()
    }

    fun recalculate() {
        val curve = InsulinCurve(insulinType.dia.value.toDouble(), insulinType.peak.value.toDouble())
        for (index in intervalEffectiveInsulin.indices) {
            val intervalStartTime = (index * sampleIntervalSize.value).toDouble()
            var activitySum = 0.0
            for (i in 0..<sampleIntervalSize.value) {
                activitySum += curve.normalizedActivity(intervalStartTime + i)
            }
            intervalEffectiveInsulin[index] = activitySum
            remainingFractionSamples[index] = curve.remainingFraction(intervalStartTime)
        }
    }

    /**
     * Current insulin activity for the given insulin application.
     * Unit: Unit / interval
     */
    fun effectiveInsulin(
        insulinUnits: Double,
        intervalsSinceApplication: Int
    ): Double {
        if (intervalsSinceApplication <= 0.0) return 0.0
        if (intervalsSinceApplication >= intervalEffectiveInsulin.size) return 0.0
        return insulinUnits * intervalEffectiveInsulin[intervalsSinceApplication]
    }

    /**
     * Currently remaining fraction of insulin on board for the given insulin application.
     * Unit: Units
     */
    fun remainingInsulin(
        insulinUnits: Double,
        intervalsSinceApplication: Int
    ): Double {
        if (intervalsSinceApplication <= 0.0) return 0.0
        if (intervalsSinceApplication >= remainingFractionSamples.size) return 0.0
        return insulinUnits * remainingFractionSamples[intervalsSinceApplication]
    }
}