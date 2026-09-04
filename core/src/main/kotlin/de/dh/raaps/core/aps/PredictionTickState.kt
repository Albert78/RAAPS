package de.dh.raaps.core.aps

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick

/**
 * Read-only view of the prediction data for a specific time tick.
 * This interface prevents leaking mutable internal state to the outside.
 */
interface ReadOnlyPredictionTickState {
    val tick: Tick
    val effectiveCarbs: Double?
    val effectiveInsulin: InsulinAmount?
    val isf: BgDelta?
    val cr: Double?
    val basalRateUph: InsulinAmount?
    val bgi: BgDelta
    val cumulatedBasalActivity: InsulinAmount

    /**
     * Prediction for the future.
     */
    val predictedBg: BgValue

    /**
     * Best Bg value we have: For the past, this is the glucose value, if present, else past prediction.
     * For the future, this is the predicted value for this tick.
     */
    val assumedBg: BgValue
}

/**
 * Contains the prediction data for the time interval starting at the given time tick in the APS rolling history window.
 * A [PredictionTickState] is a pure calculation cache and can be reconstructed at any time from base data.
 * We avoid storing JVM objects here and try to use primitive values for all data contained here to
 * relieve the garbage collector.
 */
class PredictionTickState : ReadOnlyPredictionTickState {
    override var tick: Tick = Tick.invalid()
    // Cache block 1: Impacts of carbs and insulin. Depend on metabolic events. Cached until carbs or insulin change.
    override var effectiveCarbs: Double? = null // Sum from all meals in the past, per tick
    override var effectiveInsulin: InsulinAmount? = null // Sum from all insulin applications in the past, per tick

    // Cache block 2: Therapy settings. To avoid more or less expensive calculation, cached until settings change.
    override var isf: BgDelta? = null // ISF at the time of this tick, from profile
    override var cr: Double? = 0.0 // CR at the time of this tick, from profile
    override var basalRateUph: InsulinAmount? = null // Normal basal rate in units per hour for this tick, from profile

    // Prediction block:
    // BGI values depend on blocks 1 and 2.
    // BGI is calculated based on carbs and "net insulin" (delivered insulin minus basal requirement).
    // This means BGI is 0 if we deliver exactly the standard basal rate and have no active carbs and
    // insulin influence.
    override var bgi: BgDelta = BgDelta.ZERO

    // The cumulated activity of 100% rate basal insulin from "now" to the time of this tick.
    // Can be used to calculate LOW or HIGH temp scenarios.
    override var cumulatedBasalActivity: InsulinAmount = InsulinAmount.ZERO

    // Predicted BG depends on block 3 and current BG.
    override var predictedBg: BgValue = BgValue.INVALID // Calculated from a starting BG, applying BGIs of previous ticks

    // A unified BG value representing the best available data for both past and future ticks.
    // Since the algorithm's treatment decisions focus on a 1-2 hour horizon, assumedBg
    // is reliable for the near future but may lose significant accuracy for longer-range
    // projections.
    override var assumedBg: BgValue = BgValue.INVALID

    fun initializeToTick(tick: Tick) {
        this.tick = tick
        effectiveCarbs = null
        effectiveInsulin = null
        isf = null
        cr = null
        bgi = BgDelta.ZERO
        cumulatedBasalActivity = InsulinAmount.ZERO
        predictedBg = BgValue.INVALID
    }
}