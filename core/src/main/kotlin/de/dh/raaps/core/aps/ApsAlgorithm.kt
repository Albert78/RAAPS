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

interface ApsAlgorithm {
    suspend fun updateTherapySettings()
    suspend fun updateMeals()
    suspend fun updateInsulin()
    suspend fun recalculate(treatmentLock: TreatmentLock): Set<CoreIssue>
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

    override suspend fun recalculate(treatmentLock: TreatmentLock): Set<CoreIssue> {
        return emptySet()
    }
}
