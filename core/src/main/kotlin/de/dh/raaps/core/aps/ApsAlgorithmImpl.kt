package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.convertToCarbsFromBgDelta
import de.dh.raaps.common.model.convertToUnitsFromBgDelta
import de.dh.raaps.common.model.convertToUnitsFromCarbs
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.util.PersistentLogger
import de.dh.raaps.core.repository.TreatmentRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApsAlgorithmImpl(
    val timeline: Timeline,
    val treatmentRepository: TreatmentRepository,
    val sampledBgReadings: SampledBgReadings,
    val predictionModel: PredictionModel,
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    val therapyManager: TherapyManager,
    val onCancelInsulinJobs: (treatmentLock: TreatmentLock) -> Unit,
    val onDeliverBolus: suspend (treatmentLock: TreatmentLock, amount: InsulinAmount, handledDeferredBoluses: List<DeferredBolus>?) -> Unit,
    val onSetTempBasal: (treatmentLock: TreatmentLock, durationInHours: Int, unitsPerHour: Double) -> Unit,
    val onClearTempBasal: (treatmentLock: TreatmentLock) -> Unit,
    val onCarbsHint: (treatmentLock: TreatmentLock, Int) -> Unit,
    private val onAlgorithmInsight: (AlgorithmInsight) -> Unit
): ApsAlgorithm {
    // --- Time-based extensions for Tick to provide a Timestamp-like API ---
    private fun Tick.plusMinutes(minutes: Int): Tick = this + (minutes / timeline.tickDuration.value.toInt())
    private fun Tick.minusMinutes(minutes: Int): Tick = this - (minutes / timeline.tickDuration.value.toInt())
    private fun Tick.minus(minutes: Minutes): Tick = minusMinutes(minutes.value.toInt())
    private val Tick.timestamp get() = timeline.timestamp(this)

    /**
     * Called when therapy settings changed.
     */
    override suspend fun updateTherapySettings() {
        predictionModel.invalidateTherapySettingsCache()
    }

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

    data class TempBasalResult(
        val unitsPerHour: Double,
        val durationInHours: Int
    )

    data class CalculationResult(
        val carbsInGHint: Int?,
        val tempBasal: TempBasalResult?,
        val clearTempBasal: Boolean,
        val bolus: InsulinAmount?,
        val handledDeferredBoluses: List<DeferredBolus>?,
        val algorithmIssues: List<AlgorithmIssue>?,
        val reasoning: AlgorithmReasoning,
        val metrics: AlgorithmInsight? = null
    ) {
        companion object {
            fun safetyBasal(): CalculationResult = CalculationResult(
                carbsInGHint = null,
                tempBasal = null,
                clearTempBasal = true,
                bolus = null,
                handledDeferredBoluses = null,
                algorithmIssues = null,
                reasoning = AlgorithmReasoning.SAFETY_BASAL_FALLBACK
            )

            fun normalSafetyBasal(): CalculationResult = CalculationResult(
                carbsInGHint = null,
                tempBasal = null,
                clearTempBasal = true,
                bolus = null,
                handledDeferredBoluses = null,
                algorithmIssues = null,
                reasoning = AlgorithmReasoning.NORMAL_CONDITION_SAFETY_BASAL
            )

            fun tempBasal(unitsPerHour: Double, durationInHours: Int) = CalculationResult(
                carbsInGHint = null,
                tempBasal = TempBasalResult(
                    unitsPerHour = unitsPerHour,
                    durationInHours = durationInHours
                ),
                clearTempBasal = false,
                bolus = null,
                handledDeferredBoluses = null,
                algorithmIssues = null,
                reasoning = AlgorithmReasoning.LOW_PREDICTED_LOW_BASAL
            )

            fun zeroTemp(durationInHours: Int): CalculationResult = CalculationResult(
                carbsInGHint = null,
                tempBasal = TempBasalResult(unitsPerHour = 0.0, durationInHours = durationInHours),
                clearTempBasal = false,
                bolus = null,
                handledDeferredBoluses = null,
                algorithmIssues = null,
                reasoning = AlgorithmReasoning.LOW_PREDICTED_ZERO_TEMP
            )

            fun carbsSuggestion(carbsInGHint: Int?) = CalculationResult(
                carbsInGHint = carbsInGHint,
                tempBasal = TempBasalResult(unitsPerHour = 0.0, durationInHours = 1),
                clearTempBasal = false,
                bolus = null,
                handledDeferredBoluses = null,
                algorithmIssues = null,
                reasoning = AlgorithmReasoning.LOW_PREDICTED_CARBS_SUGGESTION
            )

            fun mealOrCorrectionBolus(
                bolusAmount: InsulinAmount,
                handledDeferredBoluses: MutableList<DeferredBolus>
            ) = CalculationResult(
                carbsInGHint = null,
                tempBasal = null,
                clearTempBasal = true,
                bolus = bolusAmount,
                handledDeferredBoluses = handledDeferredBoluses,
                algorithmIssues = null,
                reasoning = AlgorithmReasoning.NORMAL_CONDITION_MEAL_BOLUS
            )

            fun algorithmIssues(vararg issues: AlgorithmIssue) = CalculationResult(
                carbsInGHint = null,
                tempBasal = null,
                clearTempBasal = true,
                bolus = null,
                handledDeferredBoluses = null,
                algorithmIssues = issues.toList(),
                reasoning = if (issues.any { it is AlgorithmIssue.NoRecentValues })
                    AlgorithmReasoning.NO_RECENT_VALUES
                else
                    AlgorithmReasoning.INTERNAL_ERROR
            )
        }
    }

    override suspend fun recalculate(treatmentLock: TreatmentLock): List<AlgorithmIssue> {
        Log.d(TAG, "recalculate: onCancelInsulinJobs")
        onCancelInsulinJobs(treatmentLock)

        val result = doRecalculate()
        if (result.carbsInGHint != null) {
            Log.d(TAG, "recalculate: Carbs Hint ${result.carbsInGHint} g")
            onCarbsHint(treatmentLock, result.carbsInGHint)
        }
        if (result.tempBasal != null) {
            Log.d(TAG, "recalculate: Set Temp Basal ${result.tempBasal.durationInHours} h ${result.tempBasal.unitsPerHour} IU/h")
            onSetTempBasal(treatmentLock, result.tempBasal.durationInHours, result.tempBasal.unitsPerHour)
        }
        if (result.clearTempBasal) {
            Log.d(TAG, "recalculate: Clear Temp Basal")
            onClearTempBasal(treatmentLock)
        }
        if (result.bolus != null && result.bolus.iu >= de.dh.raaps.common.model.INSULIN_EPSILON) {
            Log.d(TAG, "recalculate: Deliver Bolus ${result.bolus.iu} IU")
val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(System.currentTimeMillis()))
PersistentLogger.log("ApsAlgorithmImpl", "------------ recalculate: Calling onDeliverBolus to create BOLUS at $time, amount=${result.bolus.iu}")
            onDeliverBolus(treatmentLock, result.bolus, result.handledDeferredBoluses)
        }

        result.metrics?.let { metrics ->
            onAlgorithmInsight(metrics.copy(
                reasoning = result.reasoning,
                actionBolus = result.bolus?.iu,
                actionTempBasalUnitsPerHour = result.tempBasal?.unitsPerHour,
                actionTempBasalDurationInHours = result.tempBasal?.durationInHours
            ))
        }

        return result.algorithmIssues ?: emptyList()
    }

    suspend fun doRecalculate(): CalculationResult = try {
        Log.d(TAG, "Algorithm is calculating...")
        val nowTick = timeline.getNowTick()
        val now = Timestamp.now()

        // Move the prediction window forward. It always covers roughly our BG readings history cache
        // to be able to calculate deviations between the static predictions and the actual readings.
        predictionModel.advanceToTick(nowTick.minus(PRESERVE_PREDICTIONS_PAST_TIME))

        // ------------------------------- BG Filtering & Validation -------------------------------

        val currentBgMgDl = run {
            // Filter BG values to avoid big jumps caused by measurement errors.
            // If we have enough input values, we can use the better SavitzkyGolay filter, else fallback to PTWMA
            var filtered = sampledBgReadings.calculateSavitzkyGolayEndBorder3()
            if (filtered.isInvalid()) {
                filtered = sampledBgReadings.calculatePTWMA(0.7)
            }

            // ----------------------------- Switch off algorithm handling -----------------------------
            if (filtered.isInvalid()) {
                var receivedValidValue = false
                for (tick in nowTick.minusMinutes(SWITCH_OFF_ALGORITHM_INVALID_VALUES_THRESHOLD_IN_MINUTES)..nowTick) {
                    if (sampledBgReadings.getAt(tick).isValid()) {
                        receivedValidValue = true
                        break
                    }
                }
                if (!receivedValidValue) {
                    return@doRecalculate CalculationResult.algorithmIssues(
                        AlgorithmIssue.NoRecentValues(SWITCH_OFF_ALGORITHM_INVALID_VALUES_THRESHOLD_IN_MINUTES)
                    )
                }
                // We cannot make any new predictions if we don't have fresh values.
                // Fallback to safe basal
                return@doRecalculate CalculationResult.safetyBasal()
            }
            filtered.mgdl
        }

        // TODO: We should check if there is much more insulin then carbs, e.g. BG prediction dropping fast.
        //  If this is the situation, tell the user to manually check the situation.

        // TODO: We should check extremely high BG and switch off the algorithm.

        // ------------------------------ Calculate predictions ------------------------------------

        // Most of our calculations below are based on a static prediction model for insulin and carbs.
        // To react to dynamic changes (e.g. unannounced snacks), we calculate an average deviation of our
        // model predictions to the real blood glucose.
        // The deviation is the actual slope of bg values minus the bgi, which is the predicted slope.
        val avgCurrentDeviationPerTick = calcAvgDeviationPerTick(DEVIATION_TIME_BASE)
        // Assumption: That deviation will be continued in the future but will fade away

        val defaultBasal = therapyManager.getBasalPerHour(now)
        val meals = treatmentRepository.getMeals()
        val insulinApplications = treatmentRepository.getInsulinApplications()
        val settings = therapyManager.getActiveTherapySettings()
        val dia = settings.insulinProfile.dia
        val insulinPeak = settings.insulinProfile.peak

        // Cache block 1: Influence of meals and insulin - the data is reused in succeeding calculation
        // calls until another metabolic event occurs, which invalidates the cache block

        // Cache block 2: Profile data - the data is reused in succeeding calculation calls
        // until the profile changes, which invalidates the cache block

        // Prediction block:
        // Update predicted BGI, update predicted BG
        predictionModel.calculate(
            currentBGMgDl = currentBgMgDl,
            avgCurrentDeviationPerTick = avgCurrentDeviationPerTick,
            meals = meals,
            insulinApplications = insulinApplications,
            dia = dia,
            insulinPeak = insulinPeak,
            therapyManager = therapyManager,
            carbsInsulinCalculationModel = carbsInsulinCalculationModel
        )

        val bgSettings = therapyManager.getBgSettings()
        val targetBg = bgSettings.first
        val lowThreshold = bgSettings.second

        val pumpInsulinType = therapyManager.getPumpInsulinType()
        val insulinPeakTicks = timeline.inTicks(pumpInsulinType.peak)
        val isfValue = therapyManager.getIsfFactor(now)
        val crValue = therapyManager.getCrFactor(now)

        // First handle low and high, then fall back to normal, smooth correction

        // -------------------------------- Low handling -------------------------------------------

        // Get out of a current or impending low by suggesting carbs
        // Find the next occurrence where the value falls below the minimum; find the minimum with time
        predictionModel.findNext(startAt = nowTick, until = nowTick.plusMinutes(LOW_WARNING_THRESHOLD.value.toInt())) {
            it.predictedBg.isValid() && it.predictedBg < lowThreshold + LOW_BG_SAFETY_MARGIN
        }?.let { _ ->
            var carbsInGHint: Int? = null

            // We're too low. Find out, now low we'll come to calculate the amount of suggested carbs.

            // Correct the minimum BG for twice the peak time of fast KE -> Don't look into the future too much
            val bgMin = predictionModel.findBgMin(startAt = nowTick, nowTick.plusMinutes(FAST_KE_DEFAULT_PEAK.value.toInt()))
            if (bgMin != null && bgMin.predictedBg.isValid()) {
                val bgErrorAtMin = targetBg - bgMin.predictedBg
                val lowCorrectionCarbsForMinInG = convertToCarbsFromBgDelta(
                    bgDelta = bgErrorAtMin,
                    isf = isfValue,
                    cr = crValue
                )
                carbsInGHint = lowCorrectionCarbsForMinInG.toInt()
            }
            return@doRecalculate CalculationResult.carbsSuggestion(carbsInGHint = carbsInGHint) // Stop further processing when we're currently low
        }

        val cobAtPeak = carbsInsulinCalculationModel.cob(meals, now.plus(insulinPeak))
        val iobAtPeak = carbsInsulinCalculationModel.iob(
            insulinApplications = insulinApplications,
            timestamp = now.plus(insulinPeak),
            dia = dia,
            peak = insulinPeak
        )

        val insightTemplate = AlgorithmInsight(
            timestamp = now,
            bgOriginal = sampledBgReadings.getAt(nowTick).mgdl,
            bgFiltered = currentBgMgDl,
            deviationPerTick = avgCurrentDeviationPerTick.mgdl.toDouble(),
            iobAtPeak = iobAtPeak,
            cobAtPeak = cobAtPeak,
            predictedBgAtPeak = 0, // Will be filled below if available
            targetBg = targetBg.mgdl,
            isf = isfValue.mgdl.toDouble(),
            cr = crValue,
            reasoning = AlgorithmReasoning.INTERNAL_ERROR // Dummy, will be overwritten
        )

        val recentCarbsInG = meals.
            filter { meal -> meal.timestamp > now.minusMinutes(20) }.
            sumOf { meal -> meal.carbGrams }
        val predictedBgAtPeak = predictionModel.tryGetTickState(nowTick + insulinPeakTicks)?.
            predictedBg?.
            takeIf { it.isValid() } ?:
            return CalculationResult.safetyBasal().copy(metrics = insightTemplate) // This should never happen if we have BG values. If not, fall back to safety basal.

        val insight = insightTemplate.copy(predictedBgAtPeak = predictedBgAtPeak.mgdl)
        val bgErrorAtPeak = predictedBgAtPeak - targetBg // < 0 if too low

        // Low protection for "lower than target" situations
        if (bgErrorAtPeak < BgDelta(-10)) {
            // Prediction is too low; Either BG is falling, or, raising too slow -> Lower basal rate and defer meals

            if (bgErrorAtPeak < BgDelta(-20)) {
                // Prediction is too low -> Defer ongoing meal boluses
                val safetyCorrectionCarbsInG = convertToCarbsFromBgDelta(-bgErrorAtPeak, isfValue, crValue)
                if (recentCarbsInG < safetyCorrectionCarbsInG) {
                    // Bg is too low, further falling and not enough safety carbs -> Suggest carbs
                    val lowCorrectionCarbsForPeakInG = safetyCorrectionCarbsInG - recentCarbsInG
                    if (lowCorrectionCarbsForPeakInG > 5) {
                        return CalculationResult.carbsSuggestion(carbsInGHint = lowCorrectionCarbsForPeakInG.toInt()).copy(metrics = insight)
                    }
                }
                // Enough or almost enough safety carbs, wait for carbs to have effect
                return CalculationResult.zeroTemp(durationInHours = 1).copy(metrics = insight)
            }
            // Else go on with decreased basal
            val safetCorrectionUnits = convertToUnitsFromBgDelta(-bgErrorAtPeak, isfValue)
            return CalculationResult.tempBasal(
                unitsPerHour = (defaultBasal - safetCorrectionUnits).coerceAtLeast(0.0),
                durationInHours = 1
            ).copy(metrics = insight)
        }

        // *****************************************************************************************
        // At this point, we're sure not to become (too) low, it's safe to have a normal basal rate and
        // to administer meal boluses
        // *****************************************************************************************

        // -------------------------------- Meal & high handling -----------------------------------

        // Calculate scheduled meal boluses
        var dueMealBolusAmount = InsulinAmount(0.0)
        val dueDeferredBoluses: MutableList<DeferredBolus> = mutableListOf()

        val deferredBoluses = therapyManager.getDeferredBoluses()
        var sumFutureDeferredBolus = InsulinAmount(0.0)
        for (deferredBolus in deferredBoluses) {
            if (deferredBolus.timestamp < now) {
                dueMealBolusAmount += deferredBolus.amount
                dueDeferredBoluses += deferredBolus
            } else {
                sumFutureDeferredBolus += deferredBolus.amount
            }
        }

        val insulinEquivalentOfCob = convertToUnitsFromCarbs(carbs = cobAtPeak, cr = crValue)

        val bgErrorCorrectionUnits = convertToUnitsFromBgDelta(bgErrorAtPeak, isfValue).coerceAtLeast(0.0)
        val futureInsulinU = iobAtPeak + dueMealBolusAmount.iu + sumFutureDeferredBolus.iu
        if (futureInsulinU > insulinEquivalentOfCob + bgErrorCorrectionUnits * AGGRESSIVENESS_ERROR_CORRECTION) {
            // The meal is already corrected or scheduled to be corrected.
            // This which means we expect the blood sugar to rise;
            // skip correction in that case and wait for carbs & deferred boluses to take effect
            if (dueDeferredBoluses.isEmpty())
                CalculationResult.normalSafetyBasal().copy(metrics = insight)
            else
                CalculationResult.mealOrCorrectionBolus(bolusAmount = dueMealBolusAmount, handledDeferredBoluses = dueDeferredBoluses).copy(metrics = insight)
        } else {
            // Insufficient correction: Try restrained correction
            val neededInsulin = insulinEquivalentOfCob * AGGRESSIVENESS_CARBS_CORRECTION + bgErrorCorrectionUnits * AGGRESSIVENESS_ERROR_CORRECTION
            val futureAvailableInsulin = iobAtPeak + dueMealBolusAmount.iu + sumFutureDeferredBolus.iu
            CalculationResult.mealOrCorrectionBolus(
                bolusAmount = InsulinAmount(
                    // Scheduled insulin
                    dueMealBolusAmount.iu +
                    // "Uncorrected rest"
                    (neededInsulin - futureAvailableInsulin).coerceAtLeast(0.0)
                ),
                handledDeferredBoluses = dueDeferredBoluses
            ).copy(metrics = insight)
        }
    } catch (e: Exception) {
        CalculationResult.algorithmIssues(AlgorithmIssue.InternalError(e.message))
    }

    companion object {
        val TAG = ApsAlgorithmImpl::class.simpleName!!

        const val PREDICTION_WINDOW_HOURS = 10
        val DEVIATION_TIME_BASE = Minutes(30)
        val PRESERVE_PREDICTIONS_PAST_TIME = DEVIATION_TIME_BASE

        const val DEVIATION_DECAY_FACTOR_PER_TICK = 0.9

        val LOW_BG_SAFETY_MARGIN = BgDelta(10)

        fun create(
            treatmentRepository: TreatmentRepository,
            sampledBgReadings: SampledBgReadings,
            therapyManager: TherapyManager,
            onCancelInsulinJobs: (treatmentLock: TreatmentLock) -> Unit,
            onDeliverBolus: suspend (treatmentLock: TreatmentLock, amount: InsulinAmount, handledDeferredBoluses: List<DeferredBolus>?) -> Unit,
            onSetTempBasal: (treatmentLock: TreatmentLock, durationInHours: Int, unitsPerHour: Double) -> Unit,
            onClearTempBasal: (treatmentLock: TreatmentLock) -> Unit,
            onCarbsHint: (treatmentLock: TreatmentLock, Int) -> Unit,
            onAlgorithmInsight: (AlgorithmInsight) -> Unit,
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
                onClearTempBasal = onClearTempBasal,
                onCarbsHint = onCarbsHint,
                onDeliverBolus = onDeliverBolus,
                onAlgorithmInsight = onAlgorithmInsight
            )
        }
    }
}
