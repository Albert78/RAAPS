package de.dh.raaps.core.aps

interface ApsAlgorithm {
    suspend fun updateMeals()
    suspend fun updateInsulin()
    suspend fun recalculate()
}

class NoopAlgorithm: ApsAlgorithm {
    override suspend fun updateMeals() {
        // Do nothing
    }

    override suspend fun updateInsulin() {
        // Do nothing
    }

    override suspend fun recalculate() {
        // Do nothing
    }
}