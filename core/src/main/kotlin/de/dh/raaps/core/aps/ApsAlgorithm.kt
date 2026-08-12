package de.dh.raaps.core.aps

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Timestamp

sealed class CoreIssue {
    data class NoRecentValues(val minutes: Int) : CoreIssue()
    data object NoisyValues : CoreIssue()
    data class InternalError(val message: String?) : CoreIssue()
    data object TherapyLockBusy : CoreIssue()
}

enum class CoreReasoning {
    NO_RECENT_VALUES,
    SAFETY_BASAL_FALLBACK,
    LOW_PREDICTED_CARBS_SUGGESTION,
    LOW_PREDICTED_ZERO_TEMP,
    LOW_PREDICTED_LOW_BASAL,
    MEAL_OR_CORRECTION_BOLUS,
    NORMAL_CONDITION_SAFETY_BASAL,
    PENDING_PUMP_JOBS,
    THERAPY_LOCK_HELD,
    INTERNAL_ERROR
}

data class CoreInsight(
    val timestamp: Timestamp,
    val bgOriginal: BgValue,
    val bgFiltered: BgValue,
    val deviationPerTick: BgDelta,
    val iobAtPeak: InsulinAmount,
    val cobAtPeak: Double,
    val predictedBgAtPeak: BgValue,
    val targetBg: BgValue,
    val isf: BgDelta,
    val cr: Double,
    val actionBolus: InsulinAmount? = null,
    val actionTempBasalPercent: Int? = null,
    val actionTempBasalDurationInHours: Int? = null,
    val reasoning: CoreReasoning
)

data class TempBasalResult(
    val percent: Int,
    val durationInHours: Int
)

data class CalculationResult(
    val carbsInGHint: Int?,
    val tempBasal: TempBasalResult?,
    val clearTempBasal: Boolean,
    val bolus: InsulinAmount?,
    val handledDeferredBoluses: List<DeferredBolus>?,
    val coreIssues: Set<CoreIssue>?,
    val reasoning: CoreReasoning,
    val metrics: CoreInsight? = null
) {
    companion object {
        fun safetyBasal(): CalculationResult = CalculationResult(
            carbsInGHint = null,
            tempBasal = null,
            clearTempBasal = true,
            bolus = null,
            handledDeferredBoluses = null,
            coreIssues = null,
            reasoning = CoreReasoning.SAFETY_BASAL_FALLBACK
        )

        fun normalSafetyBasal(): CalculationResult = CalculationResult(
            carbsInGHint = null,
            tempBasal = null,
            clearTempBasal = true,
            bolus = null,
            handledDeferredBoluses = null,
            coreIssues = null,
            reasoning = CoreReasoning.NORMAL_CONDITION_SAFETY_BASAL
        )

        fun tempBasal(percent: Int, durationInHours: Int) = CalculationResult(
            carbsInGHint = null,
            tempBasal = TempBasalResult(
                percent = percent,
                durationInHours = durationInHours
            ),
            clearTempBasal = false,
            bolus = null,
            handledDeferredBoluses = null,
            coreIssues = null,
            reasoning = CoreReasoning.LOW_PREDICTED_LOW_BASAL
        )

        fun zeroTemp(durationInHours: Int): CalculationResult = CalculationResult(
            carbsInGHint = null,
            tempBasal = TempBasalResult(percent = 0, durationInHours = durationInHours),
            clearTempBasal = false,
            bolus = null,
            handledDeferredBoluses = null,
            coreIssues = null,
            reasoning = CoreReasoning.LOW_PREDICTED_ZERO_TEMP
        )

        fun carbsSuggestion(carbsInGHint: Int?) = CalculationResult(
            carbsInGHint = carbsInGHint,
            tempBasal = TempBasalResult(percent = 0, durationInHours = 1),
            clearTempBasal = false,
            bolus = null,
            handledDeferredBoluses = null,
            coreIssues = null,
            reasoning = CoreReasoning.LOW_PREDICTED_CARBS_SUGGESTION
        )

        fun mealOrCorrectionBolus(
            bolusAmount: InsulinAmount,
            handledDeferredBoluses: MutableList<DeferredBolus>
        ) = CalculationResult(
            carbsInGHint = null,
            tempBasal = null,
            clearTempBasal = true,
            bolus = bolusAmount,
            handledDeferredBoluses = handledDeferredBoluses,
            coreIssues = null,
            reasoning = CoreReasoning.MEAL_OR_CORRECTION_BOLUS
        )

        fun coreIssues(vararg issues: CoreIssue) = CalculationResult(
            carbsInGHint = null,
            tempBasal = null,
            clearTempBasal = true,
            bolus = null,
            handledDeferredBoluses = null,
            coreIssues = issues.toSet(),
            reasoning = if (issues.any { it is CoreIssue.NoRecentValues })
                CoreReasoning.NO_RECENT_VALUES
            else
                CoreReasoning.INTERNAL_ERROR
        )
    }
}

interface ApsAlgorithm {
    suspend fun updateTherapySettings()
    suspend fun updateMeals()
    suspend fun updateInsulin()
    suspend fun recalculate(): CalculationResult
}

class NoopAlgorithm: ApsAlgorithm {
    override suspend fun updateTherapySettings() {
        // Do nothing
    }

    override suspend fun updateMeals() {
        // Do nothing
    }

    override suspend fun updateInsulin() {
        // Do nothing
    }

    override suspend fun recalculate(): CalculationResult {
        return CalculationResult.normalSafetyBasal()
    }
}
