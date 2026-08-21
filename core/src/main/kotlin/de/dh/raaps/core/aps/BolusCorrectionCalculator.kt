package de.dh.raaps.core.aps

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.METABOLIC_EVENTS_HISTORY_HOURS
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.PlannedInsulin
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculator
import de.dh.raaps.common.model.convertToCarbsFromBgDelta
import de.dh.raaps.common.model.convertToInsulinAmountFromBgDelta
import de.dh.raaps.common.model.convertToInsulinAmountFromCarbs
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.TreatmentRepository
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round

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

/**
 * A simple bolus correction calculator that does not rely on APS predictions.
 * It uses current blood glucose readings and historical IOB/COB data.
 */
class SimpleBolusCorrectionCalculator(
    private val therapyManager: TherapyManager,
    private val treatmentRepository: TreatmentRepository,
    private val carbsInsulinCalculator: CarbsInsulinCalculator,
    private val glucoseSourceManager: GlucoseSourceManager
) : BolusCorrectionCalculator {
    override suspend fun calculateBaseData(): BolusScreenBaseData {
        val now = Timestamp.now()
        val currentBg = glucoseSourceManager.currentBg.value?.value ?: BgValue.INVALID

        val bgSettings = therapyManager.getBgSettings()
        val targetBg = bgSettings.first
        val isf = therapyManager.getIsfFactor(now)
        val cr = therapyManager.getCrFactor(now)

        var suggestedCarbsKe = 0.0
        if (currentBg.isValid() && currentBg < targetBg) {
            val bgDiff = targetBg - currentBg
            val carbsGrams = convertToCarbsFromBgDelta(bgDiff, isf, cr)
            // Round up to whole 5g
            val roundedCarbsGrams = ceil(carbsGrams / 5.0) * 5.0
            suggestedCarbsKe = roundedCarbsGrams / 10.0
        }

        return BolusScreenBaseData(
            referenceTimestamp = now,
            referenceBg = currentBg,
            suggestedCarbsKe = suggestedCarbsKe,
            suggestedSea = calculateSuggestedSea()
        )
    }

    override suspend fun calculateSuggestedSea(): Int {
        val bgSettings = therapyManager.getBgSettings()
        val targetBg = bgSettings.first
        val lowThreshold = bgSettings.second

        val currentBg = glucoseSourceManager.currentBg.value?.value ?: BgValue.INVALID

        if (currentBg.isInvalid()) return 0

        if (currentBg.mgdl <= lowThreshold.mgdl) {
            return -15
        }

        val diff = currentBg.mgdl - targetBg.mgdl
        if (diff <= 0) return 0

        // Simple rule: 5 minutes per 20 mg/dL above target, max 45 min
        val suggested = (diff / 20) * 5
        return suggested.coerceIn(0, 45)
    }

    override suspend fun calculateBolusParts(
        carbsKe: Double,
        mealTimestamp: Timestamp,
        referenceTimestamp: Timestamp
    ): BolusParts {
        val now = Timestamp.now()
        val settings = therapyManager.getCurrentTherapySettings()
        val dia = settings.insulinProfile.dia
        val peak = settings.insulinProfile.peak

        val cr = therapyManager.getCrFactor(now)
        val isf = therapyManager.getIsfFactor(now).mgdl.toInt()
        val bgSettings = therapyManager.getBgSettings()
        val targetBg = bgSettings.first

        val historyLimit = now.minusHours(METABOLIC_EVENTS_HISTORY_HOURS)
        val insulinHistory = treatmentRepository.getInsulinApplications(from = historyLimit)
        val mealsHistory = treatmentRepository.getMeals(from = historyLimit)

        val projectedIob = carbsInsulinCalculator.iob(insulinHistory, referenceTimestamp, dia, peak)
        val projectedCob = carbsInsulinCalculator.cob(mealsHistory, referenceTimestamp)

        val currentBg = glucoseSourceManager.currentBg.value?.value ?: BgValue.INVALID

        val carbsGrams = carbsKe * 10.0
        val mealPart = convertToInsulinAmountFromCarbs(carbsGrams, cr)

        val bgMgdl = if (currentBg.isValid()) currentBg.mgdl else targetBg.mgdl
        val bgDiff = bgMgdl - targetBg.mgdl

        val correctionPart = convertToInsulinAmountFromBgDelta(BgDelta(bgDiff.toShort()), BgDelta(isf.toShort()))
        val cobPart = convertToInsulinAmountFromCarbs(projectedCob, cr)

        val total = (mealPart + correctionPart - projectedIob + cobPart).coerceAtLeast(InsulinAmount.ZERO)
        val roundedTotal = round(total.iu * 100.0) / 100.0
        val bolusAmount = InsulinAmount(roundedTotal)

        return BolusParts(
            mealPart = mealPart,
            correctionPart = correctionPart,
            iobPart = projectedIob,
            cobPart = cobPart,
            totalProposed = bolusAmount,
            cobGrams = projectedCob,
            calculationBg = currentBg,
            calculationTimestamp = referenceTimestamp
        )
    }

    override suspend fun distributeInsulinPlan(
        manualBolus: InsulinAmount,
        correctionPart: InsulinAmount,
        mealType: MealType?,
        mealTimestamp: Timestamp,
        existingPlan: List<PlannedInsulin>
    ): List<PlannedInsulin> {
        if (manualBolus <= InsulinAmount.ZERO) {
            return emptyList()
        }

        val sea = calculateSuggestedSea()
        val suggestedOffset = if (sea < 0) abs(sea) else 0

        if (mealType == null) {
            return distributeSingleBolus(manualBolus, mealTimestamp, suggestedOffset, existingPlan)
        }

        val totalAmount = manualBolus.iu
        val correction = correctionPart.iu

        val roundedAmounts = calculateComponentAmounts(totalAmount, correction, mealType)

        return mealType.components.mapIndexed { index, component ->
            val amount = InsulinAmount(roundedAmounts[index])
            val delayFromBase = if (index == 0) 0 else component.peakMinutes.value.toInt()
            val finalOffset = suggestedOffset + delayFromBase

            val existing = existingPlan.getOrNull(index)
            val isUserModified = existing?.isUserModified == true

            val newTimestamp = if (isUserModified) {
                mealTimestamp + Minutes(existing.offsetMinutes.toShort())
            } else {
                mealTimestamp + Minutes(finalOffset.toShort())
            }

            PlannedInsulin(
                amount = amount,
                timestamp = newTimestamp,
                offsetMinutes = if (isUserModified) existing.offsetMinutes else finalOffset,
                description = if (mealType.components.size > 1) "Teil ${index + 1} (${component.weight}%)" else "Bolus",
                isUserModified = isUserModified
            )
        }.filter { it.amount > InsulinAmount.ZERO }
    }

    private fun distributeSingleBolus(
        manualBolus: InsulinAmount,
        mealTimestamp: Timestamp,
        suggestedOffset: Int,
        existingPlan: List<PlannedInsulin>
    ): List<PlannedInsulin> {
        val existing = existingPlan.getOrNull(0)
        val isUserModified = existing?.isUserModified == true

        val newTimestamp = if (isUserModified) {
            mealTimestamp + Minutes(existing.offsetMinutes.toShort())
        } else {
            mealTimestamp + Minutes(suggestedOffset.toShort())
        }

        return listOf(
            PlannedInsulin(
                amount = manualBolus,
                timestamp = newTimestamp,
                offsetMinutes = if (isUserModified) existing.offsetMinutes else suggestedOffset,
                description = "Bolus",
                isUserModified = isUserModified
            )
        )
    }

    private fun calculateComponentAmounts(
        totalAmount: Double,
        correction: Double,
        mealType: MealType
    ): DoubleArray {
        val restToDistribute = totalAmount - correction
        val rawAmounts = DoubleArray(mealType.components.size) { i ->
            val weight = mealType.components[i].weight / 100.0
            val part = restToDistribute * weight
            if (i == 0) part + correction else part
        }

        // Balance negative amounts across components
        for (i in 0 until rawAmounts.size - 1) {
            if (rawAmounts[i] < 0) {
                rawAmounts[i + 1] += rawAmounts[i]
                rawAmounts[i] = 0.0
            }
        }
        for (i in rawAmounts.size - 1 downTo 1) {
            if (rawAmounts[i] < 0) {
                rawAmounts[i - 1] += rawAmounts[i]
                rawAmounts[i] = 0.0
            }
        }

        val roundedAmounts = rawAmounts.map { round(max(0.0, it) * 100.0) / 100.0 }.toDoubleArray()

        // Adjust sum to match totalAmount due to rounding
        val currentSum = roundedAmounts.sum()
        val targetSum = round(totalAmount * 100.0) / 100.0
        val diff = round((targetSum - currentSum) * 100.0) / 100.0

        if (abs(diff) >= 0.01) {
            val indexToAdjust = roundedAmounts.indices.maxByOrNull { roundedAmounts[it] } ?: 0
            roundedAmounts[indexToAdjust] = round((roundedAmounts[indexToAdjust] + diff) * 100.0) / 100.0
        }

        return roundedAmounts
    }
}