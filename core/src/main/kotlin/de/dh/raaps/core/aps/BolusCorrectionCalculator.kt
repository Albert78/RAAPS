package de.dh.raaps.core.aps

import de.dh.raaps.common.model.DEFAULT_IMI_MINUTES
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.PlannedInsulin
import de.dh.raaps.common.model.convertToCarbsFromBgDelta
import de.dh.raaps.common.model.convertToInsulinAmountFromBgDelta
import de.dh.raaps.common.model.convertToInsulinAmountFromCarbs
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round

data class BolusParts(
    val mealPart: InsulinAmount,
    val correctionPart: InsulinAmount,
    val iobPart: InsulinAmount,
    val cobPart: InsulinAmount,
    val futureCarbsPart: InsulinAmount,
    val deferredBolusPart: InsulinAmount,
    val totalProposed: InsulinAmount
) {
    companion object {
        fun empty(): BolusParts = BolusParts(
            mealPart = InsulinAmount.ZERO,
            correctionPart = InsulinAmount.ZERO,
            iobPart = InsulinAmount.ZERO,
            cobPart = InsulinAmount.ZERO,
            futureCarbsPart = InsulinAmount.ZERO,
            deferredBolusPart = InsulinAmount.ZERO,
            totalProposed = InsulinAmount.ZERO
        )
    }
}

/**
 * System projections for a reference timestamp.
 */
data class BolusProjections(
    val timestamp: Timestamp = Timestamp.now(),
    val isProjected: Boolean = false,
    val bg: BgValue = BgValue.INVALID,
    val iob: InsulinAmount = InsulinAmount.ZERO,
    val cob: Double = 0.0,
    val futureCarbs: Double = 0.0,
    val deferredBolusAmount: InsulinAmount = InsulinAmount.ZERO
)

/**
 * Bolus calculator to support the user with carbs, IMI and bolus suggestions.
 */
interface BolusCorrectionCalculator {
    /**
     * Calculates the base data for the meal bolus screen.
     */
    suspend fun calculateBolusProjections(mealTimestamp: Timestamp = Timestamp.now()): BolusProjections

    /**
     * Calculates the bolus parts for a given carb intake at a specific time.
     */
    suspend fun calculateBolusParts(
        carbsKe: Double,
        mealTimestamp: Timestamp,
        projectedBg: BgValue,
        projectedIob: InsulinAmount,
        projectedCob: Double,
        futureCarbs: Double,
        deferredBolusAmount: InsulinAmount
    ): BolusParts

    /**
     * Distributes the insulin plan based on the manual bolus amount and other parameters.
     * The resulting [PlannedInsulin] objects have offsets relative to the meal time.
     */
    suspend fun distributeInsulinPlan(
        manualBolus: InsulinAmount,
        correctionPart: InsulinAmount,
        mealType: MealType?,
        suggestedImi: Minutes
    ): List<PlannedInsulin>
}

/**
 * Shared calculation logic for bolus correction.
 */
object BolusCalculationMath {
    fun calculateSuggestedCarbsKe(bg: BgValue, targetBg: BgValue, isf: BgDelta, cr: Double, futureCarbs: Double): Double {
        var suggestedCarbsKe = 0.0
        if (bg.isValid() && bg < targetBg) {
            val bgDiff = targetBg - bg
            val carbsGrams = convertToCarbsFromBgDelta(bgDiff, isf, cr)
            val remainingCarbsGrams = (carbsGrams - futureCarbs).coerceAtLeast(0.0)
            // Round up to whole 5g
            val roundedCarbsGrams = ceil(remainingCarbsGrams / 5.0) * 5.0
            suggestedCarbsKe = roundedCarbsGrams / 10.0
        }
        return suggestedCarbsKe
    }

    suspend fun calculateSuggestedImi(currentBg: BgValue, therapyManager: TherapyManager): Minutes {
        if (currentBg.isInvalid()) return Minutes(DEFAULT_IMI_MINUTES)

        val bgSettings = therapyManager.getBgSettings()
        val targetBg = bgSettings.first
        val lowThreshold = bgSettings.second

        if (currentBg.mgdl <= lowThreshold.mgdl) return Minutes(-15) // Suggest 15 min delay for bolus if low

        val diff = currentBg.mgdl - targetBg.mgdl
        if (diff <= 0) return Minutes(0)

        // Simple rule: 5 minutes per 20 mg/dL above target, max 45 min
        return Minutes(((diff / 20) * 5).coerceIn(0, 45).toShort())
    }

