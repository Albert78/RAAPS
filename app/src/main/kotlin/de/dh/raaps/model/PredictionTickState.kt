package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick

/**
 * Contains the prediction data contained at a discrete time tick in the APS rolling history window.
 * A [PredictionTickState] is a pure calculation cache and can be reconstructed at any time from base data.
 * We avoid storing objects here and try to use primitive values for all data contained here to
 * relieve the garbage collector.
 */
class PredictionTickState {
    var tick: Tick = Tick.invalid()
    var effectiveCarbs: Double = 0.0 // Sum from all meals in the past
    var effectiveInsulin: Double = 0.0 // Sum from all insulin applications in the past
    var isf: Double = 0.0 // ISF at the time of this tick, from profile
    var ic: Double = 0.0 // IC at the time of this tick, from profile
    var bgi: BgValue = BgValue(0) // For all predictions, calculated from effectiveCarbs, effectiveInsulin, isf and ic
    var basal: Double = 0.0 // Remember temp low decisions
    var predictedBg: BgValue = BgValue(0) // Calculated from a starting BG, applying BGIs of previous ticks

    fun initializeToTick(tick: Tick) {
        this.tick = tick
        effectiveCarbs = 0.0
        effectiveInsulin = 0.0
        isf = 0.0
        ic = 0.0
        bgi = BgValue(0)
        basal = 0.0
        predictedBg = BgValue(0)
    }
}