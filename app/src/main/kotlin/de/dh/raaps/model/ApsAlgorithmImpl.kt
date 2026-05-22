package de.dh.raaps.model

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp

class ApsAlgorithmImpl(
    val metabolicEventsModel: MetabolicEventsModel,
    val bgReadingsHistory: BgReadingHistory,
    val predictionModel: PredictionModel,
    val tickInterval: Minutes
): ApsAlgorithm {
    private val carbsInsulinCalculation: CarbsInsulinCalculation =
        CarbsInsulinCalculation(tickInterval)

    override suspend fun recalculate() {
        TODO: Code aus ApsAlgorithmTest übernehmen
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
        val PRESERVE_PREDICTIONS_PAST_TIME = Minutes(30)

        suspend fun create(
            metabolicEventsModel: MetabolicEventsModel,
            bgReadingsHistory: BgReadingHistory,
            tickInterval: Minutes
        ): ApsAlgorithm {
            val predictionModel = PredictionModel(
                predictionWindowHours = PREDICTION_WINDOW_HOURS,
                tickInterval = tickInterval
            )
            predictionModel.initializeToTick(Timestamp.now().minusMinutes(PRESERVE_PREDICTIONS_PAST_TIME))
            val meals = metabolicEventsModel.getMeals()
            val insulinApplications = metabolicEventsModel.getInsulinApplications()
            predictionModel.forEach { tick, tickState ->
                tickState.initializeToTick(tick)
                // We only need to initialize insulin and carbs, since they only depend on the treatments.
                // They only need to be touched when we have more meals or insulin applications.
                // All other data is calculated in each tick cycle.
                tickState.effectiveInsulin = carbsInsulinCalculation.effectiveInsulin(
                    insulinApplications,
                    predictionModel.rollingHistory.timestamp(tick)
                )
                tickState.effectiveCarbs = carbsInsulinCalculation.carbAbsorption(
                    meals,
                    predictionModel.rollingHistory.timestamp(tick)
                )
            }
            return ApsAlgorithmImpl(
                metabolicEventsModel = metabolicEventsModel,
                bgReadingsHistory = bgReadingsHistory,
                predictionModel = predictionModel,
                tickInterval = tickInterval
            )
        }
    }
}