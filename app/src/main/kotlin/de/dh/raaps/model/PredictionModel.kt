package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.times
import de.dh.raaps.model.ApsAlgorithmImpl.Companion.DEVIATION_DECAY_FACTOR_PER_TICK

/**
 * Predicts future blood glucose levels based on current blood glucose, treatment history
 * and calculated treatment decisions.
 *
 * All insulin and carb calculations are modeled as future impacts (Blood Glucose Impact, BGI),
 * this allows us to cache those values until we have new treatment input.
 *
 * When a new blood glucose value is received, these cached BGI values are projected forward from
 * the current reading. Recalculation of the underlying BGI values is only necessary if the
 * user profile (ISF, IC) or the treatment history changes.
 */
class PredictionModel(
    val predictionWindowHours: Int = 10,
    val timeline: ApsTimeline
) {
    var rollingHistory = RollingPredictionWindow(
        predictionWindowHours = predictionWindowHours,
        timeline = timeline,
        timeline.tick(Timestamp.now())
    )

    inline fun getFirstTick() = rollingHistory.getFirstTick()
    inline fun getLastTick() = rollingHistory.getLastTick()

    inline fun initializeToTick(newAnchorTimestamp: Timestamp) {
        rollingHistory.init(timeline.tick(newAnchorTimestamp))
    }

    inline fun tryGetTickState(tick: Tick): PredictionTickState? {
        return rollingHistory.tryGetTickState(tick)
    }

    /**
     * Initialize the predictions to the first state where all effective insulin and effective carbs
     * are calculated.
     */
    inline suspend fun calculatePredictionStage_1(
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
    inline suspend fun calculatePredictionStates_2_3_4(
        currentBG: BgValue,
        avgCurrentDeviation: BgDelta,
        therapyModel: TherapyModel
    ): Boolean {
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
            if (bg != state.predictedBg1) {
                state.predictedBg1 = bg
                continueCalculations = true
            }
        }
        return continueCalculations
    }

    /**
     * Clears all temporary basal derivation decisions.
     */
    inline fun clearTempBasalsStage_5() {
        forEach { _, state ->
            state.basalDeviationPerHour = 0.0
        }
    }

    /**
     * Sets the temporary basal derivation per hour for the given timespan.
     * The basal derivation must be higher than the scheduled basal for that timespan.
     */
    inline fun setTempBasalDeviationStage_5(
        basalDeviationPerHour: Double,
        tempBasalStart: Tick,
        tempBasalEnd: Tick
    ) {
        forEach(from = tempBasalStart, to = tempBasalEnd) { _, state ->
            state.basalDeviationPerHour = basalDeviationPerHour
        }
    }

    inline fun calculatePredictionsWithTempBasalStage_6(
        from: Tick = getFirstTick(),
        to: Tick = getLastTick()
    ) {
        forEach(from = from, to = to) { _, state ->
            val tempBgi = state.basalDeviationPerHour * state.isf
            state.predictedBg2 = state.predictedBg1 + tempBgi
        }
    }

    inline fun advanceToTick(newAnchor: Tick) {
        rollingHistory.moveWindowTo(newAnchor)
    }

    inline fun latestPredictionTickState(): PredictionTickState? {
        return rollingHistory.tryGetTickState(rollingHistory.getLastTick())
    }

    inline fun findNextBgMin(startAt: Tick, returnLatestIfFalling: Boolean): PredictionTickState? {
        var lastValue: BgValue = BgValue.INVALID

        val tickState = rollingHistory.findForward(startAt) { tickState ->
            val currentBg = tickState.predictedBg1

            // We are looking for the point at which the value starts to grow again (local minimum)
            if (currentBg > lastValue) {
                // The previous point was the minimum
                true // Stop search
            } else {
                lastValue = currentBg
                false // Continue searching
            }
        }
        return if (tickState != null) {
            tickState
        } else if (returnLatestIfFalling) {
            latestPredictionTickState()
        } else {
            null
        }
    }

    inline fun findNextBgMax(startAt: Tick, returnLatestIfRising: Boolean): PredictionTickState? {
        var lastValue: BgValue = BgValue.INVALID

        val tickState = rollingHistory.findForward(startAt) { tickState ->
            val currentBg = tickState.predictedBg1

            // We are looking for the point at which the value starts to drop again (local maximum)
            if (currentBg < lastValue) {
                // The previous point was the maximum
                true // Stop search
            } else {
                lastValue = currentBg
                false // Continue searching
            }
        }
        return if (tickState != null) {
            tickState
        } else if (returnLatestIfRising) {
            latestPredictionTickState()
        } else {
            null
        }
    }

    fun findNext(startAt: Tick, predicate: (PredictionTickState) -> Boolean): PredictionTickState? {
        return rollingHistory.findForward(startAt, predicate)
    }

    suspend fun findNextS(startAt: Tick, predicate: suspend (PredictionTickState) -> Boolean): PredictionTickState? {
        return rollingHistory.findForwardS(startAt, predicate)
    }

    fun forEach(
        from: Tick = getFirstTick(),
        to: Tick = getLastTick(),
        action: (Tick, PredictionTickState) -> Unit) {
        rollingHistory.forEach(from = from, to = to, action)
    }

    suspend fun forEachS(
        from: Tick = getFirstTick(),
        to: Tick = getLastTick(),
        action: suspend (Tick, PredictionTickState) -> Unit) {
        rollingHistory.forEachS(action)
    }
}