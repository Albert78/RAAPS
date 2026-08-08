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
    // Cache block 1: Impacts of carbs and insulin. Depend on metabolic events. Cached until carbs or insulin change.
    var effectiveCarbs: Double? = null // Sum from all meals in the past, per tick
    var effectiveInsulin: Double? = null // Sum from all insulin applications in the past, per tick

    // Cache block 2: Therapy settings. To avoid more or less expensive calculation, cached until settings change.
    var isf: BgDelta? = null // ISF at the time of this tick, from profile
    var cr: Double? = 0.0 // CR at the time of this tick, from profile
    var basalRateUph: Double? = 0.0 // Normal basal rate in units per hour for this tick, from profile

    // Prediction block:
    // BGI values depend on blocks 1 and 2.
    // Includes Carb Impact, Insulin Impact and Basal Requirement (from profile).
    // The BGI is calculated as if no basal rate would be injected, i.e. without correcting, BGI will rise.
    // With all basal applications registered as insulin applications, we get the actual prediction.
    var bgi: BgDelta = BgDelta(0)

    // Predicted BG depends on block 3 and current BG.
    var predictedBg: BgValue = BgValue.INVALID // Calculated from a starting BG, applying BGIs of previous ticks

    fun initializeToTick(tick: Tick) {
        this.tick = tick
        effectiveCarbs = null
        effectiveInsulin = null
        isf = null
        cr = null
        bgi = BgDelta(0)
        predictedBg = BgValue.INVALID
    }
}
