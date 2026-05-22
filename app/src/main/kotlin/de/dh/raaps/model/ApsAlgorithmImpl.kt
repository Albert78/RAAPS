package de.dh.raaps.model

class ApsAlgorithmImpl: ApsAlgorithm {
    override suspend fun initialize(
        predictionModel: PredictionModel,
        metabolicEventsModel: MetabolicEventsModel,
        carbsInsulinCalculation: CarbsInsulinCalculation
    ) {
        metabolicEventsModel.load()
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
    }

    override suspend fun recalculate(
        predictionModel: PredictionModel,
        bgReadingsHistory: BgReadingHistory,
        carbsInsulinCalculation: CarbsInsulinCalculation
    ) {
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
}