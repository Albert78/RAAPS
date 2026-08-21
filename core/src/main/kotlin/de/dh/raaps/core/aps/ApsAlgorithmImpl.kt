package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.PlannedInsulin
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculator
import de.dh.raaps.common.model.convertToBgDeltaFromUnits
import de.dh.raaps.common.model.convertToCarbsFromBgDelta
import de.dh.raaps.common.model.convertToInsulinAmountFromBgDelta
import de.dh.raaps.common.model.convertToInsulinAmountFromCarbs
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.TreatmentRepository
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round

class ApsAlgorithmImpl(
    val timeline: Timeline,
    val treatmentRepository: TreatmentRepository,
    val sampledBgReadings: SampledBgReadings,
    val predictionModel: PredictionModel,
    val carbsInsulinCalculator: CarbsInsulinCalculator,
    val therapyManager: TherapyManager
): ApsAlgorithm {
    // --- Time-based extensions for Tick to provide a Timestamp-like API ---
    private fun Tick.plusMinutes(minutes: Int): Tick = this + (minutes / timeline.tickDuration.value.toInt())
    private fun Tick.minusMinutes(minutes: Int): Tick = this - (minutes / timeline.tickDuration.value.toInt())
    private fun Tick.minus(minutes: Minutes): Tick = minusMinutes(minutes.value.toInt())

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

    override suspend fun getPredictedBg(timestamp: Timestamp): BgValue {
        val tick = timeline.tick(timestamp)
        return predictionModel.withTickState(tick) { it.predictedBg } ?: BgValue.INVALID
    }

    override fun getBolusCorrectionCalculator(): BolusCorrectionCalculator {
        return BolusCorrectionCalculatorImpl()
    }

    private inner class BolusCorrectionCalculatorImpl : BolusCorrectionCalculator {
        override suspend fun calculateBaseData(): BolusScreenBaseData {
            val now = Timestamp.now()
            val futureTime = now + Minutes(15)
            val futureBg = getPredictedBg(futureTime)

            val (referenceTimestamp, referenceBg) = if (futureBg.isValid()) {
                futureTime to futureBg
            } else {
                val nowTick = timeline.getNowTick()
                now to (predictionModel.withTickState(nowTick) { it.predictedBg } ?: BgValue.INVALID)
            }

            val bgSettings = therapyManager.getBgSettings()
            val targetBg = bgSettings.first
            val isf = therapyManager.getIsfFactor(now)
            val cr = therapyManager.getCrFactor(now)

            var suggestedCarbsKe = 0.0
            if (referenceBg.isValid() && referenceBg < targetBg) {
                val bgDiff = targetBg - referenceBg
                val carbsGrams = convertToCarbsFromBgDelta(bgDiff, isf, cr)
                // Round up to whole 5g
                val roundedCarbsGrams = ceil(carbsGrams / 5.0) * 5.0
                suggestedCarbsKe = roundedCarbsGrams / 10.0
            }

            val sea = calculateSuggestedSea()

            return BolusScreenBaseData(
                referenceTimestamp = referenceTimestamp,
                referenceBg = referenceBg,
                suggestedCarbsKe = suggestedCarbsKe,
                suggestedSea = sea
            )
        }

        override suspend fun calculateSuggestedSea(): Int {
            val bgSettings = therapyManager.getBgSettings()
            val targetBg = bgSettings.first
            val lowThreshold = bgSettings.second

            val nowTick = timeline.getNowTick()
            val currentBg = predictionModel.withTickState(nowTick) { it.predictedBg }

            if ((currentBg == null) || currentBg.isInvalid()) return 0

            if (currentBg.mgdl <= lowThreshold.mgdl) {
                return -15 // Suggest 15 min delay for bolus if low
            }

            val diff = currentBg.mgdl - targetBg.mgdl
            if (diff <= 0) return 0

            // Simple rule: 5 minutes per 20 mg/dL above target, max 45 min
            val suggested = (diff / 20) * 5
            return suggested.coerceIn(0, 45)
        }

        override suspend fun calculateBolusParts(carbsKe: Double, mealTimestamp: Timestamp, referenceTimestamp: Timestamp): BolusParts {
            val now = Timestamp.now()
            val settings = therapyManager.getCurrentTherapySettings()
            val dia = settings.insulinProfile.dia
            val peak = settings.insulinProfile.peak

            val cr = therapyManager.getCrFactor(now)
            val isf = therapyManager.getIsfFactor(now).mgdl.toInt()
            val bgSettings = therapyManager.getBgSettings()
            val targetBg = bgSettings.first

            val historyLimit = now.minusHours(25)
            val insulinHistory = treatmentRepository.getInsulinApplications(from = historyLimit)
            val mealsHistory = treatmentRepository.getMeals(from = historyLimit)

            val projectedIob = carbsInsulinCalculator.iob(insulinHistory, referenceTimestamp, dia, peak)
            val projectedCob = carbsInsulinCalculator.cob(mealsHistory, referenceTimestamp)

            val bgValue = run {
                val tick = timeline.tick(referenceTimestamp)
                predictionModel.withTickState(tick) { it.predictedBg } ?: BgValue.INVALID
            }

            val carbsGrams = carbsKe * 10.0
            val mealPart = convertToInsulinAmountFromCarbs(carbsGrams, cr)

            val currentBgMgdl = if (bgValue.isValid()) bgValue.mgdl else targetBg.mgdl
            val bgDiff = currentBgMgdl - targetBg.mgdl

            val correctionPart = convertToInsulinAmountFromBgDelta(BgDelta(bgDiff.toShort()), BgDelta(isf.toShort()))
            val cobPart = convertToInsulinAmountFromCarbs(projectedCob, cr)

            val total = (mealPart + correctionPart - projectedIob + cobPart).coerceAtLeast(InsulinAmount.ZERO)
            val roundedTotal = round(total.iu * 100.0) / 100.0
            val bolusAmount = InsulinAmount(roundedTotal)

            return BolusParts(
                mealPart = mealPart,
                correctionPart = correctionPart,
                iobPart = projectedIob,
                cobPart = cobPart,
                totalProposed = bolusAmount,
                cobGrams = projectedCob,
                calculationBg = bgValue,
                calculationTimestamp = mealTimestamp
            )
        }

        override suspend fun distributeInsulinPlan(
            manualBolus: InsulinAmount,
            correctionPart: InsulinAmount,
            mealType: MealType?,
            mealTimestamp: Timestamp,
            existingPlan: List<PlannedInsulin>
        ): List<PlannedInsulin> {
            if (manualBolus <= InsulinAmount.ZERO) {
                return emptyList()
            }

            val sea = calculateSuggestedSea()
            val suggestedOffset = if (sea < 0) abs(sea) else 0

            if (mealType == null) {
                val existing = existingPlan.getOrNull(0)
                val isUserModified = existing?.isUserModified == true
                
                // If user modified the offset, keep their offset but apply it to the base time
                val newTimestamp = if (isUserModified) {
                    mealTimestamp + Minutes(existing.offsetMinutes.toShort())
                } else {
                    mealTimestamp + Minutes(suggestedOffset.toShort())
                }
                
                return listOf(
                    PlannedInsulin(
                        amount = manualBolus,
                        timestamp = newTimestamp,
                        offsetMinutes = if (isUserModified) existing.offsetMinutes else suggestedOffset,
                        description = "Bolus",
                        isUserModified = isUserModified
                    )
                )
            }

            val totalAmount = manualBolus.iu
            val correction = correctionPart.iu
            val restToDistribute = totalAmount - correction

            val rawAmounts = DoubleArray(mealType.components.size) { i ->
                val weight = mealType.components[i].weight / 100.0
                val share = restToDistribute * weight
                if (i == 0) share + correction else share
            }

            for (i in 0 until rawAmounts.size - 1) {
                if (rawAmounts[i] < 0) {
                    rawAmounts[i + 1] += rawAmounts[i]
                    rawAmounts[i] = 0.0
                }
            }
            for (i in rawAmounts.size - 1 downTo 1) {
                if (rawAmounts[i] < 0) {
                    rawAmounts[i - 1] += rawAmounts[i]
                    rawAmounts[i] = 0.0
                }
            }

            val roundedAmounts = rawAmounts.map { round(max(0.0, it) * 100.0) / 100.0 }.toDoubleArray()

            val currentSum = roundedAmounts.sum()
            val targetSum = round(totalAmount * 100.0) / 100.0
            val diff = round((targetSum - currentSum) * 100.0) / 100.0

            if (abs(diff) >= 0.01) {
                val indexToAdjust = roundedAmounts.indices.maxByOrNull { roundedAmounts[it] } ?: 0
                roundedAmounts[indexToAdjust] = round((roundedAmounts[indexToAdjust] + diff) * 100.0) / 100.0
            }

            return mealType.components.mapIndexed { index, component ->
                val amount = InsulinAmount(roundedAmounts[index])
                val delayFromBase = if (index == 0) 0 else component.peakMinutes.value.toInt()
                val finalOffset = suggestedOffset + delayFromBase

                val existing = existingPlan.getOrNull(index)
                val isUserModified = existing?.isUserModified == true
                
                // If user modified the offset, apply their offset to the new base time
                val newTimestamp = if (isUserModified) {
                    mealTimestamp + Minutes(existing.offsetMinutes.toShort())
                } else {
                    mealTimestamp + Minutes(finalOffset.toShort())
                }

                PlannedInsulin(
                    amount = amount,
                    timestamp = newTimestamp,
                    offsetMinutes = if (isUserModified) existing.offsetMinutes else finalOffset,
                    description = if (mealType.components.size > 1) "Teil ${index + 1} (${component.weight}%)" else "Bolus",
                    isUserModified = isUserModified
                )
            }.filter { it.amount > InsulinAmount.ZERO }
        }
    }

    /**
     * Calculate the deviation between previous forecasts and the blood glucose values actually received.
     * This is done by comparing recent blood glucose slopes to the predicted BGI (Blood Glucose Impact) values.
     * Deviations typically occur due to unannounced meals or variations in insulin/carb sensitivity
     * compared to the prediction model.
     */
    private suspend fun calcAvgDeviationPerTick(pastTime: Minutes): BgDelta {
        val endTick = timeline.getNowTick()
        val startTick = endTick.minus(pastTime)

        val bgStart = sampledBgReadings.getAt(startTick)
        if (!bgStart.isValid()) return BgDelta(0)

        val bgEnd = sampledBgReadings.getAt(endTick)
        if (!bgEnd.isValid()) return BgDelta(0)

        val actualChange = bgEnd.mgdl - bgStart.mgdl

        var sumPredictedBgi = 0.0
        val numTicks = endTick.value - startTick.value

        if (numTicks <= 0) return BgDelta(0)

        predictionModel.forEach(from = startTick + 1, to = endTick) { _, state ->
            sumPredictedBgi += state.bgi.mgdl
        }

        val totalDeviation = actualChange - sumPredictedBgi
        return BgDelta.fromMgDl((totalDeviation / numTicks).toInt())
    }

    override suspend fun recalculate(): CalculationResult = try {
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

            // TODO: Add detection for noisy values. If values are too noisy,
            // If too noisy, return CalculationResult.coreIssues(NoisyValues)

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
                    return@recalculate CalculationResult.coreIssue(
                        CoreIssue.NoRecentValues(SWITCH_OFF_ALGORITHM_INVALID_VALUES_THRESHOLD_IN_MINUTES),
                        CoreReasoning.BAD_VALUES
                    )
                }
                // We cannot make any new predictions if we don't have fresh values.
                // Fallback to safe basal
                return@recalculate CalculationResult.safetyBasal()
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
        val settings = therapyManager.getCurrentTherapySettings()
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
            carbsInsulinCalculator = carbsInsulinCalculator
        )

        val bgSettings = therapyManager.getBgSettings()
        val targetBg = bgSettings.first
        val lowThreshold = bgSettings.second

        val insulinPeakTicks = timeline.inTicks(insulinPeak)
        val isfValue = therapyManager.getIsfFactor(now)
        val crValue = therapyManager.getCrFactor(now)

        // ------------------------------ Recovery Check -------------------------------------------

        // If we're currently low, check if we're already recovering.
        if (currentBgMgDl < lowThreshold.mgdl) {
            // Phase 1: Find the first point within the next 30 minutes where we're back above the threshold
            val recoveryStartTick = predictionModel.findNext(
                startAt = nowTick,
                until = nowTick.plusMinutes(30),
                predicate = { it.predictedBg.isValid() && it.predictedBg >= lowThreshold },
                block = { it.tick }
            )

            if (recoveryStartTick != null) {
                // Phase 2: Check the following 30 minutes to ensure we stay above the threshold
                val relapseFound = predictionModel.findNext(
                    startAt = recoveryStartTick,
                    until = recoveryStartTick.plusMinutes(30),
                    predicate = { it.predictedBg.isValid() && it.predictedBg < lowThreshold },
                    block = { true }
                ) ?: false

                if (!relapseFound) {
                    val recoveryTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(timeline.timestamp(recoveryStartTick).ms)
                    Log.d(TAG, "Recovery detected: We're currently low ($currentBgMgDl mg/dl), but returning above threshold of ${lowThreshold.mgdl} mg/dl at $recoveryTime and staying stable for 30m. Don't suggest carbs.")
                    return@recalculate CalculationResult.normalSafetyBasal()
                }
            }
        }

        // -------------------------------- Low handling -------------------------------------------

        // Get out of a current or impending low by suggesting carbs
        // Find the next occurrence where the value falls below the minimum; find the minimum with time
        val impendingLow = predictionModel.findNext(
            startAt = nowTick,
            until = nowTick.plusMinutes(LOW_WARNING_THRESHOLD.value.toInt()),
            predicate = { it.predictedBg.isValid() && it.predictedBg < lowThreshold + LOW_BG_SAFETY_MARGIN },
            block = { true }
        ) ?: false

        if (impendingLow) {
            // We're too low. Find out, how low we'll come to calculate the amount of suggested carbs.

            // Correct the minimum BG for twice the peak time of fast KE -> Don't look into the future too much
            val bgMin = predictionModel.findBgMin(
                startAt = nowTick,
                until = nowTick.plusMinutes(FAST_KE_DEFAULT_PEAK.value.toInt()),
                block = { it.predictedBg }
            )
            if (bgMin != null && bgMin.isValid()) {
                val bgErrorAtMin = targetBg - bgMin
                val lowCorrectionCarbsForMinInG = convertToCarbsFromBgDelta(
                    bgDelta = bgErrorAtMin,
                    isf = isfValue,
                    cr = crValue
                )
                val recentCarbsInG = meals.
                    filter { meal -> meal.timestamp > now.minusMinutes(20) }.
                    sumOf { meal -> meal.carbGrams }
                val rawCarbsInGHint = lowCorrectionCarbsForMinInG - recentCarbsInG
                val carbsInGHint = (ceil(rawCarbsInGHint / 5.0) * 5).toInt()
                if (carbsInGHint > 0) {
                    return CalculationResult.carbsSuggestion(carbsInGHint = carbsInGHint) // Stop further processing when we're currently low
                }
            }
        }

        val cobAtPeak = carbsInsulinCalculator.cob(meals, now + insulinPeak)
        val iobNow = carbsInsulinCalculator.iob(
            insulinApplications = insulinApplications,
            timestamp = now,
            dia = dia,
            peak = insulinPeak
        )
        val iobAtPeak = carbsInsulinCalculator.iob(
            insulinApplications = insulinApplications,
            timestamp = now + insulinPeak,
            dia = dia,
            peak = insulinPeak
        )

        val insightTemplate = CoreInsight(
            timestamp = now,
            bgOriginal = sampledBgReadings.getAt(nowTick),
            bgFiltered = BgValue.fromMgDl(currentBgMgDl),
            deviationPerTick = avgCurrentDeviationPerTick,
            iobAtPeak = iobAtPeak,
            cobAtPeak = cobAtPeak,
            predictedBgAtPeak = BgValue.INVALID, // Will be filled below if available
            targetBg = targetBg,
            isf = isfValue,
            cr = crValue,
            reasoning = CoreReasoning.INTERNAL_ERROR // Will be overwritten by withMetrics()
        )

        val tickStateAtPeakValues = predictionModel.withTickState(nowTick + insulinPeakTicks) {
            it.predictedBg to it.cumulatedBasalInsulin
        } ?: return CalculationResult.safetyBasal().withMetrics(insightTemplate)

        val predictedBgAtPeak = tickStateAtPeakValues.first.takeIf { it.isValid() }
            ?: return CalculationResult.safetyBasal().withMetrics(insightTemplate)
        val cumulatedBasalInsulinAtPeak = tickStateAtPeakValues.second

        val insight = insightTemplate.copy(predictedBgAtPeak = predictedBgAtPeak)
        val bgErrorAtPeak = predictedBgAtPeak - targetBg // < 0 if too low

        // Low protection for "lower than target" situations
        if (bgErrorAtPeak < BgDelta(-10)) {
            // Prediction is too low under normal basal;
            // Either BG is falling, or, raising too slow -> Lower basal rate and defer meals

            val cumulatedInsulinActivityUntilPeak = iobNow - iobAtPeak
            val currentInsulinEffectUntilPeak = -convertToBgDeltaFromUnits(cumulatedInsulinActivityUntilPeak, isfValue)
            val normalBasalEffectUntilPeak = -convertToBgDeltaFromUnits(cumulatedBasalInsulinAtPeak, isfValue)
            val lowTempBasalEffectUntilPeak = currentInsulinEffectUntilPeak - normalBasalEffectUntilPeak

            if (bgErrorAtPeak + lowTempBasalEffectUntilPeak < BgDelta(-20)) {
                // Prediction is too low -> Defer ongoing meal boluses
                return CalculationResult.zeroTemp(durationInHours = 1).withMetrics(insight)
            }
            // Else go on with decreased basal
            val safetCorrectionUnits = convertToInsulinAmountFromBgDelta(-bgErrorAtPeak, isfValue)
            val unitsPerHour = (defaultBasal - safetCorrectionUnits).coerceAtLeast(InsulinAmount.ZERO)
            val percent = if (defaultBasal > InsulinAmount.ZERO) {
                (unitsPerHour / defaultBasal * 100.0).toInt()
            } else {
                0
            }
            return CalculationResult.tempBasal(
                percent = percent,
                durationInHours = 1
            ).withMetrics(insight)
        }

        // Calculation of recovery phase
        val isRecoveringFromLow = currentBgMgDl < targetBg.mgdl - 10 &&
                bgErrorAtPeak >= BgDelta(-10)

        if (isRecoveringFromLow) {
            // If current BG is still below target but the prediction at peak has already reached
            // the target, we stop basal reduction early to avoid a rebound high caused by the
            // insulin's action delay.

            // Return to normal basal rate (clear temp basal) but do not calculate
            // any correction boluses yet to avoid overshooting during recovery.
            return CalculationResult.normalSafetyBasal().withMetrics(insight)
        }

        // *****************************************************************************************
        // At this point, we're sure not to become (too) low, it's safe to have a normal basal rate and
        // to administer meal boluses
        // *****************************************************************************************

        // -------------------------------- Meal & high handling -----------------------------------

        // Calculate scheduled meal boluses
        var dueMealBolusAmount = InsulinAmount.ZERO
        val dueDeferredBoluses: MutableList<DeferredBolus> = mutableListOf()

        val deferredBoluses = therapyManager.getDeferredBoluses()
        val sumFutureDeferredBolus = deferredBoluses.filter { it.timestamp >= now }.fold(InsulinAmount.ZERO) { acc, next -> acc + next.amount }
        for (deferredBolus in deferredBoluses) {
            if (deferredBolus.timestamp < now) {
                dueMealBolusAmount += deferredBolus.amount
                dueDeferredBoluses += deferredBolus
            }
        }

        val insulinEquivalentOfCob = convertToInsulinAmountFromCarbs(carbs = cobAtPeak, cr = crValue)

        val bgErrorCorrectionUnits = (convertToInsulinAmountFromBgDelta(bgErrorAtPeak, isfValue) * AGGRESSIVENESS_ERROR_CORRECTION)
            .coerceAtLeast(InsulinAmount.ZERO)
        val futureInsulin = iobAtPeak + dueMealBolusAmount + sumFutureDeferredBolus
        if (futureInsulin + InsulinAmount.EPSILON >= insulinEquivalentOfCob + bgErrorCorrectionUnits) {
            // Meals and BG error are covered by IOB/planned boluses.
            // Return to normal basal rate and wait for insulin/carbs to act.
            if (dueDeferredBoluses.isEmpty()) {
                CalculationResult.normalSafetyBasal().withMetrics(insight)
            } else {
                // Administer due deferred bolus.
                // It might be that this is too much for the COB but this is in the
                // responsibility of the user.
                CalculationResult.mealOrCorrectionBolus(
                    bolusAmount = dueMealBolusAmount,
                    handledDeferredBoluses = dueDeferredBoluses
                ).withMetrics(insight)
            }
        } else {
            // Insufficient insulin: Calculate the delta needed to cover the gap.
            // TODO: If BG is too high, consider administering a deferred bolus at once
            val neededInsulin = (insulinEquivalentOfCob * AGGRESSIVENESS_CARBS_CORRECTION) +
                    bgErrorCorrectionUnits
            val futureAvailableInsulin = iobAtPeak + dueMealBolusAmount + sumFutureDeferredBolus
            val bolusAmount = dueMealBolusAmount + (neededInsulin - futureAvailableInsulin).coerceAtLeast(InsulinAmount.ZERO)

            CalculationResult.mealOrCorrectionBolus(
                bolusAmount = bolusAmount,
                handledDeferredBoluses = dueDeferredBoluses
            ).withMetrics(insight)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error while recalculating", e)
        CalculationResult.internalError("Error while recalculating", e)
    }

    companion object {
        val TAG = ApsAlgorithmImpl::class.simpleName!!

        const val PREDICTION_WINDOW_HOURS = 10
        val DEVIATION_TIME_BASE = Minutes(30)
        val PRESERVE_PREDICTIONS_PAST_TIME = DEVIATION_TIME_BASE

        const val DEVIATION_DECAY_FACTOR_PER_TICK = 0.9

        val LOW_BG_SAFETY_MARGIN = BgDelta(10)

        suspend fun create(
            treatmentRepository: TreatmentRepository,
            sampledBgReadings: SampledBgReadings,
            therapyManager: TherapyManager,
            timeline: Timeline,
            carbsInsulinCalculator: CarbsInsulinCalculator,
        ): ApsAlgorithm {
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
                carbsInsulinCalculator = carbsInsulinCalculator,
                therapyManager = therapyManager
            )
        }
    }
}