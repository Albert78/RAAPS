package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp

class ApsAlgorithmImpl(
    val timeline: ApsTimeline,
    val metabolicEventsModel: MetabolicEventsModel,
    val bgReadingsHistory: BgReadingHistory,
    val predictionModel: PredictionModel,
    val carbsInsulinCalculation: CarbsInsulinCalculation
): ApsAlgorithm {
    private fun calcAvgDeviation(): Double {
        // Last value in BgReadingHistory should be at nowTick + 1, so at the end of the iterated
        // range, we have 2 more ticks with readings
        for (tick in predictionModel.getFirstTick()..timeline.getNowTick() - 1) {
            val tick1 = timeline.timestamp(tick)
            val tick2 = timeline.timestamp(tick + 1)
            val tick3 = timeline.timestamp(tick + 2)
            val avgBgValue1 = bgReadingsHistory.avgBgValue(
                tick1,
                true,
                tick2,
                false
            )
            val avgBgValue2 = bgReadingsHistory.avgBgValue(
                tick2,
                true,
                tick3,
                false
            )
            val slope = avgBgValue2 - avgBgValue1
        }


        var ts = Timestamp.now().minus(deviationTimeBase)
        weiter()
    }

    override suspend fun recalculate(bg: BgReading) {
        bgReadingsHistory.add(bg)

        // Most of our calculations below are based on a static prediction model for insulin and carbs.
        // To react to dynamic changes (e.g. unannounced snacks), we calculate an average deviation of our
        // model predictions to the real blood glucose.
        // The deviation is the actual slope of bg values minus the bgi, which is the predicted slope.
        val avgCurrentDeviation = calcAvgDeviation()
        // Assumption: That deviation will be continued in the future but will fade away

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
            val meals = metabolicEventsModel.getMeals()
            val insulinApplications = metabolicEventsModel.getInsulinApplications()
            val carbsInsulinCalculation = CarbsInsulinCalculation(tickInterval)
            predictionModel.forEach { tick, tickState ->
                tickState.initializeToTick(tick)
                // We only need to initialize insulin and carbs, since they only depend on the treatments.
                // They only need to be touched when we have more meals or insulin applications.
                // All other data is calculated in each tick cycle.
                tickState.effectiveInsulin = carbsInsulinCalculation.effectiveInsulin(
                    insulinApplications,
                    timeline.timestamp(tick)
                )
                tickState.effectiveCarbs = carbsInsulinCalculation.carbAbsorption(
                    meals,
                    timeline.timestamp(tick)
                )
            }
            val bgReadingsHistory = BgReadingHistory(DEVIATION_TIME_BASE)
            bgReadingsHistory.addAll(readingsHistory)
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