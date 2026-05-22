package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp

class ApsAlgorithmImpl(
    val timeline: ApsTimeline,
    val metabolicEventsModel: MetabolicEventsModel,
    val bgReadingsHistory: RecentBgReadingsHistory,
    val predictionModel: PredictionModel,
    val carbsInsulinCalculation: CarbsInsulinCalculation
): ApsAlgorithm {
    val sampledBgReadings = SampledBgReadings(timeline, bgReadingsHistory)

    /**
     * Calculate the deviation between previous forecasts and the blood glucose values actually received.
     * This is done by comparing recent blood glucose slopes to the predicted BGI (Blood Glucose Impact) values.
     * Deviations typically occur due to unannounced meals or variations in insulin/carb sensitivity
     * compared to the prediction model.
     */
    private fun calcAvgDeviation(): BgDelta {
        // Since our readings history contains bg readings which are 1) not sampled and 2) we might have
        // more than 1 reading per tick (e.g. if we get one reading per minute in a 5 minutes sample interval),
        // we first calculate the average bg value in each tick:

        // Last value in BgReadingHistory should be at nowTick + 1, so after the end of the iterated
        // tick range, we should have a last reading
        val ticksToAvgValues = (predictionModel.getFirstTick()..timeline.getNowTick())
            .map { tick ->
                val tick1 = timeline.timestamp(tick)
                val tick2 = timeline.timestamp(tick + 1)

                tick to bgReadingsHistory.avgBgValue(
                    tick1,
                    true,
                    tick2,
                    false
                )
            }

        // Each slope is the difference of two bg values:
        val slopes = ticksToAvgValues.zipWithNext { a, b ->
            val slope = if (a.second == null || b.second == null) null else (b.second!! - a.second!!)
            // The slope must be associated with the tick of the first element - it will be compared later
            // with the BGI (which contains the prediction of the slope to the next value)
            a.first to slope
        }
        if (slopes.isEmpty()) return BgDelta(0)
        var numValues = 0
        val sumDeviations = slopes
            .sumOf { (tick, slope) ->
                val predictionTickState = predictionModel.rollingHistory.tryGetTickState(tick)
                if (predictionTickState == null || slope == null) return@sumOf 0
                numValues++
                // The difference of the real bg slope and the predicted bg slope (= bgi) is the deviation
                (slope - predictionTickState.bgi).mgdl.toInt()
            }
        return BgDelta.fromMgDl(sumDeviations / numValues)
    }

    override suspend fun recalculate(currentBG: BgReading) {
        bgReadingsHistory.add(currentBG)

        // Sample our unaligned input values to our fixed sample buffer.
        // This makes us independent of different input frequencies for BG values and minimizes
        // calculation costs when we access the input BG readings.
        sampledBgReadings.sampleAvgValues()

        // Most of our calculations below are based on a static prediction model for insulin and carbs.
        // To react to dynamic changes (e.g. unannounced snacks), we calculate an average deviation of our
        // model predictions to the real blood glucose.
        // The deviation is the actual slope of bg values minus the bgi, which is the predicted slope.
        val avgCurrentDeviation = calcAvgDeviation()
        // Assumption: That deviation will be continued in the future but will fade away

        // Filter BG values to avoid big jumps caused by measurement errors
        bgReadingsHistory.getReadings()
        SavitzkyGolayFilterWin5Order2.calculateFilteredValue()

        predictionModel.calculateBgPredictions(currentBG, avgCurrentDeviation)

        TODO: Weiter Code aus ApsAlgorithmTest übernehmen
        // IOB, COB, BG kommen aus der Vergangenheit
        // BG Predictions berechnen für verschiedene Szenarien
        // Bewerten aufgrund von Aggressivitätseinstellungen

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
            val carbsInsulinCalculation = CarbsInsulinCalculation(tickInterval)
            predictionModel.calculateInsulinAndCarbs(
                metabolicEventsModel,
                carbsInsulinCalculation
            )
            val bgReadingsHistory = RecentBgReadingsHistory(DEVIATION_TIME_BASE)
            bgReadingsHistory.setAll(readingsHistory)
            return ApsAlgorithmImpl(
                timeline = timeline,
                metabolicEventsModel = metabolicEventsModel,
                bgReadingsHistory = bgReadingsHistory,
                predictionModel = predictionModel,
                carbsInsulinCalculation
            )
        }
    }
}