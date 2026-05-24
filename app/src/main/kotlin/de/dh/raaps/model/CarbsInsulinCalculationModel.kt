package de.dh.raaps.model

import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp


/**
 * Calculation core for carbs and insulin calculations, based on pre-sampled activity functions.
 * All values are cumulated for a whole interval or are the average for an interval.
 * Note that the accuracy of the calculation is +/- 1 interval for each meal and insulin application.
 */
class CarbsInsulinCalculationModel(
    val intervalSize: Minutes
) {
    val carbsCalculationCache: SampledCarbsCalculationCache = SampledCarbsCalculationCache(intervalSize)
    val insulinCalculationCache: SampledInsulinCalculationCache = SampledInsulinCalculationCache(intervalSize)

    /**
     * Calculates the total COB which is expected as result of the given meal consumptions
     * in the interval at the given timestamp.
     * @return COB in grams.
     */
    fun totalCob(
        meals: List<MealEntry>,
        timestamp: Timestamp
    ): Double {
        return meals.sumOf { meal ->
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
     * @return Effective insulin in Units / interval
     */
    fun effectiveInsulin(
        insulinApplications: List<InsulinApplication>,
        timestamp: Timestamp
    ): Double {
        return insulinApplications.sumOf { entry ->
            val intervalsSinceApplication =
                Minutes.timeDifference(entry.timestamp, timestamp).value / intervalSize.value

            insulinCalculationCache.effectiveInsulin(
                insulinUnits = entry.insulinUnits,
                insulinType = entry.insulinType,
                intervalsSinceApplication = intervalsSinceApplication
            )
        }
    }

    /**
     * Calculates the amount of Insulin On Board (IOB) which is expected as result of the given
     * insulin applications during the interval of the given timestamp.
     * @return IOB in Units
     */
    fun iob(
        insulinEntries: List<InsulinApplication>,
        timestamp: Timestamp
    ): Double {
        return insulinEntries.sumOf { entry ->
            val intervalsSinceApplication =
                Minutes.timeDifference(entry.timestamp, timestamp).value / intervalSize.value

            insulinCalculationCache.remainingInsulin(
                insulinUnits = entry.insulinUnits,
                insulinType = entry.insulinType,
                intervalsSinceApplication = intervalsSinceApplication
            )
        }
    }

    fun remainingInsulin(
        insulinApplication: InsulinApplication,
        timestamp: Timestamp
    ): Double {
        val intervalsSinceApplication =
            Minutes.timeDifference(insulinApplication.timestamp, timestamp).value / intervalSize.value
        return insulinCalculationCache.remainingInsulin(
            insulinApplication.insulinUnits,
            insulinApplication.insulinType,
            intervalsSinceApplication = intervalsSinceApplication
        )
    }

    fun spentInsulin(
        insulinUnits: Double,
        insulinType: InsulinType,
        insulinApplicationTimestamp: Timestamp,
        timestamp: Timestamp
    ): Double {
        val intervalsSinceApplication =
            Minutes.timeDifference(insulinApplicationTimestamp, timestamp).value / intervalSize.value
        return insulinCalculationCache.spentInsulin(
            insulinUnits = insulinUnits,
            insulinType = insulinType,
            intervalsSinceApplication = intervalsSinceApplication
        )
    }

    /**
     * Calculates the blood glucose impact for an interval which is expected as result of the given
     * insulin applications.
     * Unit: Depending on isf unit, mg/dL or mmol/L
     */
    fun bgi(
        insulinEntries: List<InsulinApplication>,
        isf: Double,
        timestamp: Timestamp
    ): Double {
        val effectiveInsulin = effectiveInsulin(
            insulinApplications = insulinEntries,
            timestamp
        )

        return -effectiveInsulin * isf
    }
}