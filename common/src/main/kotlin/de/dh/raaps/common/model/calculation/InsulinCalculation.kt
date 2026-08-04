package de.dh.raaps.common.model.calculation

import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.Minutes
import java.util.concurrent.ConcurrentHashMap
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
 * amount * k(t)
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
    private val betaFactor = 2.0 * beta * beta * beta
    private val diaCdf = 1.0 - exp(-diaMinutes / beta) * (1.0 + diaMinutes / beta
            + 0.5 * (diaMinutes / beta) * (diaMinutes / beta))


    /**
     * Normalized insulin activity.
     * Unit: 1/min
     */
    fun normalizedActivity(timeSinceApplicationMinutes: Double): Double {
        if (timeSinceApplicationMinutes <= 0.0) return 0.0
        if (timeSinceApplicationMinutes >= diaMinutes) return 0.0

        val t = timeSinceApplicationMinutes
        return (t * t) / betaFactor * exp(-t / beta)
    }

    /**
     * Fraction already spent in [0,1].
     */
    fun spentFraction(timeSinceApplicationMinutes: Double): Double {
        if (timeSinceApplicationMinutes <= 0.0) return 0.0
        if (timeSinceApplicationMinutes >= diaMinutes) return 1.0

        val x = timeSinceApplicationMinutes / beta
        val cdf = 1.0 - exp(-x) * (1.0 + x + 0.5 * x * x)
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
 * Key for caching sampled insulin activity function values.
 */
data class InsulinActionKey(
    val dia: Minutes,
    val peak: Minutes
)

/**
 * Samples the insulin activity function for all discrete time intervals
 * in the duration of insulin action (DIA) for different insulin types and caches the calculation
 * vectors to avoid repeating the expensive calculations.
 */
class SampledInsulinCalculationCache(
    val sampleIntervalSize: Minutes
) {
    /**
     * Cached sampled cumulative remaining insulin fraction values at each interval start, per action profile.
     * The length of the arrays are different and depend on the declared dia time.
     */
    val remainingFractionSamples: MutableMap<InsulinActionKey, DoubleArray> = ConcurrentHashMap()

    /**
     * Samples the remaining fraction of insulin for the given insulin type to an array of activity values per interval.
     */
    private fun sampleInsulinAbsorbedFraction(dia: Minutes, peak: Minutes): DoubleArray {
        val numSamples = dia.value / sampleIntervalSize.value
        return DoubleArray(numSamples) { index ->
            val intervalStartTime = index * sampleIntervalSize.value
            val curve = InsulinCurve(
                dia.value.toDouble(),
                peak.value.toDouble()
            )
            curve.remainingFraction(intervalStartTime.toDouble())
        }
    }

    private fun getOrCreateSampledInsulinRemainingFraction(dia: Minutes, peak: Minutes): DoubleArray {
        val key = InsulinActionKey(dia, peak)
        return remainingFractionSamples.computeIfAbsent(
            key,
            { _ -> sampleInsulinAbsorbedFraction(dia, peak) }
        )
    }

    /**
     * Clears all cached samples.
     */
    fun clearCache() {
        remainingFractionSamples.clear()
    }

    /**
     * Removes the cached data for the given action profile.
     */
    fun dropActionProfile(dia: Minutes, peak: Minutes) {
        remainingFractionSamples.remove(InsulinActionKey(dia, peak))
    }

    /**
     * Pre-calculates and caches the remaining fraction samples for an action profile.
     * @param forceRefresh If true, existing cached values will be removed and recalculated.
     */
    fun calculateForActionProfile(dia: Minutes, peak: Minutes, forceRefresh: Boolean = false) {
        if (forceRefresh) {
            dropActionProfile(dia, peak)
        }
        getOrCreateSampledInsulinRemainingFraction(dia, peak)
    }

    /**
     * Current insulin activity for the given insulin application.
     * Unit: Unit / interval
     */
    fun effectiveInsulin(
        amount: Double,
        dia: Minutes,
        peak: Minutes,
        intervalsSinceApplication: Int
    ): Double {
        if (intervalsSinceApplication <= 0.0) return 0.0
        val samples = getOrCreateSampledInsulinRemainingFraction(dia, peak)
        if (intervalsSinceApplication >= samples.size - 1) return 0.0
        val remainingFractionAtIntervalStart = samples[intervalsSinceApplication]
        val remainingFractionAtIntervalEnd = samples[intervalsSinceApplication + 1]
        return amount * (remainingFractionAtIntervalStart - remainingFractionAtIntervalEnd)
    }

    /**
     * Currently remaining fraction of insulin on board for the given insulin application.
     * Unit: Units
     */
    fun remainingInsulin(
        amount: Double,
        dia: Minutes,
        peak: Minutes,
        intervalsSinceApplication: Int
    ): Double {
        if (intervalsSinceApplication < 0) return 0.0
        val samples = getOrCreateSampledInsulinRemainingFraction(dia, peak)
        if (intervalsSinceApplication >= samples.size) return 0.0
        return amount * samples[intervalsSinceApplication]
    }

    fun spentInsulin(
        amount: Double,
        dia: Minutes,
        peak: Minutes,
        intervalsSinceApplication: Int
    ): Double {
        return amount - remainingInsulin(
            amount = amount,
            dia = dia,
            peak = peak,
            intervalsSinceApplication = intervalsSinceApplication
        )
    }
}