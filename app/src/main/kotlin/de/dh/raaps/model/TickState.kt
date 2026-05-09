package de.dh.raaps.model

import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Tick

/**
 * Contains the calculation data contained at a discrete time tick in the APS rolling history window.
 */
data class TickState(
    /**
     * Database id; 0 for new entity.
     */
    var id: Long,

    /**
     * Tick number of this entry. The tick is the numbers of ms since epoch start (1.1.1970, 0:00),
     * divided by our tick interval. The tick interval is typically 5 minutes but might be decreased
     * to 1 minute in the future.
     */
    val tick: Tick,

    /**
     * The blood glucose value from our source,either via CGM or bloody measure.
     */
    var bg: BgReading? = null,

    // Calculated fields

    /**
     * The calculated amount of insulin in U/tickDuration which is supposed to be effective at the
     * time of this tick.
     *
     * How to calculate:
     * Since an insulin application is effective over a time of multiple hours (the `DIA`) and different
     * insulin applications sum up, we can calculate the effective insulin for a given timespan
     * as the integral over the insulin activity.
     *
     * The insulin activity is a snapshot for a given moment in time `t`.
     * For calculating the insulin activity for time `t`, we need to look at all insulin
     * applications in the past (up to the duration of insulin action, `DIA`). For each
     * insulin application `i`, we can calculate the activity curve over the time.
     * For calculating this curve (`insulinActivity(i, t)`), we need the amount of applied
     * insulin `insulinAmount(i)` for the application `i, the time when the application took
     * place `applicationTime(i)` and the insulin effect curve for the applied insulin over the time `IEC(t)`.
     * `insulinActivity(i, t) = insulinAmount(i) * IEC(t - applicationTime(i))`
     * The active insulin for time `t` is the sum of all past insulin applications during the
     * duration of insulin action (`DIA`):
     * `insulinActivity(t) = sum insulinActivity(i, t)`
     * It has to be recalculated for all future ticks if we do an insulin application, e.g. via
     * pump action.
     *
     * We can calculate the effective insulin for a timespan between times `t1`and `t2` by calculating
     * the integral over the insulin activity, so for calculating the effective insulin for this tick,
     * we calculate that integral from the beginning of the tick up to the end of the tick.
     * To approximate this, we just sum the insulin activity values
     * calculated for each minute in this tick from the beginning of the tick (`t0`) up to the tick
     * interval size, e.g.
     * `effectiveInsulinDuringTick = sum(t = t0..t0+tickIntervalSize) insulinActivity(t)`.
     *
     * The impact on the blood glucose in the whole tick should then be:
     * `bgi(tick) = -effectiveInsulinDuringTick * ISF(tickTime)`.
     *
     * Connection between insulinActivity and IOB:
     * The IOB value is just a compacted value which is the integral of all
     * insulinActivity over the future.
     */
    var supposedEffectiveInsulin: Double = 0.0,

    /**
     * The calculated amount of carbs in g/min which we know about and which is supposed to be
     * effective at the time of this tick.
     * TODO: Describe calculation.
     */
    var supposedEffectiveCarbs: Double = 0.0,
) {
    companion object {
        fun empty(tick: Tick) = TickState(id = ID_UNDEFINED, tick = tick)
    }
}