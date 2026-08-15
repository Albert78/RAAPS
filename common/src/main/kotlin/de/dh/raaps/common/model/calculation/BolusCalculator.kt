package de.dh.raaps.common.model.calculation

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.PlannedInsulin
import de.dh.raaps.common.model.convertToInsulinAmountFromBgDelta
import de.dh.raaps.common.model.convertToInsulinAmountFromCarbs
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

data class BolusParts(
    val mealPart: InsulinAmount,
    val correctionPart: InsulinAmount,
    val iobPart: InsulinAmount,
    val cobPart: InsulinAmount,
    val totalProposed: InsulinAmount
)

object BolusCalculator {
    fun calculateSuggestedSea(currentBg: Int?, targetBg: Int): Int {
        if (currentBg == null) return 0
        val diff = currentBg - targetBg
        if (diff <= 0) return 0

        // Simple rule: 5 minutes per 20 mg/dL above target, max 45 min
        val suggested = (diff / 20) * 5
        return suggested.coerceIn(0, 45)
    }

    fun calculateBolusParts(
        carbsKe: Double,
        cr: Double,
        isf: Int,
        currentBg: Int?,
        targetBg: Int,
        lowThreshold: Int,
        iob: InsulinAmount,
        cob: Double
    ): BolusParts {
        val carbsGrams = carbsKe * 10.0
        val mealPart = convertToInsulinAmountFromCarbs(carbsGrams, cr)

        val currentBgValue = currentBg ?: targetBg
        val bgDiff = currentBgValue - targetBg

        // Can be positive or negative
        val correctionPart = convertToInsulinAmountFromBgDelta(BgDelta(bgDiff.toShort()), BgDelta(isf.toShort()))

        val iobPart = iob
        val cobPart = convertToInsulinAmountFromCarbs(cob, cr)

        val total = (mealPart + correctionPart - iobPart + cobPart).coerceAtLeast(InsulinAmount.ZERO)

        // Round to 2 decimal places
        val roundedTotal = round(total.iu * 100.0) / 100.0
        var bolusAmount = InsulinAmount(roundedTotal)

        // If BG is unknown or below low threshold, no insulin is proposed
        if (currentBg == null || currentBg <= lowThreshold) {
            bolusAmount = InsulinAmount.ZERO
        }

        return BolusParts(
            mealPart = mealPart,
            correctionPart = correctionPart,
            iobPart = iobPart,
            cobPart = cobPart,
            totalProposed = bolusAmount
        )
    }

    fun distributeInsulinPlan(
        manualBolus: InsulinAmount,
        correctionPart: InsulinAmount,
        selectedMealType: MealType?,
        currentBg: Int?,
        lowThreshold: Int,
        now: Timestamp,
        existingPlan: List<PlannedInsulin> = emptyList()
    ): List<PlannedInsulin> {
        if (manualBolus <= InsulinAmount.ZERO || selectedMealType == null) {
            return emptyList()
        }

        val totalAmount = manualBolus.iu
        val correction = correctionPart.iu
        val restToDistribute = totalAmount - correction
        val mealType = selectedMealType

        val rawAmounts = DoubleArray(mealType.components.size) { i ->
            val weight = mealType.components[i].weight / 100.0
            val share = restToDistribute * weight
            if (i == 0) share + correction else share
        }

        // Re-balance negatives forward (drain deficit to next part)
        for (i in 0 until rawAmounts.size - 1) {
            if (rawAmounts[i] < 0) {
                rawAmounts[i + 1] += rawAmounts[i]
                rawAmounts[i] = 0.0
            }
        }
        // Re-balance negatives backward (safety check)
        for (i in rawAmounts.size - 1 downTo 1) {
            if (rawAmounts[i] < 0) {
                rawAmounts[i - 1] += rawAmounts[i]
                rawAmounts[i] = 0.0
            }
        }

        val roundedAmounts = rawAmounts.map { round(max(0.0, it) * 100.0) / 100.0 }.toDoubleArray()

        // Final adjustment to ensure sum matches exactly the totalAmount
        val currentSum = roundedAmounts.sum()
        val targetSum = round(totalAmount * 100.0) / 100.0
        val diff = round((targetSum - currentSum) * 100.0) / 100.0

        if (abs(diff) >= 0.01) {
            val indexToAdjust = roundedAmounts.indices.maxByOrNull { roundedAmounts[it] } ?: 0
            roundedAmounts[indexToAdjust] = round((roundedAmounts[indexToAdjust] + diff) * 100.0) / 100.0
        }

        val isLowBg = currentBg != null && currentBg <= lowThreshold

        return mealType.components.mapIndexed { index, component ->
            val amount = InsulinAmount(roundedAmounts[index])

            // Suggested offset:
            val suggestedOffset = if (isLowBg) 15 else 0
            val delayFromBase = if (index == 0) 0 else component.peakMinutes.value.toInt()
            val finalOffset = suggestedOffset + delayFromBase

            // Preserve user modification if possible
            val existing = existingPlan.getOrNull(index)
            val offsetToUse = if (existing?.isUserModified == true) existing.offsetMinutes else finalOffset
            val finalTimestamp = now + Minutes(offsetToUse.toShort())

            PlannedInsulin(
                amount = amount,
                timestamp = finalTimestamp,
                offsetMinutes = offsetToUse,
                description = if (mealType.components.size > 1) "Teil ${index + 1} (${component.weight}%)" else "Bolus",
                isUserModified = existing?.isUserModified ?: false
            )
        }.filter { it.amount > InsulinAmount.ZERO }
    }
}
