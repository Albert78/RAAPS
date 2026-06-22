package de.dh.raaps.common.model.calculation

import de.dh.raaps.common.model.Bolus
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
    val carbsCalculationCache: SampledCarbsCalculationCache =
        SampledCarbsCalculationCache(intervalSize)
    val insulinCalculationCache: SampledInsulinCalculationCache =
        SampledInsulinCalculationCache(intervalSize)

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
        boluses: List<Bolus>,
        timestamp: Timestamp
    ): Double {
        return boluses.sumOf { entry ->
            val intervalsSinceApplication =
                Minutes.timeDifference(entry.timestamp, timestamp).value / intervalSize.value

            insulinCalculationCache.effectiveInsulin(
                amount = entry.amount,
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
        bolusEntries: List<Bolus>,
        timestamp: Timestamp
    ): Double {
        return bolusEntries.sumOf { entry ->
            val intervalsSinceApplication =
                Minutes.timeDifference(entry.timestamp, timestamp).value / intervalSize.value

            insulinCalculationCache.remainingInsulin(
                amount = entry.amount,
                insulinType = entry.insulinType,
                intervalsSinceApplication = intervalsSinceApplication
            )
        }
    }

    fun remainingInsulin(
        bolus: Bolus,
        timestamp: Timestamp
    ): Double {
        val intervalsSinceApplication =
            Minutes.timeDifference(bolus.timestamp, timestamp).value / intervalSize.value
        return insulinCalculationCache.remainingInsulin(
            bolus.amount,
            bolus.insulinType,
            intervalsSinceApplication = intervalsSinceApplication
        )
    }

    fun spentInsulin(
        amount: Double,
        insulinType: InsulinType,
        applicationTimestamp: Timestamp,
        timestamp: Timestamp
    ): Double {
        val intervalsSinceApplication =
            Minutes.timeDifference(applicationTimestamp, timestamp).value / intervalSize.value
        return insulinCalculationCache.spentInsulin(
            amount = amount,
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
        bolusEntries: List<Bolus>,
        isf: Double,
        timestamp: Timestamp
    ): Double {
        val effectiveInsulin = effectiveInsulin(
            boluses = bolusEntries,
            timestamp
        )

        return -effectiveInsulin * isf
    }
}