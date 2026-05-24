package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.times
import de.dh.raaps.model.ApsAlgorithmImpl.Companion.DEVIATION_DECAY_FACTOR_PER_TICK

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
    val timeline: ApsTimeline
) {
    var rollingHistory = RollingPredictionWindow(predictionWindowHours = predictionWindowHours, timeline = timeline, timestamp = Timestamp.now())

    fun getFirstTick() = rollingHistory.getFirstTick()
    fun getLastTick() = rollingHistory.getLastTick()

    fun initializeToTick(newAnchorTimestamp: Timestamp) {
        rollingHistory.init(timeline.tick(newAnchorTimestamp))
    }

    fun tryGetTickState(timestamp: Timestamp): PredictionTickState? {
        return rollingHistory.tryGetTickState(timeline.tick(timestamp))
    }

    /**
     * Initialize the predictions to the first state where all effective insulin and effective carbs
     * are calculated.
     */
    suspend fun calculatePredictionStage_1(
        metabolicEventsModel: MetabolicEventsModel,
        carbsInsulinCalculationModel: CarbsInsulinCalculationModel
    ) {
        val meals = metabolicEventsModel.getMeals()
        val insulinApplications = metabolicEventsModel.getInsulinApplications()
        forEach { tick, tickState ->
            tickState.initializeToTick(tick)
            // We only need to initialize insulin and carbs, since they only depend on the treatments.
            // They only need to be touched when we have more meals or insulin applications.
            // All other data is calculated in each tick cycle.
            tickState.effectiveInsulin = carbsInsulinCalculationModel.effectiveInsulin(
                insulinApplications,
                timeline.timestamp(tick)
            )
            tickState.effectiveCarbs = carbsInsulinCalculationModel.carbAbsorption(
                meals,
                timeline.timestamp(tick)
            )
        }
    }

    /**
     * Calculates all "prediction stages" of the future ticks in our prediction model.
     * @return `true` if values have changed compared to the previous settings; next stages must be
     * calculated. Else `false`.
     */
    suspend fun calculatePredictionStates_2_3_4(currentBG: BgValue, avgCurrentDeviation: BgDelta, therapyModel: TherapyModel): Boolean {
        var bg = currentBG
        var deviation = avgCurrentDeviation
        var continueCalculations = false
        forEachS(from = timeline.getNowTick() + 1, to = getLastTick()) { tick, state ->
            val timestamp = timeline.timestamp(tick)
            val isf = therapyModel.getIsfFactor(timestamp)
            val ic = therapyModel.getIcFactor(timestamp)
            val basalPerHour = therapyModel.getBasalPerHour(timestamp)

            if (isf != state.isf || ic != state.ic || basalPerHour != state.basalPerHour) {
                state.isf = isf
                state.ic = ic
                state.basalPerHour = basalPerHour

                val insulinEquivalentOfCarbs = state.effectiveCarbs / ic
                val bgi = (insulinEquivalentOfCarbs - state.effectiveInsulin) * isf

                state.bgi = bgi

                continueCalculations = true
            }

            // Ease out the deviation
            deviation *= DEVIATION_DECAY_FACTOR_PER_TICK

            bg = bg + state.bgi + deviation
            if (bg != state.predictedBg) {
                state.predictedBg = bg
                continueCalculations = true
            }
        }
        return continueCalculations
    }

    /**
     * Clears all temporary basal derivation decisions.
     */
    fun clearTempBasalsStage_5() {
        forEach { _, state ->
            state.basalDeviationPerHour = 0.0
        }
    }

    /**
     * Sets the temporary basal derivation per hour for the given timespan.
     * The basal derivation must be higher than the scheduled basal for that timespan.
     */
    fun setTempBasalDeviationStage_5(
        basalDeviationPerHour: Double,
        tempBasalStart: Timestamp,
        tempBasalEnd: Timestamp
    ) {
        forEach(from = timeline.tick(tempBasalStart), to = timeline.tick(tempBasalEnd)) { _, state ->
            state.basalDeviationPerHour = basalDeviationPerHour
        }
    }

    fun calculatePredictionsWithTempBasalStage_6(
        from: Tick = getFirstTick(),
        to: Tick = getLastTick()
    ) {
        forEach(from = from, to = to) { _, state ->
            val tempBgi = state.basalDeviationPerHour * state.isf
            state.predictedBg2 = state.predictedBg + tempBgi
        }
    }

    fun advanceToTimestamp(newAnchor: Timestamp) {
        rollingHistory.moveWindowTo(timeline.tick(newAnchor))
    }

    fun toPredictionPoint(tickState: PredictionTickState): PredictionPoint {
        return PredictionPoint(tickState.predictedBg, timeline.timestamp(tickState.tick))
    }

    fun latestPredictionPoint(): PredictionPoint? {
        return rollingHistory.tryGetTickState(rollingHistory.getLastTick())?.
            let { tickState -> toPredictionPoint(tickState) }
    }

    fun findNextBgMin(startAt: Timestamp, returnLatestIfFalling: Boolean): PredictionPoint? {
        val startTick = timeline.tick(startAt)

        var lastValue: BgValue = BgValue.INVALID

        val tickState = rollingHistory.findForward(startTick) { tickState ->
            val currentBg = tickState.predictedBg

            // We are looking for the point at which the value starts to grow again (local minimum)
            if (currentBg > lastValue) {
                // The previous point was the minimum
                true // Stop search
            } else {
                lastValue = currentBg
                false // Continue searching
            }
        }
        if (tickState != null) {
            return toPredictionPoint(tickState)
        } else if (returnLatestIfFalling) {
            return latestPredictionPoint()
        } else {
            return null
        }
    }

    fun findNextBgMax(startAt: Timestamp, returnLatestIfRising: Boolean): PredictionPoint? {
        val startTick = timeline.tick(startAt)

        var lastValue: BgValue = BgValue.INVALID

        val tickState = rollingHistory.findForward(startTick) { tickState ->
            val currentBg = tickState.predictedBg

            // We are looking for the point at which the value starts to drop again (local maximum)
            if (currentBg < lastValue) {
                // The previous point was the maximum
                true // Stop search
            } else {
                lastValue = currentBg
                false // Continue searching
            }
        }
        if (tickState != null) {
            return toPredictionPoint(tickState)
        } else if (returnLatestIfRising) {
            return latestPredictionPoint()
        } else {
            return null
        }
    }

    fun findNext(startAt: Timestamp, predicate: (PredictionTickState) -> Boolean): PredictionPoint? {
        val startTick = timeline.tick(startAt)

        return rollingHistory.findForward(startTick) {
            tickState -> predicate(tickState)
        }?.let { tickState -> toPredictionPoint(tickState) }
    }

    suspend fun findNextS(startAt: Timestamp, predicate: suspend (PredictionTickState) -> Boolean): PredictionPoint? {
        val startTick = timeline.tick(startAt)

        return rollingHistory.findForwardS(startTick) {
                tickState -> predicate(tickState)
        }?.let { tickState -> toPredictionPoint(tickState) }
    }

    fun forEach(
        from: Timestamp = timeline.timestamp(getFirstTick()),
        to: Timestamp = timeline.timestamp(getLastTick()),
        action: (Tick, PredictionTickState) -> Unit) {
        rollingHistory.forEach(from = timeline.tick(from), to = timeline.tick(to), action)
    }

    suspend fun forEachS(
        from: Tick = getFirstTick(),
        to: Tick = getLastTick(),
        action: suspend (Tick, PredictionTickState) -> Unit) {
        rollingHistory.forEachS(action)
    }
}