package de.dh.raaps.core.aps

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.convertToCarbsFromBgDelta
import de.dh.raaps.common.model.convertToUnitsFromBgDelta
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.times
import de.dh.raaps.core.repository.TreatmentRepository

class ApsAlgorithmImpl(
    val timeline: Timeline,
    val treatmentRepository: TreatmentRepository,
    val sampledBgReadings: SampledBgReadings,
    val predictionModel: PredictionModel,
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    val therapyManager: TherapyManager,
    val onCancelInsulinJobs: () -> Unit,
    val onDeliverBolus: (amount: InsulinAmount) -> Unit,
    val onSetTempBasal: (durationInHours: Int, unitsPerHour: Double) -> Unit,
    val onCarbsHint: (Int) -> Unit,
): ApsAlgorithm {
    // --- Time-based extensions for Tick to provide a Timestamp-like API ---
    private fun Tick.plusMinutes(minutes: Int): Tick = this + (minutes / timeline.tickDuration.value.toInt())
    private fun Tick.plusHours(hours: Int): Tick = plusMinutes(hours * 60)
    private fun Tick.minusMinutes(minutes: Int): Tick = this - (minutes / timeline.tickDuration.value.toInt())
    private fun Tick.minusHours(hours: Int): Tick = minusMinutes(hours * 60)
    private fun Tick.minusMs(ms: Long): Tick = this - (ms / timeline.tickSizeMs).toInt()
    private fun Tick.minus(minutes: Minutes): Tick = minusMinutes(minutes.value.toInt())
    private val Tick.timestamp get() = timeline.timestamp(this)

    /**
     * Called when meals occurred. This invalidates the cached effective carbs, which will be
     * re-evaluated on the next calculation.
     */
    override suspend fun updateMeals() {
        predictionModel.invalidateCarbsCache()
    }

    /**
     * Called when insulin events occurred. This invalidates the cached effective insulin, which will be
     * re-evaluated on the next calculation.
     */
    override suspend fun updateInsulin() {
        predictionModel.invalidateInsulinCache()
    }

    /**
     * Calculate the deviation between previous forecasts and the blood glucose values actually received.
     * This is done by comparing recent blood glucose slopes to the predicted BGI (Blood Glucose Impact) values.
     * Deviations typically occur due to unannounced meals or variations in insulin/carb sensitivity
     * compared to the prediction model.
     */
    private fun calcAvgDeviationPerTick(pastTime: Minutes): BgDelta {
        val endTick = timeline.getNowTick()
        val startTick = endTick.minus(pastTime)

        val bgStart = sampledBgReadings.getAt(startTick)
        if (!bgStart.isValid()) return BgDelta(0)

        val bgEnd = sampledBgReadings.getAt(endTick)
        if (!bgEnd.isValid()) return BgDelta(0)

        var sumDeviationsMgdl = 0.0
        val numValues = endTick.value - startTick.value

        if (numValues == 0) return BgDelta(0)

        predictionModel.forEach(from = startTick, to = endTick) { _, state ->
            sumDeviationsMgdl += state.bgi.mgdl
        }

        return BgDelta.fromMgDl((sumDeviationsMgdl / numValues).toInt())
    }

    override suspend fun recalculate() {
        // Filter BG values to avoid big jumps caused by measurement errors.
        // If we have enough input values, we can use the better SavitzkyGolay filter, else fallback to PTWMA
        var currentBgFiltered = sampledBgReadings.calculateSavitzkyGolayEndBorder3()
        if (currentBgFiltered.isInvalid()) {
            currentBgFiltered = sampledBgReadings.calculatePTWMA(0.7)
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
        val avgCurrentDeviationPerTick = calcAvgDeviationPerTick(DEVIATION_TIME_BASE)
        // Assumption: That deviation will be continued in the future but will fade away

        val nowTick = timeline.getNowTick()
        val now = Timestamp.now()

        // Move the prediction window forward. It always covers roughly our BG readings history cache
        // to be able to calculate deviations between the static predictions and the actual readings.
        predictionModel.advanceToTick(nowTick.minus(PRESERVE_PREDICTIONS_PAST_TIME))

        // Stage 1: Influence of meals and insulin - This is updated when metabolic events occur,
        // the data is reused in succeeding calculation calls until another metabolic event occurs.

        // Materialize assumed ISF and CR values, update predicted BGI if changed, update BG if changed
        predictionModel.calculate(currentBgFiltered, avgCurrentDeviationPerTick, therapyManager)

        // We cancel all insulin jobs including temporary basal rate - this means, the following code
        // must calculate:
        // - Carbs hints for low bg
        // - Deferred meal boluses
        // - Currection boluses
        // - Basal
        onCancelInsulinJobs()

        val bgSettings = therapyManager.getBgSettings()
        val targetBg = bgSettings.first
        val lowThreshold = bgSettings.second

        val pumpInsulinType = therapyManager.getPumpInsulinType()
        val insulinPeakTicks = timeline.inTicks(pumpInsulinType.peak)
        val isf = therapyManager.getIsfFactor(now)
        val cr = therapyManager.getCrFactor(now)

        // -------------------------------- Low handling -------------------------------------------

        // Step 1: Get out of a current or impending low by suggesting carbs
        // Find the next occurrence where the value falls below the minimum; find the minimum with time
        predictionModel.findNext(startAt = nowTick, until = nowTick.plusMinutes(LOW_WARNING_THRESHOLD.value.toInt())) {
            it.predictedBg < lowThreshold + LOW_BG_SAFETY_MARGIN
        }?.let { _ ->
            // TODO: This handling is for "normal" lows. We should also check if there is much more insulin
            // then carbs, e.g. BG prediction dropping fast. If this is the situation, tell the user to manually check the situation.
            onSetTempBasal(1, 0.0)

            // We're too low. Find out, now low we'll come to calculate the amount of suggested carbs.

            // Correct the minimum BG for twice the peak time of fast KE -> Don't look into the future too much
            val bgMin = predictionModel.findBgMin(startAt = nowTick, nowTick.plusMinutes(FAST_KE_DEFAULT_PEAK.value.toInt()))
            if (bgMin != null && bgMin.predictedBg.isValid()) {
                val bgError = targetBg - bgMin.predictedBg
                val carbsInG = convertToCarbsFromBgDelta(
                    bgDelta = bgError,
                    isf = isf,
                    cr = cr
                )
                onCarbsHint(carbsInG.toInt())
            }
            return // Stop further processing when we're currently low
        }

        val bgMinus15 = sampledBgReadings.getAt(nowTick.minusMinutes(15))
        val bg15Trend = if (bgMinus15.isValid() && currentBgFiltered.isValid())
            currentBgFiltered - bgMinus15
        else
            BgDelta(0)

        // Default basal rate to be adapted by the following code
        var basal = therapyManager.getBasalPerHour(now)

            // Step 2: Prevent further falling BG
        if (currentBgFiltered < targetBg && bg15Trend < BgDelta(0)) {
            // If Bg is under normal and further falling
            val bgErrorToTarget = targetBg - currentBgFiltered
            val correctionUnits = convertToUnitsFromBgDelta(bgErrorToTarget, isf)

            // Decrease basal
            basal = (basal - correctionUnits).coerceAtLeast(0.0)
            if (currentBgFiltered < targetBg - BgDelta(20)) {
                // Bg is too low and further falling -> Defer ongoing meal boluses
                onSetTempBasal(1, basal)
                return
            }
            // Else go on with decreased basal
        }

        var mealOrCorrectionBolus = InsulinAmount(0.0)

        // -------------------------------- Meal boluses -------------------------------------------

        // Step 3: Administer scheduled meal boluses
        val deferredBoluses = therapyManager.getDeferredBoluses()
        for (deferredBolus in deferredBoluses) {
            if (deferredBolus.timestamp < now) {
                mealOrCorrectionBolus = deferredBolus.amount
            }
        }

//        weiter: Für das Folgende brauchen wir eine Möglichkeit im TherapyManager, um die Basisdaten für Entscheidungen
//        zu locken, bis die Entscheidung getroffen ist. (z.B. Algorithmus so lange aufhalten, bis Nutzer Bolusscreen verlassen hat)
//        Hmm, wie machen wir die Interaktion mit dem Bolus-Screen? Soll der unabhängig von dem Algorithmus einfach eine
//        Bolusgabe anstoßen? Den Algorithmus einfach aussetzen, wenn er in der Zeit aktiv wird?
//        Z.B. mit Popup "Der Algorithmus wurde ausgesetzt während Nutzeraktion"

        // -------------------------------- High handling ------------------------------------------

        Todo() // Das folgende nur, wenn wir nicht unter starken Essenseinfluss sind, wo wir sowieso hoch kommen werden!
        // Step 4: Correct the next upcoming high by administering insulin early
        // Find the next high along with the time, then find the next low along with the time
        predictionModel.findNext(startAt = nowTick) {
            it.predictedBg > (targetBg - BgDelta.fromMgDl(10))
        }?.let { firstHighPoint ->
            val bgError = firstHighPoint.predictedBg - targetBg
            // Try to reduce BG by bgError

            var bolusAmount = minOf(bgError / isf, maxCorrectionAmount)

            // Safety validation: The calculated correction dose is verified against the
            // prediction model. If the simulated insulin action results in a projected
            // dip below the target range at any point within the prediction window,
            // the dose is iteratively reduced until safety is ensured.
            // The remaining correction will be calculated in one of the next cycles, when
            // BG has risen higher again.
            val settings = therapyManager.getActiveTherapySettings()
            val dia = settings.insulinProfile.dia
            val peak = settings.insulinProfile.peak

            predictionModel.forEach(to = nextMax.tick) { tick, state ->
                val bg = state.predictedBg2
                if (bg == BgValue.INVALID) return@forEach
                val spentInsulin = carbsInsulinCalculationModel.spentInsulin(
                    amount = bolusAmount,
                    applicationTimestamp = insulinTick.timestamp,
                    timestamp = timeline.timestamp(tick),
                    dia = dia,
                    peak = peak
                )
                val bgDeltaFromTestInsulin = spentInsulin * state.isf
                val resultBG = state.predictedBg2 - bgDeltaFromTestInsulin
                val bgError = lowThreshold - resultBG
                if (bgError > BgDelta(0)) {
                    // We would drop too low, reduce insulin
                    bolusAmount -= bgError / state.isf
                }
            }
            onDeliverBolus(InsulinAmount(bolusAmount))
        }
        Todo() // SetTemp
    }

    companion object {
        const val PREDICTION_WINDOW_HOURS = 10
        val DEVIATION_TIME_BASE = Minutes(30)
        val PRESERVE_PREDICTIONS_PAST_TIME = DEVIATION_TIME_BASE

        const val DEVIATION_DECAY_FACTOR_PER_TICK = 0.9

        val LOW_BG_SAFETY_MARGIN = BgDelta(10)

        suspend fun create(
            treatmentRepository: TreatmentRepository,
            sampledBgReadings: SampledBgReadings,
            therapyManager: TherapyManager,
            onCancelInsulinJobs: () -> Unit,
            onDeliverBolus: (amount: InsulinAmount) -> Unit,
            onSetTempBasal: (durationInHours: Int, unitsPerHour: Double) -> Unit,
            onCarbsHint: (Int) -> Unit,
            tickInterval: Minutes,
            carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
        ): ApsAlgorithm {
            val timeline = Timeline(tickInterval)
            val predictionModel = PredictionModel(
                predictionWindowHours = PREDICTION_WINDOW_HOURS,
                timeline = timeline
            )
            predictionModel.initializeToTick(Timestamp.now().minus(PRESERVE_PREDICTIONS_PAST_TIME))

            return ApsAlgorithmImpl(
                timeline = timeline,
                treatmentRepository = treatmentRepository,
                sampledBgReadings = sampledBgReadings,
                predictionModel = predictionModel,
                carbsInsulinCalculationModel = carbsInsulinCalculationModel,
                therapyManager = therapyManager,
                onCancelInsulinJobs = onCancelInsulinJobs,
                onSetTempBasal = onSetTempBasal,
                onCarbsHint = onCarbsHint,
                onDeliverBolus = onDeliverBolus,
            )
        }
    }
}