package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick

/**
 * Contains the prediction data for the time interval starting at the given time tick in the APS rolling history window.
 * A [PredictionTickState] is a pure calculation cache and can be reconstructed at any time from base data.
 * We avoid storing JVM objects here and try to use primitive values for all data contained here to
 * relieve the garbage collector.
 */
class PredictionTickState {
    var tick: Tick = Tick.invalid()
    // Stage 1: Impacts of carbs and insulin. Depend on metabolic events.
    var effectiveCarbs: Double = 0.0 // Sum from all meals in the past
    var effectiveInsulin: Double = 0.0 // Sum from all insulin applications in the past

    // Stage 2: ISF and IC. Might be adapted by user, so need to be checked each tick.
    var isf: BgDelta = BgDelta(0) // ISF at the time of this tick, from profile
    var ic: Double = 0.0 // IC at the time of this tick, from profile
    var basalRateUph: Double = 0.0 // Normal basal rate in units per hour for this tick, from profile

    // Stage 3: BGI values depend on stages 1 and 2.
    // Includes Carb Impact, Insulin Impact and Basal Requirement (from profile).
    var bgi: BgDelta = BgDelta(0)

    // Stage 4: Predicted BG depends on stages 1, 2 and 3.
    var predictedBg1: BgValue = BgValue.INVALID // Calculated from a starting BG, applying BGIs of previous ticks

    // Stage 5: Final calculated decision for temp basal depends on stages 1-4.
    var basalRateDeviationPh: Double = 0.0 // Remember temp basal deviation decisions (in units per hour)

    // Stage 6: Predicted BG from stage 4, including basal deviation from stage 5.
    var predictedBg2: BgValue = BgValue.INVALID

    fun initializeToTick(tick: Tick) {
        this.tick = tick
        effectiveCarbs = 0.0
        effectiveInsulin = 0.0
        isf = BgDelta(0)
        ic = 0.0
        bgi = BgDelta(0)
        basalRateDeviationPh = 0.0
        predictedBg1 = BgValue.INVALID
        predictedBg2 = BgValue.INVALID
    }
}