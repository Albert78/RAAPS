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
    val cobGrams: Double,
    val calculationBg: BgValue,
    val calculationTimestamp: Timestamp
)

data class BolusScreenBaseData(
    val referenceTimestamp: Timestamp,
    val referenceBg: BgValue,
    val suggestedCarbsKe: Double,
    val suggestedSea: Int
)

/**
 * Smart bolus calculator that has access to the internal state of the APS algorithm.
 */
interface BolusCorrectionCalculator {
    /**
     * Calculates the base data for the meal bolus screen.
     */
    suspend fun calculateBaseData(): BolusScreenBaseData

    /**
     * Calculates the suggested SEA (Schätzwert der Essens-Anpassung) in minutes.
     */
    suspend fun calculateSuggestedSea(): Int

    /**
     * Calculates the bolus parts for a given carb intake at a specific time.
     */
    suspend fun calculateBolusParts(
        carbsKe: Double,
        mealTimestamp: Timestamp,
        referenceTimestamp: Timestamp
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
