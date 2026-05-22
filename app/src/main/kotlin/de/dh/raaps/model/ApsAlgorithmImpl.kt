package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.plus
import de.dh.raaps.common.model.data.times
import kotlin.compareTo
import kotlin.math.min

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
        val bgPredictionChanged = predictionModel.calculatePredictions(currentBgFiltered, avgCurrentDeviation, therapyModel)

        val targetBg = therapyModel.getTarget()

        // 1. Goal: Get out of a current or impending low by lowering your basal rate early
        // Find the next occurrence where the value falls below the minimum; find the minimum with time
        predictionModel.findNext(startAt = Timestamp.now()) {
            it.predictedBg < targetBg.lower
        }?.let { minStart ->
            val basalRate = therapyModel.getBasalPerHour(minStart.timestamp)
            val isf = therapyModel.getIsfFactor(minStart.timestamp)
            val lowTempDeltaBgPerTick = (basalRate * isf).mgdl.toDouble() / timeline.ticksPerHour()
            val nextMin = predictionModel.findNextBgMin(startAt = minStart.timestamp, returnLatestIfFalling = true)!!
            val bgError = targetBg.lower - nextMin.bg
            val minNumTicksForCorrection = min(
                (bgError.mgdl / lowTempDeltaBgPerTick).toInt(),
                ((nextMin.timestamp - Timestamp.now()) / timeline.tickSizeMs).toInt())
        }

        val nextMin: PredictionPoint = prediction.findNextBgMin(startAt = nextMinStart.timestamp)
        // Korrektur durch Temp-Basal absenken von Minimum zurückgerechnet, um Error weg zu bekommen
        val startLowTemp = TODO: vom Minimum zurückgerechnet
        // TODO: Plane Low temp ein
        prediction.setLowTemp(from = startLowTemp, to = nextMin)
        prediction.updateBGIPredictions(startAt = startLowTemp)



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
            predictionModel.calculateInsulinAndCarbs(
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