    /**
     * Calculates the individual insulin components for a bolus proposal.
     *
     * IMPORTANT: This calculation is anchored at [mealTimestamp]. All time-dependent therapy factors
     * (CR, ISF) and all projected values (BG, IOB, COB, Future Carbs, Deferred Bolus) must be
     * valid/calculated for this specific point in time.
     *
     * This method breaks down the total required insulin into its contributing parts:
     * - Meal: Based on current carb intake (KE).
     * - Correction: To reach the BG target from the current projected BG.
     * - IOB: Active insulin that is subtracted.
     * - COB: Remaining active carbohydrates that require insulin.
     * - Future Carbs: Pre-announced carbohydrates.
     * - Deferred Bolus: Already planned insulin amounts that should not be double-dosed.
     *
     * @param carbsKe The amount of carbohydrates in KE (1 KE = 10g).
     * @param bg The projected blood glucose value at [mealTimestamp].
     * @param mealTimestamp The reference time for the bolus (determines CR/ISF and projection context).
     * @param therapyManager Provider for patient settings (ISF, CR, Target).
     * @param iob The projected active insulin on board at [mealTimestamp].
     * @param cob The projected active carbohydrates on board (in grams) at [mealTimestamp].
     * @param futureCarbs Carbohydrates pre-announced for the future relative to [mealTimestamp].
     * @param deferredBolusAmount Insulin amount already planned for administration after [mealTimestamp].
     * @return A [BolusParts] object containing all calculated components.
     */
    suspend fun calculateBolusParts(
        carbsKe: Double,
        bg: BgValue,
        mealTimestamp: Timestamp,
        therapyManager: TherapyManager,
        iob: InsulinAmount,
        cob: Double,
        futureCarbs: Double,
        deferredBolusAmount: InsulinAmount
    ): BolusParts {
        // Retrieve therapy factors valid for the meal time
        val cr = therapyManager.getCrFactor(mealTimestamp)
        val isf = therapyManager.getIsfFactor(mealTimestamp).mgdl.toInt()
        val targetBg = therapyManager.getBgSettings().first

        // Calculate insulin needed for the current meal (KE -> Grams -> IU)
        val carbsGrams = carbsKe * 10.0
        val mealPart = convertToInsulinAmountFromCarbs(carbsGrams, cr)

        // Determine BG deviation from target at the time of the meal.
        // Fallback to target if no valid BG is available.
        val bgMgdl = if (bg.isValid()) bg.mgdl else targetBg.mgdl
        val bgDiff = bgMgdl - targetBg.mgdl

        // Calculate correction, COB and future carb parts using meal-time factors
        val correctionPart = convertToInsulinAmountFromBgDelta(BgDelta(bgDiff.toShort()), BgDelta(isf.toShort()))
        val cobPart = convertToInsulinAmountFromCarbs(cob, cr)
        val futureCarbsPart = convertToInsulinAmountFromCarbs(futureCarbs, cr)

        // Sum up all parts: Add requirements (Meal, Correction, COB, Future Carbs),
        // subtract already active/planned resources (IOB, Deferred Boluses).
        val total = (mealPart + correctionPart - iob + cobPart + futureCarbsPart - deferredBolusAmount).coerceAtLeast(InsulinAmount.ZERO)

        // Round to 2 decimal places for insulin pump precision
        val roundedTotal = round(total.iu * 100.0) / 100.0
        val bolusAmount = InsulinAmount(roundedTotal)

        return BolusParts(
            mealPart = mealPart,
            correctionPart = correctionPart,
            iobPart = iob,
            cobPart = cobPart,
            futureCarbsPart = futureCarbsPart,
            deferredBolusPart = deferredBolusAmount,
            totalProposed = bolusAmount
        )
    }

