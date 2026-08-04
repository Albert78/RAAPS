package de.dh.raaps.core.aps

interface ApsAlgorithm {
    suspend fun updateMealsAndInsulin()
    suspend fun recalculate()
}

class NoopAlgorithm: ApsAlgorithm {
    override suspend fun updateMealsAndInsulin() {
        // Do nothing
    }

    override suspend fun recalculate() {
        // Do nothing
    }
}