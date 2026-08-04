package de.dh.raaps.core.aps

import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.times
import de.dh.raaps.core.aps.ApsAlgorithmImpl.Companion.DEVIATION_DECAY_FACTOR_PER_TICK
import de.dh.raaps.core.repository.TreatmentRepository

/**
 * Predicts future blood glucose levels based on current blood glucose, treatment history
 * and calculated treatment decisions.
 *
 * All insulin and carb calculations are modeled as future impacts (Blood Glucose Impact, BGI),
 * this allows us to cache those values until we have new treatment input.
 *
 * When a new blood glucose value is received, these cached BGI values are projected forward from
 * the current reading. Recalculation of the underlying BGI values is only necessary if the
 * user profile (ISF, IC, Basal) or the treatment history changes.
 */
class PredictionModel(
    val predictionWindowHours: Int = 10,
    val timeline: Timeline
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
        treatmentRepository: TreatmentRepository,
        carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
        dia: Minutes,
        peak: Minutes
    ) {
        val meals = treatmentRepository.getMeals()
        val insulinApplications = treatmentRepository.getInsulinApplications()
        forEach { tick, tickState ->
            tickState.initializeToTick(tick)
            // We only need to initialize insulin and carbs, since they only depend on the treatments.
            // They only need to be touched when we have more meals or boluses.
            // All other data is calculated in each tick cycle.
            tickState.effectiveInsulin = carbsInsulinCalculationModel.effectiveInsulin(
                insulinApplications = insulinApplications,
                timestamp = timeline.timestamp(tick),
                dia = dia,
                peak = peak
            )
            tickState.effectiveCarbs = carbsInsulinCalculationModel.carbAbsorption(
                meals,
                timeline.timestamp(tick)
            )
        }
    }

    /**
     * Calculates the "prediction stages" 2-4 of the future ticks in our prediction model.
     * @return `true` if values have changed compared to the previous settings; next stages must be
     * calculated. Else `false`.
     */
    inline suspend fun calculatePredictionStates_2_3_4(
        currentBG: BgValue,
        avgCurrentDeviation: BgDelta,
        therapyManager: TherapyManager
    ): Boolean {
        var bg = currentBG
        var deviation = avgCurrentDeviation
        var calculateFurtherStepsNecessary = false
        val timestampIn30Minutes = Timestamp.now().plusMinutes(30)
        forEachS(from = timeline.getNowTick() + 1, to = getLastTick()) { tick, state ->
            val timestamp = timeline.timestamp(tick)
            val isf = therapyManager.getIsfFactor(timestamp)
            val ic = therapyManager.getIcFactor(timestamp)
            val basalPerHour = therapyManager.getBasalPerHour(timestamp)

            if (isf != state.isf || ic != state.ic || basalPerHour != state.basalRateUph) {
                state.isf = isf
                state.ic = ic
                state.basalRateUph = basalPerHour
                calculateFurtherStepsNecessary = true
            }

            val insulinEquivalentOfCarbs = state.effectiveCarbs / state.ic
            // Absolute BGI: Carbs - Insulin + Basal Requirement
            // Basal rate is converted to units per tick.
            val basalUnitsPerTick = state.basalRateUph / timeline.ticksPerHour()
            val bgi = (insulinEquivalentOfCarbs - state.effectiveInsulin + basalUnitsPerTick) * state.isf

            if (bgi != state.bgi) {
                state.bgi = bgi
                calculateFurtherStepsNecessary = true
            }

            // Ease out the deviation
            deviation *= DEVIATION_DECAY_FACTOR_PER_TICK

            val isInNext30Minutes = timestamp <= timestampIn30Minutes
            bg = bg + state.bgi + deviation
            if (isInNext30Minutes && (bg - state.predictedBg1).abs > MAX_BG_DEVIATION_FOR_KEEP_PREDICTION) {
                // If the new situation shows a significant BG deviation from the predicted BG in the
                // near future, recalculation is necessary
                calculateFurtherStepsNecessary = true
            }
            state.predictedBg1 = bg
        }
        return calculateFurtherStepsNecessary
    }

    /**
     * Clears all temporary basal derivation decisions.
     */
    inline fun clearTempBasalsStage_5() {
        forEach { _, state ->
            state.basalRateDeviationPh = 0.0
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
            state.basalRateDeviationPh = basalDeviationPerHour
        }
    }

    inline fun calculatePredictionsWithTempBasalStage_6(
        from: Tick = getFirstTick(),
        to: Tick = getLastTick()
    ) {
        var accumulatedDeviationBg = BgDelta(0)
        forEach(from = from, to = to) { _, state ->
            // Accumulate the delta per tick
            // basalRateDeviationPh is Units/Hour. Convert to Units/Tick.
            val unitsPerTick = state.basalRateDeviationPh / (60.0 / timeline.tickDuration.value)

            // If deviation is positive (more insulin), BG should drop.
            // BGI is defined such that positive BGI = BG RISE.
            // A positive basalRateDeviationPh means more insulin than profile.
            val tickDeviationBg = -(unitsPerTick * state.isf)

            accumulatedDeviationBg += tickDeviationBg
            state.predictedBg2 = state.predictedBg1 + accumulatedDeviationBg
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