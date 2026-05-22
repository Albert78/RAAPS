package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgReading

interface ApsAlgorithm {
    suspend fun recalculate(currentBG: BgReading)
}