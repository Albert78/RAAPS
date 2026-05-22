package de.dh.raaps.model

interface ApsAlgorithm {
    suspend fun initialize(
        predictionModel: PredictionModel,
        metabolicEventsModel: MetabolicEventsModel,
        carbsInsulinCalculation: CarbsInsulinCalculation
    )

    suspend fun recalculate(
        predictionModel: PredictionModel,
        bgReadingsHistory: BgReadingHistory,
        carbsInsulinCalculation: CarbsInsulinCalculation
    )
}