package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Timestamp

interface ApsAlgorithm {
    suspend fun updateMealsAndInsulin()
    suspend fun recalculateForNewBgValue(currentBG: BgReading)
    suspend fun nextBgStaleCheckAt(): Timestamp
    suspend fun isStale(): Boolean
}

class NoopAlgorithm: ApsAlgorithm {
    override suspend fun updateMealsAndInsulin() {
        // Do nothing
    }

    override suspend fun recalculateForNewBgValue(currentBG: BgReading) {
        // Do nothing
    }

    override suspend fun nextBgStaleCheckAt(): Timestamp {
        return Timestamp.now().plusMinutes(5)
    }
    
    override suspend fun isStale(): Boolean {
        return false
    }
}