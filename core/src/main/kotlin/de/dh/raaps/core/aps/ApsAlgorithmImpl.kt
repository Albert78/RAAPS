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
import de.dh.raaps.core.repository.TreatmentRepository
import kotlin.math.absoluteValue

class ApsAlgorithmImpl(
    val timeline: Timeline,
    val treatmentRepository: TreatmentRepository,
    val sampledBgReadings: SampledBgReadings,
    val predictionModel: PredictionModel,
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    val therapyManager: TherapyManager,
    val onCancelInsulinJobs: (treatmentLock: TreatmentLock) -> Unit,
    val onDeliverBolus: suspend (treatmentLock: TreatmentLock, amount: InsulinAmount, handledDeferredBolus: DeferredBolus?) -> Unit,
    val onSetTempBasal: (treatmentLock: TreatmentLock, durationInHours: Int, unitsPerHour: Double) -> Unit,
    val onClearTempBasal: (treatmentLock: TreatmentLock) -> Unit,
    val onCarbsHint: (treatmentLock: TreatmentLock, Int) -> Unit,
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
        val handledDeferredBolus: DeferredBolus?
    )

    override suspend fun recalculate(treatmentLock: TreatmentLock) {
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
            onDeliverBolus(treatmentLock, result.bolus, result.handledDeferredBolus)
        }
    }

    suspend fun doRecalculate(): CalculationResult {
        val nowTick = timeline.getNowTick()
        val now = Timestamp.now()

        // Move the prediction window forward. It always covers roughly our BG readings history cache
        // to be able to calculate deviations between the static predictions and the actual readings.
        predictionModel.advanceToTick(nowTick.minus(PRESERVE_PREDICTIONS_PAST_TIME))

        // Default basal rate to be adapted by the following code
        val defaultBasal = therapyManager.getBasalPerHour(now)

        // Filter BG values to avoid big jumps caused by measurement errors.
        // If we have enough input values, we can use the better SavitzkyGolay filter, else fallback to PTWMA
        var currentBgFiltered = sampledBgReadings.calculateSavitzkyGolayEndBorder3()
        if (currentBgFiltered.isInvalid()) {
            currentBgFiltered = sampledBgReadings.calculatePTWMA(0.7)
        }

        if (currentBgFiltered.isInvalid()) {
            // We cannot make any new predictions if we don't have fresh values.
            // So we'll stick with the old ones.
            return CalculationResult(
                carbsInGHint = null,
                tempBasal = null,
                clearTempBasal = false,
                bolus = null,
                handledDeferredBolus = null
            )
        }

        // Most of our calculations below are based on a static prediction model for insulin and carbs.
        // To react to dynamic changes (e.g. unannounced snacks), we calculate an average deviation of our
        // model predictions to the real blood glucose.
        // The deviation is the actual slope of bg values minus the bgi, which is the predicted slope.
        val avgCurrentDeviationPerTick = calcAvgDeviationPerTick(DEVIATION_TIME_BASE)
        // Assumption: That deviation will be continued in the future but will fade away

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
            currentBG = currentBgFiltered,
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
        val isf = therapyManager.getIsfFactor(now)
        val cr = therapyManager.getCrFactor(now)

        // -------------------------------- Low handling -------------------------------------------

        // Get out of a current or impending low by suggesting carbs
        // Find the next occurrence where the value falls below the minimum; find the minimum with time
        predictionModel.findNext(startAt = nowTick, until = nowTick.plusMinutes(LOW_WARNING_THRESHOLD.value.toInt())) {
            it.predictedBg < lowThreshold + LOW_BG_SAFETY_MARGIN
        }?.let { _ ->
            // TODO: This handling is for "normal" lows. We should also check if there is much more insulin
            // then carbs, e.g. BG prediction dropping fast. If this is the situation, tell the user to manually check the situation.
            val tempBasalResult = TempBasalResult(unitsPerHour = 0.0, durationInHours = 1)
            var carbsInGHint: Int? = null

            // We're too low. Find out, now low we'll come to calculate the amount of suggested carbs.

            // Correct the minimum BG for twice the peak time of fast KE -> Don't look into the future too much
            val bgMin = predictionModel.findBgMin(startAt = nowTick, nowTick.plusMinutes(FAST_KE_DEFAULT_PEAK.value.toInt()))
            if (bgMin != null && bgMin.predictedBg.isValid()) {
                val bgErrorAtMin = targetBg - bgMin.predictedBg
                val lowCorrectionCarbsInG = convertToCarbsFromBgDelta(
                    bgDelta = bgErrorAtMin,
                    isf = isf,
                    cr = cr
                )
                carbsInGHint = lowCorrectionCarbsInG.toInt()
            }
            return CalculationResult(
                carbsInGHint = carbsInGHint,
                tempBasal = tempBasalResult,
                clearTempBasal = false,
                bolus = null,
                handledDeferredBolus = null
            ) // Stop further processing when we're currently low
        }

        val bgMinus15 = sampledBgReadings.getAt(nowTick.minusMinutes(15))
        val bg15Trend = if (bgMinus15.isValid() && currentBgFiltered.isValid())
            currentBgFiltered - bgMinus15
        else
            BgDelta(0)
        val recentCarbs = meals.
            filter { meal -> meal.timestamp > now.minusMinutes(30) }.
            sumOf { meal -> meal.carbGrams }

        val lookAheadStateAtPeak = predictionModel.tryGetTickState(nowTick + insulinPeakTicks)
        val predictedBgAtPeak = lookAheadStateAtPeak?.predictedBg

        var tempBasal: TempBasalResult? = null

        // Prevent further falling BG
        if (currentBgFiltered < targetBg && bg15Trend < BgDelta(0)) {
            // If Bg is under normal and further falling
            val bgErrorNow = targetBg - currentBgFiltered
            val safetyCorrectionCarbs = convertToCarbsFromBgDelta(bgErrorNow, isf, cr)

            if (recentCarbs < safetyCorrectionCarbs) {
                if (currentBgFiltered < targetBg - BgDelta(20) && bg15Trend <= BgDelta(0)) {
                    // Bg is too low and further falling -> Defer ongoing meal boluses
                    return CalculationResult(
                        carbsInGHint = null,
                        tempBasal = TempBasalResult(unitsPerHour = 0.0, durationInHours = 1),
                        clearTempBasal = false,
                        bolus = null,
                        handledDeferredBolus = null
                    )
                }
                // Else go on with decreased basal
                val safetCorrectionUnits = convertToUnitsFromBgDelta(bgErrorNow, isf)

                // Decrease basal
                tempBasal = TempBasalResult(
                    unitsPerHour = (defaultBasal - safetCorrectionUnits).coerceAtLeast(0.0),
                    durationInHours = 1
                )
            }
            // Else: Wait for correction carbs to have effect
        }

        var mealOrCorrectionBolus = InsulinAmount(0.0)

        // -------------------------------- Meal boluses -------------------------------------------

        // Administer scheduled meal boluses
        val deferredBoluses = therapyManager.getDeferredBoluses()
        var handleDeferredBolus: DeferredBolus? = null
        var sumDeferredBolus = 0.0
        for (deferredBolus in deferredBoluses) {
            sumDeferredBolus += deferredBolus.amount.iu
            if (deferredBolus.timestamp < now) {
                handleDeferredBolus = deferredBolus
                mealOrCorrectionBolus = deferredBolus.amount
            }
        }

        val neutralCalculationResult = CalculationResult(
            carbsInGHint = null,
            tempBasal = tempBasal,
            clearTempBasal = tempBasal == null,
            bolus = mealOrCorrectionBolus,
            handledDeferredBolus = handleDeferredBolus
        )

        // ------------------------ Good BG or neutral handling check ------------------------------

        val currentCob = carbsInsulinCalculationModel.cob(meals, now)
        val insulinEquivalentOfCarbs = convertToUnitsFromCarbs(carbs = currentCob, cr = cr)
        val currentIob = carbsInsulinCalculationModel.iob(
            insulinApplications = insulinApplications,
            timestamp = now,
            dia = dia,
            peak = insulinPeak
        )

        // TODO: Validate this arbitrary check; Just want to make sure that we don't start correcting
        // a high BG if we still have much IOB / deferred bolus
        if (currentCob > SUSPEND_HIGH_CORRECTIONS_ON_HIGH_COB_THRESHOLD &&
            (currentIob + sumDeferredBolus) > insulinEquivalentOfCarbs / 2.0) {
            // We're under influence of a meal, which means we expect the blood sugar to rise; skip correction in that case
            return neutralCalculationResult
        }

        mealOrCorrectionBolus = InsulinAmount(0.0) // Not relevant anymore from here on

        if (bg15Trend < BgDelta(-10)) {
            // BG falling fast but low handling above didn't trigger; don't correct and wait for BG trend to become normal
            return neutralCalculationResult
        }

        // TODO: Validate this neutral handling, does it do a good job in combination with the
        // high handling below to hold the blood sugar at a straight line when it is good?
        // Or use another point instead of insulin peak time?
        if (predictedBgAtPeak.mgdl <= targetBg.mgdl - 10) {
            val bgError = targetBg - predictedBgAtPeak
            val correction = convertToUnitsFromBgDelta(bgDelta = bgError, isf = isf)
            return CalculationResult(
                carbsInGHint = null,
                tempBasal = TempBasalResult(unitsPerHour = (defaultBasal - correction).coerceAtLeast(0.0), durationInHours = 1),
                clearTempBasal = false,
                bolus = null,
                handledDeferredBolus = handleDeferredBolus
            )
        }

        if (handleDeferredBolus == null) {
            if ((currentBgFiltered.mgdl - targetBg.mgdl).absoluteValue < 5 && bg15Trend < BgDelta(5)) {
                // Absolute best case without (big) influences. Just use standard basal.
                return CalculationResult(
                    carbsInGHint = null,
                    tempBasal = null,
                    clearTempBasal = true,
                    bolus = null,
                    handledDeferredBolus = handleDeferredBolus
                )
            }
        }

        // -------------------------------- High handling ------------------------------------------

        // Correct high blood sugar by administering insulin

        // TODO: Make configurable
        val aggressiveness = 1.0

        // This BG error must be corrected with insulin
        val bgErrorAtPeak = (predictedBgAtPeak - targetBg) * aggressiveness

        val correction = convertToUnitsFromBgDelta(bgDelta = bgErrorAtPeak, isf = isf)
        val iobAtPeak = carbsInsulinCalculationModel.iob(
            insulinApplications = insulinApplications,
            timestamp = now.plus(insulinPeak),
            dia = dia,
            peak = insulinPeak
        )

        val insulin = (correction + insulinEquivalentOfCarbs - iobAtPeak).coerceAtLeast(0.0)
        return CalculationResult(
            carbsInGHint = null,
            tempBasal = TempBasalResult(unitsPerHour = 0.0, durationInHours = 1),
            clearTempBasal = false,
            bolus = InsulinAmount(insulin),
            handledDeferredBolus = handleDeferredBolus
        )
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
            onDeliverBolus: suspend (treatmentLock: TreatmentLock, amount: InsulinAmount, handledDeferredBolus: DeferredBolus?) -> Unit,
            onSetTempBasal: (treatmentLock: TreatmentLock, durationInHours: Int, unitsPerHour: Double) -> Unit,
            onClearTempBasal: (treatmentLock: TreatmentLock) -> Unit,
            onCarbsHint: (treatmentLock: TreatmentLock, Int) -> Unit,
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
            )
        }
    }
}