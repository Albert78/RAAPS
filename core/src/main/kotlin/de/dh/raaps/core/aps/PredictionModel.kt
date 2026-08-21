package de.dh.raaps.core.aps

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculator
import de.dh.raaps.common.model.convertToBgDeltaFromUnits
import de.dh.raaps.common.model.convertToInsulinAmountFromCarbs
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.ApsAlgorithmImpl.Companion.DEVIATION_DECAY_FACTOR_PER_TICK
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val mutex = Mutex()

    private var rollingHistory = RollingPredictionWindow(
        predictionWindowHours = predictionWindowHours,
        timeline = timeline,
        timeline.tick(Timestamp.now())
    )

    suspend fun getFirstTick() = mutex.withLock { rollingHistory.getFirstTick() }
    suspend fun getLastTick() = mutex.withLock { rollingHistory.getLastTick() }

    suspend fun initializeToTick(newAnchorTimestamp: Timestamp) = mutex.withLock {
        rollingHistory.init(timeline.tick(newAnchorTimestamp))
    }

    /**
     * Executes a block with access to a specific [ReadOnlyPredictionTickState].
     * Returns the result of the block, or null if the tick is not in the window.
     */
    suspend fun <T> withTickState(tick: Tick, block: suspend (ReadOnlyPredictionTickState) -> T): T? = mutex.withLock {
        val state = rollingHistory.tryGetTickState(tick) ?: return null
        return block(state)
    }

    suspend fun invalidateCarbsCache() = mutex.withLock {
        rollingHistory.forEachS { _, tickState ->
            tickState.effectiveCarbs = null
        }
    }

    suspend fun invalidateInsulinCache() = mutex.withLock {
        rollingHistory.forEachS { _, tickState ->
            tickState.effectiveInsulin = null
        }
    }

    suspend fun invalidateTherapySettingsCache() = mutex.withLock {
        rollingHistory.forEachS { _, tickState ->
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
    suspend fun calculate(
        // Intentionally use Short data type to rule out invalid BG values
        currentBGMgDl: Short,
        avgCurrentDeviationPerTick: BgDelta,
        meals: List<MealEntry>,
        insulinApplications: List<InsulinApplication>,
        dia: Minutes,
        insulinPeak: Minutes,
        therapyManager: TherapyManager,
        carbsInsulinCalculator: CarbsInsulinCalculator
    ) = mutex.withLock {
        var bg = BgValue.fromMgDl(currentBGMgDl)
        val nowTick = timeline.getNowTick()
        rollingHistory.tryGetTickState(nowTick)?.predictedBg = bg

        var deviationPerTick = avgCurrentDeviationPerTick
        var runningCumulatedBasal = InsulinAmount.ZERO
        rollingHistory.forEachS(from = rollingHistory.getFirstTick(), to = rollingHistory.getLastTick()) { tick, state ->
            if (state.effectiveCarbs == null) {
                state.effectiveCarbs = carbsInsulinCalculator.carbAbsorption(
                    meals,
                    timeline.timestamp(tick)
                )
            }
            if (state.effectiveInsulin == null) {
                state.effectiveInsulin = carbsInsulinCalculator.effectiveInsulin(
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

            val insulinEquivalentOfCarbs = convertToInsulinAmountFromCarbs(state.effectiveCarbs!!, state.cr!!)

            // BGI calculation

            val basalUnitsPerTick = state.basalRateUph!! * (timeline.tickDuration.value.toDouble() / 60.0)

            // At steady state, the activity of a continuous basal rate is equal to its delivery rate.
            // This simplification allows us to subtract the scheduled basal units directly from
            // the total effective insulin to find the "net" insulin impact.

            // 1. Net insulin is the delivered insulin minus the basal requirement.
            //    If we are on standard basal, net insulin is 0.
            //    If we are on low temp basal, net insulin is negative.
            val netInsulin = state.effectiveInsulin!! - basalUnitsPerTick

            // 2. BGI is (Carbs - NetInsulin) * ISF.
            //    This results in BGI 0 for standard basal (no carbs) and BGI > 0 for low temp basal (no carbs).
            val bgi = convertToBgDeltaFromUnits(insulinEquivalentOfCarbs - netInsulin, state.isf!!)

            if (bgi != state.bgi) {
                state.bgi = bgi
            }

            if (tick > nowTick) {
                // Calculations that only apply to the future prediction window
                runningCumulatedBasal += basalUnitsPerTick
                state.cumulatedBasalInsulin = runningCumulatedBasal

                // Ease out the deviation
                deviationPerTick *= DEVIATION_DECAY_FACTOR_PER_TICK

                bg = bg + state.bgi + deviationPerTick
                state.predictedBg = bg
            } else {
                // Clear values that are only meaningful for future predictions
                state.cumulatedBasalInsulin = InsulinAmount.ZERO
                if (tick < nowTick) {
                    state.predictedBg = BgValue.INVALID
                }
            }
        }
    }

    suspend fun advanceToTick(newAnchor: Tick) = mutex.withLock {
        rollingHistory.moveWindowTo(newAnchor)
    }

    /**
     * Executes a block with access to the last [ReadOnlyPredictionTickState] in the window.
     */
    suspend fun <T> withLatestPredictionTickState(block: (ReadOnlyPredictionTickState) -> T): T? = mutex.withLock {
        rollingHistory.tryGetTickState(rollingHistory.getLastTick())?.let(block)
    }

    /**
     * Finds the state with the minimum BG value and executes the block with it.
     */
    suspend fun <T> findBgMin(startAt: Tick, until: Tick, block: (ReadOnlyPredictionTickState) -> T): T? = mutex.withLock {
        var min: BgValue = BgValue.INVALID
        var minState: PredictionTickState? = null
        rollingHistory.forEachS(from = startAt, to = until) { _, state ->
            val currentBg = state.predictedBg
            if (min.isInvalid() || (currentBg.isValid() && currentBg.mgdl < min.mgdl)) {
                min = currentBg
                minState = state
            }
        }
        minState?.let(block)
    }

    /**
     * Finds the next state matching the predicate and executes the block with it.
     */
    suspend fun <T> findNext(
        startAt: Tick,
        until: Tick? = null,
        predicate: suspend (ReadOnlyPredictionTickState) -> Boolean,
        block: suspend (ReadOnlyPredictionTickState) -> T
    ): T? = mutex.withLock {
        rollingHistory.findForwardS(startTick = startAt, endTick = until, predicate = predicate)?.let { block(it) }
    }

    suspend fun forEach(
        from: Tick? = null,
        to: Tick? = null,
        action: suspend (Tick, ReadOnlyPredictionTickState) -> Unit
    ) = mutex.withLock {
        val f = from ?: rollingHistory.getFirstTick()
        val t = to ?: rollingHistory.getLastTick()
        rollingHistory.forEachS(from = f, to = t, action = action)
    }
}