package de.dh.raaps.model

import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.max
import de.dh.raaps.common.model.data.times
import de.dh.raaps.common.model.pump.ApsPumpModel
import de.dh.raaps.common.model.pump.PumpActionsBuilder

// TODO: Document the models needed for calculation, document calculation algorithm
class ApsAlgorithmImpl(
    val timeline: ApsTimeline,
    val metabolicEventsModel: MetabolicEventsModel,
    val bgReadingsHistory: RecentBgReadingsHistory,
    val predictionModel: PredictionModel,
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    val therapyModel: TherapyModel,
    val pumpModel: ApsPumpModel
): ApsAlgorithm {
    val sampledBgReadings = SampledBgReadings(timeline, bgReadingsHistory)

    /**
     * Calculate the deviation between previous forecasts and the blood glucose values actually received.
     * This is done by comparing recent blood glucose slopes to the predicted BGI (Blood Glucose Impact) values.
     * Deviations typically occur due to unannounced meals or variations in insulin/carb sensitivity
     * compared to the prediction model.
     */
    private fun calcAvgDeviation(): BgDelta {
        val startTick = predictionModel.getFirstTick()
        val endTick = timeline.getNowTick()

        var sumDeviationsMgdl = 0
        var numValues = 0

        for (t in startTick.value until endTick.value) {
            val tick = Tick(t)
            val bgA = sampledBgReadings.getAt(tick)
            if (!bgA.isValid()) continue
            val bgB = sampledBgReadings.getAt(Tick(t + 1))
            if (!bgB.isValid()) continue

            val predictionTickState = predictionModel.tryGetTickState(tick) ?: continue

            // The difference of the real bg slope and the predicted bg slope (= bgi) is the deviation
            val actualSlope = bgB - bgA
            sumDeviationsMgdl += (actualSlope - predictionTickState.bgi).mgdl.toInt()
            numValues++
        }

        return if (numValues == 0) BgDelta(0) else BgDelta.fromMgDl(sumDeviationsMgdl / numValues)
    }

    override suspend fun recalculateForNewBgValue(currentBG: BgReading) {
        bgReadingsHistory.add(currentBG)

        // Sample our unaligned input values to our fixed sample buffer.
        // This makes us independent of different input frequencies for BG values and minimizes
        // calculation costs when we access the input BG readings.
        sampledBgReadings.sampleAvgValues()

        // Filter BG values to avoid big jumps caused by measurement errors.
        // If we have enough input values, we can use the better SavitzkyGolay filter, else fallback to PTWMA
        var currentBgFiltered = sampledBgReadings.calculateSavitzkyGolayEndBorder3()
        if (currentBgFiltered.isInvalid()) {
            currentBgFiltered = bgReadingsHistory.calculatePTWMA(0.7)
        }

        if (currentBgFiltered.isInvalid()) {
            // We cannot make any new predictions if we don't have fresh values.
            // So we'll stick with the old ones.
            return
        }

        // Most of our calculations below are based on a static prediction model for insulin and carbs.
        // To react to dynamic changes (e.g. unannounced snacks), we calculate an average deviation of our
        // model predictions to the real blood glucose.
        // The deviation is the actual slope of bg values minus the bgi, which is the predicted slope.
        val avgCurrentDeviation = calcAvgDeviation()
        // Assumption: That deviation will be continued in the future but will fade away

        val now = Timestamp.now()

        // Move the prediction window forward. It always covers roughly our BG readings history cache
        // to be able to calculate deviations between the static predictions and the actual readings.
        predictionModel.advanceToTimestamp(now.minus(PRESERVE_PREDICTIONS_PAST_TIME))

        // Materialize assumed ISF and IC values, update predicted BGI if changed, update BG if changed
        val continueCalculations = predictionModel.calculatePredictionStates_2_3_4(currentBgFiltered, avgCurrentDeviation, therapyModel)

        if (!continueCalculations) {
            // Predictions have not changed from the previous ones, just keep all decisions already made.
            return
        }

        val pumpActionsBuilder = PumpActionsBuilder()

        try {
            pumpActionsBuilder.clearTempBasals()
            predictionModel.clearTempBasalsStage_5()

            val targetBgRange = therapyModel.getTarget()
            val pumpInsulinType = therapyModel.getPumpInsulinType()
            val insulinPeakMs = pumpInsulinType.peak.inMs()

            // Goal 1: Get out of a current or impending low by lowering your basal rate early
            // Find the next occurrence where the value falls below the minimum; find the minimum with time
            predictionModel.findNext(startAt = now) {
                it.predictedBg < targetBgRange.lower
            }?.let { firstLowPoint ->
                val basalRate = therapyModel.getBasalPerHour(firstLowPoint.timestamp)
                val isf = therapyModel.getIsfFactor(firstLowPoint.timestamp)
                val zeroTempDeltaBgPerHour = (basalRate * isf).mgdl.toDouble()
                val nextMin = predictionModel.findNextBgMin(startAt = firstLowPoint.timestamp, returnLatestIfFalling = true) ?: return@let
                val bgError = targetBgRange.lower - nextMin.bg + MIN_BG_SAFETY_MARGIN
                val startZeroTemp = max(
                    nextMin.timestamp.minusHours(bgError.mgdl / zeroTempDeltaBgPerHour).
                        // We must drop the basal long time before the next minimum 1) to avoid falling to min and 2) because of the long insulin effect.
                        // How long would be the best? I don't know. Let's take twice the peak time as first approximation.
                        minusMs(insulinPeakMs * 2),
                    now
                )
                // We could actually start increasing the basal rate sooner than at the minimum, but when?
                pumpActionsBuilder.setTempBasal(0.0, startZeroTemp, nextMin.timestamp)
                predictionModel.setTempBasalDeviationStage_5(-basalRate, timeline.tick(startZeroTemp), timeline.tick(nextMin.timestamp))
            }

            predictionModel.calculatePredictionsWithTempBasalStage_6()

            // Goal 2: Correct the next upcoming high by administering insulin early, without subsequently dropping into a low
            // Find the next high along with the time, then find the next low along with the time
            predictionModel.findNext(startAt = now) {
                it.predictedBg2 > targetBgRange.upper
            }?.let { firstHighPoint ->
                val nextMax = predictionModel.findNextBgMax(startAt = firstHighPoint.timestamp, returnLatestIfRising = true) ?: return@let
                val targetBg = (targetBgRange.lower + targetBgRange.upper) / 2.0
                val bgError = nextMax.bg - targetBg
                if (bgError > BgDelta(0)) {
                    // Try to reduce BG by bgError

                    // Attempts to find the best possible insulin doses and timing for correction with limited computational effort
                    // Basic heuristic: We always try to administer insulin as early as possible to keep blood glucose levels low.
                    // In the worst case, this can lead to larger spikes.

                    val minAfterMax = predictionModel.findNextBgMin(startAt = nextMax.timestamp, returnLatestIfFalling = true)

                    // Insulin correction amount: Try correction based on bgError, but limited by lowBuffer so
                    // that we don't become low due to our IOB
                    val lowBuffer = minAfterMax?.let { minAfterMax.bg - targetBgRange.lower } // We have that much leeway for the correction
                    val maxCorrection = if (lowBuffer == null)
                        bgError
                    else
                        BgDelta.fromMgDl(minOf(bgError.mgdl, lowBuffer.mgdl))

                    val isf = therapyModel.getIsfFactor(firstHighPoint.timestamp)
                    val maxCorrectionInsulinUnits = maxCorrection / isf

                    // We've found the next maximum before, so we can assume a monotonous rising BG curve.

                    // Now try to find the correct time allocation of one or more parts of the calculated
                    // correction insulin, which depends on the slope of the BG curve.

                    // The next part checks if we can apply all necessary insulin in a single application
                    // or if we must divide the insulin in multiple parts because the blood glucose
                    // raises too slowly.

                    // As a heuristic, we start as early as possible.
                    // We choose as first insulin application the insulin peak interval before raising high,
                    // which is a heuristic which seems well to me but could be improved in the future.

                    val insulinTime = max(firstHighPoint.timestamp.minusMs(insulinPeakMs), now)
                    if (insulinTime > now.plusMinutes(10)) {
                        // Don't schedule the insulin too early, we never know what will happen...
                        // (User could change the target temporarily, user could do sports, ...)
                        return@let
                    }

                    var insulinUnits = maxCorrectionInsulinUnits

                    // Simulate an insulin application of errorCorrectionInsulinUnits at insulinTime and check
                    // if we fall under the low threshold. If yes, reduce the insulin amount for this
                    // first insulin administration.
                    // To calculate the right amount, we reduce the application until we don't find
                    // any more ticks where we will drop down the low mark.
                    predictionModel.forEach(to = timeline.tick(nextMax.timestamp)) { tick, state ->
                        val bg = state.predictedBg2
                        if (bg == BgValue.INVALID) return@forEach
                        val spentInsulin = carbsInsulinCalculationModel.spentInsulin(
                            insulinUnits = insulinUnits,
                            insulinType = pumpInsulinType,
                            insulinApplicationTimestamp = insulinTime,
                            timestamp = timeline.timestamp(tick)
                        )
                        val bgDeltaFromTestInsulin = spentInsulin * state.isf
                        val resultBG = state.predictedBg2 - bgDeltaFromTestInsulin
                        val bgError = targetBgRange.lower - resultBG
                        if (bgError > BgDelta(0)) {
                            // We would drop too low, reduce insulin
                            insulinUnits -= bgError / state.isf
                        }
                    }
                    val insulinApplication = InsulinApplication(
                        timestamp = insulinTime,
                        insulinUnits = insulinUnits,
                        insulinType = pumpInsulinType
                    )
                    pumpActionsBuilder.addInsulinApplication(insulinUnits, insulinTime)
                    metabolicEventsModel.addInsulinApplication(insulinApplication)
                    predictionModel.calculatePredictionStage_1(metabolicEventsModel, carbsInsulinCalculationModel)
                }
            }
        } finally {
            pumpActionsBuilder.execute(pumpModel)
        }
    }

    companion object {
        const val PREDICTION_WINDOW_HOURS = 10
        val DEVIATION_TIME_BASE = Minutes(30)
        val PRESERVE_PREDICTIONS_PAST_TIME = DEVIATION_TIME_BASE

        const val DEVIATION_DECAY_FACTOR_PER_TICK = 0.9

        val MIN_BG_SAFETY_MARGIN = BgDelta(10)

        suspend fun create(
            metabolicEventsModel: MetabolicEventsModel,
            readingsHistory: List<BgReading>,
            therapyModel: TherapyModel,
            pumpModel: ApsPumpModel,
            tickInterval: Minutes
        ): ApsAlgorithm {
            val timeline = ApsTimeline(tickInterval)
            val predictionModel = PredictionModel(
                predictionWindowHours = PREDICTION_WINDOW_HOURS,
                timeline = timeline
            )
            predictionModel.initializeToTick(Timestamp.now().minus(PRESERVE_PREDICTIONS_PAST_TIME))
            val carbsInsulinCalculationModel = CarbsInsulinCalculationModel(tickInterval)
            predictionModel.calculatePredictionStage_1(
                metabolicEventsModel,
                carbsInsulinCalculationModel
            )
            val bgReadingsHistory = RecentBgReadingsHistory(DEVIATION_TIME_BASE)
            bgReadingsHistory.setAll(readingsHistory)
            return ApsAlgorithmImpl(
                timeline = timeline,
                metabolicEventsModel = metabolicEventsModel,
                bgReadingsHistory = bgReadingsHistory,
                predictionModel = predictionModel,
                carbsInsulinCalculationModel,
                therapyModel,
                pumpModel
            )
        }
    }
}