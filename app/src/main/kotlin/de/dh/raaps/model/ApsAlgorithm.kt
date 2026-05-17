package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Tick

interface ApsAlgorithm {
    fun isRecalculationNecessary(newBgReading: BgReading, tick: Tick): Boolean
    fun recalculate(fromCurrentTick: Tick)
}