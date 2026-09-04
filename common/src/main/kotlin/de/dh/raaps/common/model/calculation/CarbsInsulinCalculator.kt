package de.dh.raaps.common.model.calculation

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinStatus
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.convertToBgDeltaFromUnits
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp


/**
 * Calculation core for carbs and insulin calculations, based on pre-sampled activity functions.
 * All values are cumulated for a whole interval or are the average for an interval.
 * Note that the accuracy of the calculation is +/- 1 interval for each meal and insulin application.
 */
class CarbsInsulinCalculator(
    val intervalSize: Minutes
) {
    val carbsCalculationCache: SampledCarbsCalculationCache =
        SampledCarbsCalculationCache(intervalSize)
    val insulinCalculationCache: SampledInsulinCalculationCache =
        SampledInsulinCalculationCache(intervalSize)

    /**
     * Calculates the total COB (Carbs On Board) at the given [timestamp].
     *
     * @param meals The list of all relevant meals.
     * @param timestamp The point in time for which the COB is calculated.
     * @param includeFutureMeals Explicitly defines how to treat meals occurring after [timestamp].
     * - If **false**: Future meals are ignored (standard for realistic projections).
     * - If **true**: Future meals are added with their full amount.
     * This parameter is intentionally mandatory to force the caller to decide whether upcoming
     * planned meals should be part of the current calculation context.
     * @return COB in grams.
     */
    fun cob(
        meals: List<MealEntry>,
        timestamp: Timestamp,
        includeFutureMeals: Boolean
    ): Double {
        return meals.sumOf { meal ->
            if (meal.timestamp > timestamp) {
                if (includeFutureMeals)
                    return@sumOf meal.carbGrams
                else
                    return@sumOf 0.0
            }
            val intervalsSinceMeal =
                Minutes.timeDifference(meal.timestamp, timestamp).value / intervalSize.value

            carbsCalculationCache.remainingCarbs(
                carbGrams = meal.carbGrams,
                mealType = meal.mealType,
                intervalsSinceMeal = intervalsSinceMeal
            )
        }
    }

    /**
     * Calculates the total carb absorption rate which is expected as result of the given meal
     * consumptions in the interval at the given timestamp.
     * @return Absorption rate in grams / interval.
     */
    fun carbAbsorption(
        meals: List<MealEntry>,
        timestamp: Timestamp
    ): Double {
        return meals.sumOf { meal ->
            val intervalsSinceMeal =
                Minutes.timeDifference(meal.timestamp, timestamp).value / intervalSize.value

            carbsCalculationCache.carbAbsorption(
                carbGrams = meal.carbGrams,
                mealType = meal.mealType,
                intervalsSinceMeal = intervalsSinceMeal
            )
        }
    }

    /**
     * Calculates the effective insulin amount which is expected as result of the given insulin applications
     * during the interval of the given timestamp.
     * @return Effective insulin in [InsulinAmount] / interval
     */
    fun effectiveInsulin(
        insulinApplications: List<InsulinApplication>,
        timestamp: Timestamp,
        dia: Minutes,
        peak: Minutes
    ): InsulinAmount {
        var total = InsulinAmount.ZERO
        insulinApplications.forEach { entry ->
            if (entry.status == InsulinStatus.Cancelled) {
                return@forEach
            }
            val intervalsSinceApplication =
                Minutes.timeDifference(entry.timestamp, timestamp).value / intervalSize.value

            total += insulinCalculationCache.effectiveInsulin(
                amount = entry.amount,
                dia = dia,
                peak = peak,
                intervalsSinceApplication = intervalsSinceApplication
            )
        }
        return total
    }

    /**
     * Calculates the amount of Insulin On Board (IOB) which is expected as result of the given
     * insulin applications during the interval of the given timestamp.
     * @return IOB in [InsulinAmount]
     */
    fun iob(
        insulinApplications: List<InsulinApplication>,
        timestamp: Timestamp,
        dia: Minutes,
        peak: Minutes,
        excludeBasal: Boolean = false
    ): InsulinAmount {
        var total = InsulinAmount.ZERO
        insulinApplications.forEach { entry ->
            if (entry.status == InsulinStatus.Cancelled) {
                return@forEach
            }
            if (excludeBasal && entry.basal) {
                return@forEach
            }
            val intervalsSinceApplication =
                Minutes.timeDifference(entry.timestamp, timestamp).value / intervalSize.value

            total += insulinCalculationCache.remainingInsulin(
                amount = entry.amount,
                dia = dia,
                peak = peak,
                intervalsSinceApplication = intervalsSinceApplication
            )
        }
        return total
    }

    fun remainingInsulin(
        insulinApplication: InsulinApplication,
        timestamp: Timestamp,
        dia: Minutes,
        peak: Minutes
    ): InsulinAmount {
        val intervalsSinceApplication =
            Minutes.timeDifference(insulinApplication.timestamp, timestamp).value / intervalSize.value
        return insulinCalculationCache.remainingInsulin(
            insulinApplication.amount,
            dia = dia,
            peak = peak,
            intervalsSinceApplication = intervalsSinceApplication
        )
    }

    fun spentInsulin(
        amount: InsulinAmount,
        applicationTimestamp: Timestamp,
        timestamp: Timestamp,
        dia: Minutes,
        peak: Minutes
    ): InsulinAmount {
        val intervalsSinceApplication =
            Minutes.timeDifference(applicationTimestamp, timestamp).value / intervalSize.value
        return insulinCalculationCache.spentInsulin(
            amount = amount,
            dia = dia,
            peak = peak,
            intervalsSinceApplication = intervalsSinceApplication
        )
    }

    /**
     * Calculates the blood glucose impact for an interval which is expected as result of the given
     * insulin applications.
     */
    fun bgi(
        insulinApplications: List<InsulinApplication>,
        isf: BgDelta,
        timestamp: Timestamp,
        dia: Minutes,
        peak: Minutes
    ): BgDelta {
        val effectiveInsulin = effectiveInsulin(
            insulinApplications = insulinApplications,
            timestamp = timestamp,
            dia = dia,
            peak = peak
        )

        return -convertToBgDeltaFromUnits(effectiveInsulin, isf)
    }
}