    fun distributeInsulinPlan(
        manualBolus: InsulinAmount,
        correctionPart: InsulinAmount,
        mealType: MealType?,
        suggestedImi: Minutes
    ): List<PlannedInsulin> {
        if (manualBolus <= InsulinAmount.ZERO) return emptyList()

        // IMI (Injection-Meal Interval) > 0 means wait time between bolus and meal -> Bolus before meal.
        // IMI < 0 means a negative wait time (delay) -> Bolus after meal.
        val defaultTimeFromMeal = if (suggestedImi.value >= 0) Minutes((-suggestedImi.value).toShort()) else Minutes(abs(suggestedImi.value.toInt()).toShort())

        if (mealType == null) {
            return listOf(
                PlannedInsulin(amount = manualBolus, timeFromMeal = defaultTimeFromMeal)
            )
        }

        val roundedAmounts = calculateComponentAmounts(manualBolus.iu, correctionPart.iu, mealType)

        return mealType.components.mapIndexed { index, component ->
            val amount = InsulinAmount(roundedAmounts[index])
            val delayFromBase = if (index == 0) 0 else component.peakMinutes.value.toInt()
            val finalDefaultTime = defaultTimeFromMeal + Minutes(delayFromBase.toShort())

            PlannedInsulin(
                amount = amount,
                timeFromMeal = finalDefaultTime,
                partWeight = if (mealType.components.size > 1) component.weight else null
            )
        }.filter { it.amount > InsulinAmount.ZERO }
    }

    private fun calculateComponentAmounts(totalAmount: Double, correction: Double, mealType: MealType): DoubleArray {
        val restToDistribute = totalAmount - correction
        val rawAmounts = DoubleArray(mealType.components.size) { i ->
            val weight = mealType.components[i].weight / 100.0
            val part = restToDistribute * weight
            if (i == 0) part + correction else part
        }

        // Balance negative amounts across components (e.g. if a negative correction
        // outweighs a meal part). Shifting the "debt" ensures the total sum is preserved.
        for (i in 0 until rawAmounts.size - 1) {
            if (rawAmounts[i] < 0) {
                rawAmounts[i + 1] += rawAmounts[i] // Carry the negative amount to the next part
                rawAmounts[i] = 0.0
            }
        }

        // Secondary pass from back to front to clean up any remaining negative values.
        // This should never if meal types are configured correctly.
        for (i in rawAmounts.size - 1 downTo 1) {
            if (rawAmounts[i] < 0) {
                rawAmounts[i - 1] += rawAmounts[i] // Carry the negative amount to the previous part
                rawAmounts[i] = 0.0
            }
        }
        val roundedAmounts = rawAmounts.map { round(max(0.0, it) * 100.0) / 100.0 }.toDoubleArray()

        // Adjust sum to match totalAmount due to rounding
        val targetSum = round(totalAmount * 100.0) / 100.0
        val diff = round((targetSum - roundedAmounts.sum()) * 100.0) / 100.0
        if (abs(diff) >= 0.01) {
            val idx = roundedAmounts.indices.maxByOrNull { roundedAmounts[it] } ?: 0
            roundedAmounts[idx] = round((roundedAmounts[idx] + diff) * 100.0) / 100.0
        }
        return roundedAmounts
    }
}

/**
 * A simple bolus correction calculator that does not rely on APS predictions.
 */
class SimpleBolusCorrectionCalculator(
    private val therapyManager: TherapyManager,
    private val glucoseSourceManager: GlucoseSourceManager
) : BolusCorrectionCalculator {

    private fun getCurrentBg() = glucoseSourceManager.currentBg.value?.value ?: BgValue.INVALID

    override suspend fun calculateBolusProjections(mealTimestamp: Timestamp) = BolusProjections(
        timestamp = Timestamp.now(),
        isProjected = false,
        bg = getCurrentBg(),
        iob = InsulinAmount.ZERO,
        cob = 0.0,
        futureCarbs = 0.0
    )

    override suspend fun calculateBolusParts(
        carbsKe: Double,
        mealTimestamp: Timestamp,
        projectedBg: BgValue,
        projectedIob: InsulinAmount,
        projectedCob: Double,
        futureCarbs: Double,
        deferredBolusAmount: InsulinAmount
    ) = BolusCalculationMath.calculateBolusParts(
            carbsKe = carbsKe,
            bg = projectedBg,
            mealTimestamp = mealTimestamp,
            therapyManager = therapyManager,
            iob = projectedIob,
            cob = projectedCob,
            futureCarbs = futureCarbs,
            deferredBolusAmount = deferredBolusAmount
        )

    override suspend fun distributeInsulinPlan(
        manualBolus: InsulinAmount,
        correctionPart: InsulinAmount,
        mealType: MealType?,
        suggestedImi: Minutes
    ) = BolusCalculationMath.distributeInsulinPlan(
        manualBolus = manualBolus,
        correctionPart = correctionPart,
        mealType = mealType,
        suggestedImi = suggestedImi
    )
}