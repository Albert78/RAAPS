package de.dh.raaps.model.test

import de.dh.raaps.model.CarbModel
import de.dh.raaps.model.MealEntry
import de.dh.raaps.model.MealTypes


fun main() {
    val now = System.currentTimeMillis()

    val meals = listOf(
        MealEntry(
            timestampMillis = now - 45 * 60_000,
            carbGrams = 50.0,
            mealType = MealTypes.STANDARD_MEAL
        ),
        MealEntry(
            timestampMillis = now - 2 * 60 * 60_000,
            carbGrams = 80.0,
            mealType = MealTypes.HIGH_FAT_MEAL
        )
    )

    val cob = CarbModel.totalCob(meals, now)
    val absorption = CarbModel.totalCarbAbsorption(meals, now)

    println("COB: %.1f g".format(cob))
    println("Carb absorption: %.2f g/min".format(absorption))
}