package de.dh.raaps.core.aps

import android.util.Log
import de.dh.raaps.common.model.DeferredBolus
import de.dh.raaps.common.model.FAST_KE_DEFAULT_PEAK
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.METABOLIC_EVENTS_HISTORY_HOURS
import de.dh.raaps.common.model.MealType
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
import kotlin.math.ceil

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

    override suspend fun getAssumedBg(timestamp: Timestamp): BgValue {
        val tick = timeline.tick(timestamp)
        return predictionModel.withTickState(tick) { it.assumedBg } ?: BgValue.INVALID
    }

    override fun getBolusCorrectionCalculator(): BolusCorrectionCalculator {
        return BolusCorrectionCalculatorImpl()
    }

    /**
     * Smart bolus calculator that has access to the internal state of the APS algorithm.
     */
    private inner class BolusCorrectionCalculatorImpl : BolusCorrectionCalculator {
        override suspend fun calculateBolusProjections(mealTimestamp: Timestamp): BolusProjections {
            val mealTimeTick = timeline.tick(mealTimestamp)
            return predictionModel.withTickState(mealTimeTick) { state ->
                val historyLimit = mealTimestamp.minusHours(METABOLIC_EVENTS_HISTORY_HOURS)
                val insulinHistory = treatmentRepository.getInsulinApplications(from = historyLimit)
                val mealsHistory = treatmentRepository.getMeals(from = historyLimit)

                val settings = therapyManager.getCurrentTherapySettings()
                val dia = settings.insulinProfile.dia
                val peak = settings.insulinProfile.peak
                val lowThreshold = therapyManager.getBgSettings().second

                val impendingLow = predictionModel.findNextWithLock(
                    startAt = mealTimeTick + 1,
                    until = mealTimeTick.plusMinutes(BOLUS_CALCULATOR_LOW_PREDICTION_LOOKAHEAD.value.toInt()),
                    predicate = { it.assumedBg.isValid() && it.assumedBg < lowThreshold },
                    block = { ProjectedBg(bg = it.assumedBg, timestamp = timeline.timestamp(it.tick)) }
                )

                val projectedIob = carbsInsulinCalculator.iob(
                    insulinApplications = insulinHistory,
                    timestamp = mealTimestamp,
                    dia = dia,
                    peak = peak,
                    excludeBasal = true
                )
                val projectedCob = carbsInsulinCalculator.cob(
                    meals = mealsHistory,
                    timestamp = mealTimestamp,
                    includeFutureMeals = false
                )
                val sumFutureCarbs = mealsHistory.sumOf { if (it.timestamp > mealTimestamp) it.carbGrams else 0.0 }
                val deferredBoluses = treatmentRepository.getDeferredBoluses()
                val sumFutureDeferredBolus = deferredBoluses
                    .fold(InsulinAmount.ZERO) { acc, next -> acc + next.amount }

                BolusProjections(
                    timestamp = mealTimestamp,
                    bg = state.assumedBg,
                    isProjected = mealTimestamp > Timestamp.now(),
                    impendingLow = impendingLow,
                    iob = projectedIob,
                    cob = projectedCob,
                    futureCarbs = sumFutureCarbs,
                    deferredBolusAmount = sumFutureDeferredBolus
                )
            } ?: BolusProjections()
        }

        override suspend fun calculateBolusParts(
            carbsKe: Double,
            mealTimestamp: Timestamp,
            projectedBg: BgValue,
            impendingLow: ProjectedBg?,
            projectedIob: InsulinAmount,
            projectedCob: Double,
            futureCarbs: Double,
            deferredBolusAmount: InsulinAmount
        ) = BolusCalculationMath.calculateBolusParts(
            carbsKe = carbsKe,
            bg = projectedBg,
            cr = therapyManager.getCrFactor(mealTimestamp),
            isf = therapyManager.getIsfFactor(mealTimestamp),
            targetBg = therapyManager.getBgSettings(mealTimestamp).first,
            impendingLow = impendingLow,
            iob = projectedIob,
            cob = projectedCob,
            futureCarbs = futureCarbs,
            deferredBolusAmount = deferredBolusAmount
        )

        override suspend fun distributeInsulinPlan(
            manualBolus: InsulinAmount,
            correctionPart: InsulinAmount,
            mealType: MealType?,
            suggestedImi: Minutes
        ) = BolusCalculationMath.distributeInsulinPlan(
            manualBolus,
            correctionPart,
            mealType,
            suggestedImi
        )
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

    override suspend fun recalculate(): CalculationResult {
        val nowTick = timeline.getNowTick()
        val now = Timestamp.now()

        var insight = CoreInsight(
            timestamp = now,
            bgOriginal = sampledBgReadings.getAt(nowTick),
            bgFiltered = BgValue.INVALID,
            deviationPerTick = BgDelta.fromMgDl(0),
            futureActiveInsulin = InsulinAmount.ZERO,
            futureActiveCarbs = 0.0,
            predictedBgAtPeak = BgValue.INVALID,
            targetBg = BgValue.INVALID,
            isf = BgDelta.fromMgDl(0),
            cr = 0.0,
            reasoning = CoreReasoning.INTERNAL_ERROR
        )

        return try {
            Log.d(TAG, "Algorithm is calculating...")

            // Move the prediction window forward. It always covers roughly our BG readings history cache
            // to be able to calculate deviations between the static predictions and the actual readings.
            predictionModel.advanceToTick(nowTick.minus(PRESERVE_PREDICTIONS_PAST_TIME))

            // Fill the "past" part of our tick states with real BG values from out input.
            // This is necessary at cold start and for each state when the algorithm didn't recalculate for some reason.
            predictionModel.rollingHistory.forEachS(to = nowTick) { tick, state ->
                val bg = sampledBgReadings.getAt(tick)
                if (bg.isValid()) {
                    state.assumedBg = bg
                }
            }

            // ------------------------------- BG Filtering & Validation -------------------------------

            // Use Short value directly to make clear that it's a valid BG value
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
                            CoreReasoning.INVALID_VALUES
                        ).withMetrics(insight)
                    }
                    // We cannot make any new predictions if we don't have fresh values.
                    // Fallback to safe basal
                    return@recalculate CalculationResult.safetyBasal(CoreReasoning.INVALID_VALUES).withMetrics(insight)
                }
                insight = insight.copy(bgFiltered = BgValue.fromMgDl(filtered.mgdl))
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

            insight = insight.copy(deviationPerTick = avgCurrentDeviationPerTick)

            val meals = treatmentRepository.getMeals()
            val insulinApplications = treatmentRepository.getInsulinApplications()
            val settings = therapyManager.getCurrentTherapySettings()
            val dia = settings.insulinProfile.dia
            val insulinPeak = settings.insulinProfile.peak

            val bgSettings = therapyManager.getBgSettings(now)
            val targetBg = bgSettings.first
            val lowThreshold = bgSettings.second

            val isfValue = therapyManager.getIsfFactor(now)
            val crValue = therapyManager.getCrFactor(now)

            insight = insight.copy(
                targetBg = targetBg,
                isf = isfValue,
                cr = crValue
            )

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

            val insulinPeakTicks = timeline.inTicks(insulinPeak)

            // -------------------------------- Low handling -------------------------------------------

            // Get out of a current or impending low by suggesting carbs.

            // Find the next tick where the value falls below the minimum within the next LOW_WARNING_THRESHOLD minutes
            val impendingLowTick = predictionModel.findNext(
                startAt = nowTick,
                until = nowTick.plusMinutes(LOW_WARNING_THRESHOLD.value.toInt()),
                predicate = { it.assumedBg.isValid() && it.assumedBg < lowThreshold + LOW_BG_SAFETY_MARGIN },
                block = { it.tick }
            )

            if (impendingLowTick != null) {
                // We're too low or becoming too low soon.
                val recovery = predictionModel.findNext(
                    startAt = impendingLowTick,
                    until = nowTick.plusMinutes(30),
                    predicate = { it.assumedBg.isValid() && it.assumedBg > lowThreshold + LOW_BG_SAFETY_MARGIN },
                    block = { true }
                ) ?: false

                if (!recovery) {
                    // Find out, how low we'll come to calculate the amount of suggested carbs.

                    // Correct the minimum BG for twice the peak time of fast KE -> Don't look into the future too much
                    val bgMin = predictionModel.findBgMin(
                        startAt = nowTick,
                        until = nowTick.plusMinutes(FAST_KE_DEFAULT_PEAK.value.toInt() * 2),
                        block = { it.assumedBg }
                    )
                    if (bgMin != null && bgMin.isValid()) {
                        val bgErrorAtMin = targetBg - bgMin
                        val neededLowCorrectionCarbsForMinInG = convertToCarbsFromBgDelta(
                            bgDelta = bgErrorAtMin,
                            isf = isfValue,
                            cr = crValue
                        )
                        val recentCarbsInG = meals.
                            filter { meal -> meal.timestamp > now.minusMinutes(20) && meal.timestamp < now.plusMinutes(15) }.
                            sumOf { meal -> meal.carbGrams }
                        val rawCarbsInGHint = neededLowCorrectionCarbsForMinInG - recentCarbsInG
                        val carbsInGHint = (ceil(rawCarbsInGHint / 5.0) * 5).toInt()
                        if (carbsInGHint > 0) {
                            return CalculationResult.carbsSuggestion(carbsInGHint = carbsInGHint).withMetrics(insight) // Stop further processing when we're currently low
                        }
                    }
                }
            }

            // Expected future carbs and insulin activity
            val futureActiveCarbsAtPeak = carbsInsulinCalculator.cob(meals = meals, timestamp = now + insulinPeak, includeFutureMeals = true)

            // Includes basal. (Subtract cumulatedBasalInsulinUntilPeak later for net/0-line calculation).
            val iobNow_WB = carbsInsulinCalculator.iob(
                insulinApplications = insulinApplications,
                timestamp = now,
                dia = dia,
                peak = insulinPeak,
                excludeBasal = false
            )

            // Includes basal. (Subtract cumulatedBasalInsulinUntilPeak later for net/0-line calculation).
            val iobAtPeak_WB = carbsInsulinCalculator.iob(
                insulinApplications = insulinApplications,
                timestamp = now + insulinPeak,
                dia = dia,
                peak = insulinPeak,
                excludeBasal = false
            )

            val deferredBoluses = therapyManager.getDeferredBoluses()
            val sumDeferredBolusesAtPeak = deferredBoluses.
                filter { it.timestamp >= now + insulinPeak }.
                fold(InsulinAmount.ZERO) { acc, next -> acc + next.amount }

            insight = insight.copy(
                futureActiveInsulin = iobAtPeak_WB + sumDeferredBolusesAtPeak,
                futureActiveCarbs = futureActiveCarbsAtPeak
            )

            val tickStateAtPeakValues = predictionModel.withTickState(nowTick + insulinPeakTicks) {
                it.assumedBg to it.cumulatedBasalInsulin
            } ?: return CalculationResult.safetyBasal().withMetrics(insight)

            val predictedBgAtPeak = tickStateAtPeakValues.first.takeIf { it.isValid() }
                ?: return CalculationResult.safetyBasal().withMetrics(insight)
            val cumulatedBasalInsulinUntilPeak = tickStateAtPeakValues.second

            val iobAtPeak_net = iobAtPeak_WB - cumulatedBasalInsulinUntilPeak

            insight = insight.copy(predictedBgAtPeak = predictedBgAtPeak)
            val bgErrorAtPeak = predictedBgAtPeak - targetBg // < 0 if too low

            // Low protection for "lower than target" situations
            if (bgErrorAtPeak < BgDelta(0)) {
                // Prediction is too low under normal basal;
                // Either BG is falling, or, raising too slow -> Lower basal rate and defer meals

                // IOB now and IOB at peak contain already injected insulin, inclusive basal insulin.
                // Not included is future basal.
                // So cumulatedInsulinActivityUntilPeak is what we expect to be active from past
                // injections, without any new basal injections.
                val cumulatedInsulinActivityUntilPeak_WB = iobNow_WB - iobAtPeak_WB
                val insulinEffectUntilPeak_WB = -convertToBgDeltaFromUnits(cumulatedInsulinActivityUntilPeak_WB, isfValue)
                val normalBasalEffectUntilPeak = -convertToBgDeltaFromUnits(cumulatedBasalInsulinUntilPeak, isfValue)

                // All predictions are based on the assumption that we deliver normal basal. So
                // to calculate the low temp scenario, we just subtract the normal basal effect.
                val lowTempBasalEffectUntilPeak = insulinEffectUntilPeak_WB - normalBasalEffectUntilPeak

                if (bgErrorAtPeak + lowTempBasalEffectUntilPeak < BgDelta(-20)) {
                    // Prediction is too low, even without basal -> Zero temp and defer ongoing meal boluses
                    return CalculationResult.zeroTemp(durationInHours = 1).withMetrics(insight)
                }
                // Else go on with decreased basal
                val needed = -(normalBasalEffectUntilPeak - bgErrorAtPeak).coerceAtMost(BgDelta.fromMgDl(0))
                val normalEffect = -normalBasalEffectUntilPeak
                val percent = ((needed * 100.0 / normalEffect).coerceIn(0.0, 100.0)).toInt()

                return CalculationResult.tempBasal(
                    percent = percent,
                    durationInHours = 1
                ).withMetrics(insight)
            }

            // *****************************************************************************************
            // At this point, we're sure not to become (too) low, it's safe to have a normal basal rate and
            // to administer meal boluses
            // *****************************************************************************************

            // -------------------------------- Meal & high handling -----------------------------------

            // Situation: BG >= Target

            // Calculate scheduled meal boluses
            var dueMealBolusAmount = InsulinAmount.ZERO
            val dueDeferredBoluses: MutableList<DeferredBolus> = mutableListOf()
            for (deferredBolus in deferredBoluses) {
                if (deferredBolus.timestamp < now) {
                    dueMealBolusAmount += deferredBolus.amount
                    dueDeferredBoluses += deferredBolus
                }
            }
            val sumFutureDeferredBoluses = deferredBoluses.
                filter { it.timestamp >= now }.
                fold(InsulinAmount.ZERO) { acc, next -> acc + next.amount }

            val insulinEquivalentOfCarbsAtPeak = convertToInsulinAmountFromCarbs(carbs = futureActiveCarbsAtPeak, cr = crValue)

            val bgErrorCorrectionUnits = (convertToInsulinAmountFromBgDelta(bgErrorAtPeak, isfValue) * AGGRESSIVENESS_ERROR_CORRECTION)
                .coerceAtLeast(InsulinAmount.ZERO)

            // Simplified calculation. We mix remaining insulin/carbs activity of now and peak.
            val futureInsulin = iobAtPeak_net + dueMealBolusAmount + sumFutureDeferredBoluses
            if (futureInsulin + InsulinAmount.EPSILON >= insulinEquivalentOfCarbsAtPeak + bgErrorCorrectionUnits) {
                // Meals and BG error are covered by IOB/planned boluses.
                // Return to normal basal rate and wait for insulin/carbs to act.
                if (dueDeferredBoluses.isEmpty()) {
                    CalculationResult.normalSafetyBasal().withMetrics(insight)
                } else {
                    // Administer due deferred bolus.
                    // It might be that this is too much for the COB but this is in the
                    // responsibility of the user.
                    CalculationResult.mealBolus(
                        bolusAmount = dueMealBolusAmount,
                        handledDeferredBoluses = dueDeferredBoluses,
                    ).withMetrics(insight)
                }
            } else {
                // Situation is: BG > Target, i.e. a correction is necessary.

                // If we don't have insulin/carbs influence, we can just correct the BG error.
                // But IF we have insulin/carbs influence, we must not just add predicted
                // future insulin and carbs because:
                // 1) Insulin and carbs activity can be so different in time that we would risk low BG
                // 2) Future carbs should be covered by deferred boluses

                // So strategy is, just correct the BG error, taking into account the IOB.
                val correctionPart = bgErrorCorrectionUnits - iobAtPeak_net.abs()
                val mealCorrectionBolusAmount = dueMealBolusAmount + correctionPart

                // We assume that the current blood glucose deviation is primarily caused by faster
                // carbs absorption than specified in the model (just a timing issue).
                // Before we generate additional correction insulin, to prevent overdoses, we bring
                // planned future doses forward to the present (“shifting”). If the user has indeed
                // underreported their intake, the system will gradually use up all deferred boluses
                // and only then increase the total dose.
                val deferredBolusUpdates = if (correctionPart > InsulinAmount.EPSILON) {
                    deferredBoluses.filter { it.timestamp >= now }.minByOrNull { it.timestamp }?.let { next ->
                        listOf(DeferredBolusUpdate(next.id, (next.amount - correctionPart).coerceAtLeast(InsulinAmount.ZERO)))
                    }
                } else null

                CalculationResult.mealOrCorrectionBolus(
                    bolusAmount = mealCorrectionBolusAmount,
                    handledDeferredBoluses = dueDeferredBoluses,
                    correctionPart = correctionPart,
                    deferredBolusUpdates = deferredBolusUpdates
                ).withMetrics(insight)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while recalculating", e)
            CalculationResult.internalError("Error while recalculating", e).withMetrics(insight)
        }
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