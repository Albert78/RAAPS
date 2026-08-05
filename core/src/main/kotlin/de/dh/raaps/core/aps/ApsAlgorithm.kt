package de.dh.raaps.core.aps

sealed class AlgorithmIssue {
    data class NoRecentValues(val minutes: Int) : AlgorithmIssue()
    data object NoisyValues : AlgorithmIssue()
}

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