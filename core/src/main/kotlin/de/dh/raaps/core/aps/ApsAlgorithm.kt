package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Timestamp

interface ApsAlgorithm {
    suspend fun recalculateForNewBgValue(currentBG: BgReading)
    suspend fun nextStaleCheck(): Timestamp
    suspend fun isStale(): Boolean
}