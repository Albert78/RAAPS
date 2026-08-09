package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.Timestamp

sealed class AlgorithmIssue {
    data class NoRecentValues(val minutes: Int) : AlgorithmIssue()
    data object NoisyValues : AlgorithmIssue()
    data class InternalError(val message: String?) : AlgorithmIssue()
}

enum class AlgorithmReasoning {
    NO_RECENT_VALUES,
    SAFETY_BASAL_FALLBACK,
    LOW_PREDICTED_CARBS_SUGGESTION,
    LOW_PREDICTED_ZERO_TEMP,
    LOW_PREDICTED_LOW_BASAL,
    MEAL_OR_CORRECTION_BOLUS,
    NORMAL_CONDITION_SAFETY_BASAL,
    INTERNAL_ERROR
}

data class AlgorithmInsight(
    val timestamp: Timestamp,
    val bgOriginal: Short,
    val bgFiltered: Short,
    val deviationPerTick: Double,
    val iobAtPeak: Double,
    val cobAtPeak: Double,
    val cobEquivalentOfBasalAtPeak: Double,
    val predictedBgAtPeak: Short,
    val targetBg: Short,
    val isf: Double,
    val cr: Double,
    val reasoning: AlgorithmReasoning,
    val actionBolus: Double? = null,
    val actionTempBasalPercent: Int? = null,
    val actionTempBasalDurationInHours: Int? = null
)

interface ApsAlgorithm {
    suspend fun updateTherapySettings()
    suspend fun updateMeals()
    suspend fun updateInsulin()
    suspend fun recalculate(treatmentLock: TreatmentLock): List<AlgorithmIssue>
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

    override suspend fun recalculate(treatmentLock: TreatmentLock): List<AlgorithmIssue> {
        return emptyList()
    }
}
