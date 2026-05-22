package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.plus
import de.dh.raaps.common.model.data.times
import de.dh.raaps.common.model.pump.PumpActions
import kotlin.math.max

// TODO: Document the models needed for calculation, document calculation algorithm
class ApsAlgorithmImpl(
    val timeline: ApsTimeline,
    val metabolicEventsModel: MetabolicEventsModel,
    val bgReadingsHistory: RecentBgReadingsHistory,
    val predictionModel: PredictionModel,
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    val therapyModel: TherapyModel
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

            val predictionTickState = predictionModel.rollingHistory.tryGetTickState(tick) ?: continue

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

        // Move the prediction window forward. It always covers roughly our BG readings history cache
        // to be able to calculate deviations between the static predictions and the actual readings.
        predictionModel.advanceToTimestamp(Timestamp.now().minus(PRESERVE_PREDICTIONS_PAST_TIME))

        // Materialize assumed ISF and IC values, update predicted BGI if changed, update BG if changed
        val continueCalculations = predictionModel.calculatePredictionStates_2_3_4(currentBgFiltered, avgCurrentDeviation, therapyModel)

        if (!continueCalculations) {
            // Predictions have not changed from the previous ones, just keep all decisions already made.
            return
        }

        val pumpActionsBuilder = PumpActions.Builder()
        pumpActionsBuilder.clearTempBasals()
        predictionModel.clearTempBasalsStage_5()

        val targetBg = therapyModel.getTarget()

        // Goal 1: Get out of a current or impending low by lowering your basal rate early
        // Find the next occurrence where the value falls below the minimum; find the minimum with time
        predictionModel.findNext(startAt = Timestamp.now()) {
            it.predictedBg < targetBg.lower
        }?.let { minStart ->
            val basalRate = therapyModel.getBasalPerHour(minStart.timestamp)
            val isf = therapyModel.getIsfFactor(minStart.timestamp)
            val zeroTempDeltaBgPerHour = (basalRate * isf).mgdl.toDouble()
            val nextMin = predictionModel.findNextBgMin(startAt = minStart.timestamp, returnLatestIfFalling = true)!!
            val bgError = targetBg.lower - nextMin.bg + MIN_BG_SAFETY_MARGIN
            val startZeroTemp = Timestamp(
                max(
                    nextMin.timestamp.minusHours(bgError.mgdl / zeroTempDeltaBgPerHour).ms,
                    Timestamp.now().ms
                )
            )
            pumpActionsBuilder.setTempBasal(0.0, startZeroTemp, nextMin.timestamp)
            predictionModel.setTempBasalDeviationStage_5(-basalRate, startZeroTemp, nextMin.timestamp)
        }

        predictionModel.calculatePredictionsWithTempBasal()

        // Goal 2: Correct the next upcoming high by administering insulin early, without subsequently dropping into a low
        // Find the next high along with the time, then find the next low along with the time

        TODO: Weiter Code aus ApsAlgorithmTest übernehmen

        // Actions: Insulin. Aber wie mitteilen? Wir können eine optimale Kurve berechnen, das kann die Pumpe aber ggf nicht.
        // Einstellung des Benutzers:
        // - SMB nutzen? Vorteil: Bessere Steuerung. Nachteil: APS muss immer da sein, höhere Rechenlast.
        // - Pumpen-EB bzw. Dual-Bolus nutzen: Es passiert mehr in der Pumpe. Hierzu benötigen wir Zugriff auf die Pumpen-Fähigkeiten
        // Ausgabe dann: Insulinplan. Das Pumpenplugin muss das umsetzen.
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
                carbsInsulinCalculationModel
            )
        }
    }
}