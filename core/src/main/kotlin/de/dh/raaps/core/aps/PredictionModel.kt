package de.dh.raaps.core.aps

import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.convertToUnitsFromCarbs
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.times
import de.dh.raaps.core.aps.ApsAlgorithmImpl.Companion.DEVIATION_DECAY_FACTOR_PER_TICK

/**
 * Predicts future blood glucose levels based on current blood glucose, treatment history
 * and calculated treatment decisions.
 *
 * All insulin and carb calculations are modeled as future impacts (Blood Glucose Impact, BGI),
 * this allows us to cache those values until we have new treatment input.
 *
 * When a new blood glucose value is received, these cached BGI values are projected forward from
 * the current reading. Recalculation of the underlying BGI values is only necessary if the
 * user profile (ISF, CR, Basal) or the treatment history changes.
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

    inline fun invalidateCarbsCache() {
        forEach { _, tickState ->
            tickState.effectiveCarbs = null
        }
    }

    inline fun invalidateInsulinCache() {
        forEach { _, tickState ->
            tickState.effectiveInsulin = null
        }
    }

    inline fun invalidateTherapySettingsCache() {
        forEach { _, tickState ->
            tickState.isf = null
            tickState.cr = null
            tickState.basalRateUph = null
        }
    }

    /**
     * Calculates the "prediction stages" 2-4 of the future ticks in our prediction model.
     * @return `true` if values have changed compared to the previous settings; next stages must be
     * calculated. Else `false`.
     */
    suspend inline fun calculate(
        currentBG: BgValue,
        avgCurrentDeviationPerTick: BgDelta,
        meals: List<MealEntry>,
        insulinApplications: List<InsulinApplication>,
        dia: Minutes,
        insulinPeak: Minutes,
        therapyManager: TherapyManager,
        carbsInsulinCalculationModel: CarbsInsulinCalculationModel
    ) {
        var bg = currentBG
        var deviationPerTick = avgCurrentDeviationPerTick
        forEachS(from = timeline.getNowTick() + 1, to = getLastTick()) { tick, state ->
            if (state.effectiveCarbs == null) {
                state.effectiveCarbs = carbsInsulinCalculationModel.carbAbsorption(
                    meals,
                    timeline.timestamp(tick)
                )
            }
            if (state.effectiveInsulin == null) {
                state.effectiveInsulin = carbsInsulinCalculationModel.effectiveInsulin(
                    insulinApplications = insulinApplications,
                    timestamp = timeline.timestamp(tick),
                    dia = dia,
                    peak = insulinPeak
                )
            }
            val timestamp = timeline.timestamp(tick)

            if (state.isf == null || state.cr == null || state.basalRateUph == null) {
                state.isf = therapyManager.getIsfFactor(timestamp)
                state.cr = therapyManager.getCrFactor(timestamp)
                state.basalRateUph = therapyManager.getBasalPerHour(timestamp)
            }

            val insulinEquivalentOfCarbs = convertToUnitsFromCarbs(state.effectiveCarbs!!, state.cr!!)
            // Absolute BGI: Carbs - Insulin
            // We don't include the basal requirement in BGI, so a BGI of 0 should
            // come true if we deliver standard basal rates.
            val bgi = (insulinEquivalentOfCarbs - state.effectiveInsulin!!) * state.isf!!

            if (bgi != state.bgi) {
                state.bgi = bgi
            }

            // Ease out the deviation
            deviationPerTick *= DEVIATION_DECAY_FACTOR_PER_TICK

            bg = bg + state.bgi + deviationPerTick
            state.predictedBg = bg
        }
    }

    inline fun advanceToTick(newAnchor: Tick) {
        rollingHistory.moveWindowTo(newAnchor)
    }

    inline fun latestPredictionTickState(): PredictionTickState? {
        return rollingHistory.tryGetTickState(rollingHistory.getLastTick())
    }

    inline fun findBgMin(startAt: Tick, until: Tick): PredictionTickState? {
        var min: BgValue = BgValue.INVALID
        var minState: PredictionTickState? = null
        rollingHistory.forEach(from = startAt, to = until) { tick, state ->
            val currentBg = state.predictedBg
            if (min.isInvalid() || (currentBg.isValid() && currentBg.mgdl < min.mgdl)) {
                min = currentBg
                minState = state
            }
        }
        return minState
    }

    fun findNext(startAt: Tick, until: Tick, predicate: (PredictionTickState) -> Boolean): PredictionTickState? {
        return rollingHistory.findForward(startTick = startAt, endTick = until, predicate = predicate)
    }

    suspend fun findNextS(startAt: Tick, predicate: suspend (PredictionTickState) -> Boolean): PredictionTickState? {
        return rollingHistory.findForwardS(startTick = startAt, predicate)
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
        rollingHistory.forEachS(from = from, to = to, action = action)
    }
}