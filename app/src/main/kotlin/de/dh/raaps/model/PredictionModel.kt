package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp

data class PredictionPoint(
    val bg: BgValue,
    val timestamp: Timestamp
)

/**
 * Predicts future blood glucose levels based on current treatment history and state.
 *
 * All insulin and carb calculations are modeled as future impacts (Blood Glucose Impact, BGI).
 * Instead of recalculating everything from scratch, the predicted impacts of previous treatments
 * are pre-calculated and cached for future time ticks.
 *
 * When a new blood glucose value is received, these cached BGI values are projected forward from
 * the current reading. Recalculation of the underlying BGI values is only necessary if the
 * user profile (ISF, IC) or the treatment history changes.
 */
class PredictionModel(
    val predictionWindowHours: Int = 10,
    val tickInterval: Minutes = Minutes(5)
) {
    var rollingHistory = RollingPredictionWindow(predictionWindowHours = predictionWindowHours, tickDuration = tickInterval, timestamp = Timestamp.now())

    fun clear() {
        rollingHistory.init(rollingHistory.tick(Timestamp.now()))
    }

    fun toPredictionPoint(tickState: PredictionTickState): PredictionPoint {
        return PredictionPoint(tickState.predictedBg, rollingHistory.timestamp(tickState.tick))
    }

    fun findNextBgMax(startAt: Timestamp): PredictionPoint? {
        val startTick = rollingHistory.tick(startAt)

        var lastValue: BgValue = BgValue(0)

        return rollingHistory.findForward(startTick) { tickState ->
            val currentBg = tickState.predictedBg

            // We are looking for the point at which the value starts to drop again (local maximum)
            if (currentBg < lastValue) {
                // The previous point was the maximum
                true // Stop search
            } else {
                lastValue = currentBg
                false // Continue searching
            }
        }?.let { tickState -> toPredictionPoint(tickState) }
    }

    fun findNext(startAt: Timestamp, predicate: (PredictionTickState) -> Boolean): PredictionPoint? {
        val startTick = rollingHistory.tick(startAt)

        return rollingHistory.findForward(startTick) {
            tickState -> predicate(tickState)
        }?.let { tickState -> toPredictionPoint(tickState) }
    }

    fun forEach(action: (Tick, PredictionTickState) -> Unit) {
        rollingHistory.forEach(action)
    }
}