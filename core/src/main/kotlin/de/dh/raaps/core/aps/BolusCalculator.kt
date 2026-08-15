package de.dh.raaps.core.aps

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.PlannedInsulin
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Timestamp

data class BolusParts(
    val mealPart: InsulinAmount,
    val correctionPart: InsulinAmount,
    val iobPart: InsulinAmount,
    val cobPart: InsulinAmount,
    val totalProposed: InsulinAmount,
    val cobGrams: Double
)

/**
 * Smart bolus calculator that has access to the internal state of the APS algorithm.
 */
interface BolusCalculator {
    /**
     * Calculates the suggested SEA (Schätzwert der Essens-Anpassung) in minutes.
     * @param overrideBg Optional blood glucose value to use instead of the current/predicted value.
     */
    suspend fun calculateSuggestedSea(overrideBg: BgValue? = null): Int

    /**
     * Calculates the bolus parts for a given carb intake at a specific time.
     */
    suspend fun calculateBolusParts(
        carbsKe: Double,
        mealTimestamp: Timestamp
    ): BolusParts

    /**
     * Distributes the insulin plan based on the manual bolus amount and other parameters.
     */
    suspend fun distributeInsulinPlan(
        manualBolus: InsulinAmount,
        correctionPart: InsulinAmount,
        mealType: MealType?,
        mealTimestamp: Timestamp,
        existingPlan: List<PlannedInsulin> = emptyList()
    ): List<PlannedInsulin>
}
