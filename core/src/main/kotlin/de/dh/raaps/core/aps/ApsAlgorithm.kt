package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.BgReading

interface ApsAlgorithm {
    suspend fun recalculateForNewBgValue(currentBG: BgReading)
}