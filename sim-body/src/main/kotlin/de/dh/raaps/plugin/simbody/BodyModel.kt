package de.dh.raaps.plugin.simbody

import androidx.compose.runtime.mutableStateListOf
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.calculation.CarbCurveComponent
import de.dh.raaps.common.model.calculation.InsulinCurve
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.getAmountForMinute
import de.dh.raaps.plugin.simbody.model.BodyProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Impacts(
    val carbImpact: Double,
    val insulinImpact: Double,
    val endogenousImpact: Double,
    val exerciseImpact: Double,
    val stressImpact: Double,
    val currentTimestamp: Timestamp
)

/**
 * Models a diabetic's body for simulation purposes.
 * It tracks blood glucose levels influenced by meals, insulin, exercise, and health states.
 */
class BodyModel(initialProfile: BodyProfile) {
    // Simulation Defaults (independent of database)
    private val defaultInsulinType = InsulinType(
        id = "sim-aspart-id",
        name = "Sim Aspart",
        dia = Minutes.ofHours(5),
        peak = Minutes(75)
    )

    private val defaultMealType = MealType(
        id = "sim-standard-meal-id",
        name = "Sim Standard Meal",
        components = listOf(
            CarbCurveComponentData(weight = 70, peakMinutes = Minutes(75)),
            CarbCurveComponentData(weight = 30, peakMinutes = Minutes(150))
        ),
        cat = Minutes.ofHours(4)
    )

    // Inputs (historical data) - using Compose state for UI updates
    val meals = mutableStateListOf<MealEntry>()
    val insulinApplications = mutableStateListOf<InsulinApplication>()
    val impactHistory = mutableStateListOf<Impacts>()

    // Health factors (external influences, controlled via UI)
    private val _exerciseIntensity = MutableStateFlow(0.0)
    var exerciseIntensity: Double
        get() = _exerciseIntensity.value
        set(value) { _exerciseIntensity.value = value }
    val exerciseIntensityFlow: StateFlow<Double> = _exerciseIntensity.asStateFlow()

    private val _illnessFactor = MutableStateFlow(1.0)
    var illnessFactor: Double
        get() = _illnessFactor.value
        set(value) { _illnessFactor.value = value }
    val illnessFactorFlow: StateFlow<Double> = _illnessFactor.asStateFlow()

    private val _stressLevel = MutableStateFlow(0.0)
    var stressLevel: Double
        get() = _stressLevel.value
        set(value) { _stressLevel.value = value }
    val stressLevelFlow: StateFlow<Double> = _stressLevel.asStateFlow()

    // Simulated Person Profile (metabolic parameters)
    private val _activeProfile = MutableStateFlow(initialProfile)
    var activeProfile: BodyProfile
        get() = _activeProfile.value
        private set(value) { _activeProfile.value = value }
    val activeProfileFlow: StateFlow<BodyProfile> = _activeProfile.asStateFlow()

    val isf: Double
        get() = activeProfile.isfBlocks.getAmountForMinute(Timestamp.now().minutesSinceMidnight())

    val ic: Double
        get() = activeProfile.icBlocks.getAmountForMinute(Timestamp.now().minutesSinceMidnight())

    val liverGlucoseOutputGph: Double
        get() = activeProfile.liverGlucoseOutputBlocks.getAmountForMinute(Timestamp.now().minutesSinceMidnight())

    fun setProfile(profile: BodyProfile) {
        activeProfile = profile
    }

    // Current state
    private val _bloodGlucose = MutableStateFlow(BgValue.fromMgDl(120))
    var bloodGlucose: BgValue
        get() = _bloodGlucose.value
        set(value) { _bloodGlucose.value = value }
    val bloodGlucoseFlow: StateFlow<BgValue> = _bloodGlucose.asStateFlow()

    var lastTickTimestamp: Timestamp = Timestamp.now()

    /**
     * Advances the simulation state to the [currentTimestamp].
     * Calculates the delta in blood glucose based on all active influences.
     */
    fun advanceToTick(currentTimestamp: Timestamp = Timestamp.now()) {
        val durationMs = currentTimestamp.ms - lastTickTimestamp.ms
        if (durationMs <= 0) return

        val durationHours = durationMs / (1000.0 * 60 * 60)

        // Calculate deltas
        val insulinImpact = calculateInsulinImpact(lastTickTimestamp, currentTimestamp)
        val carbImpact = calculateCarbImpact(lastTickTimestamp, currentTimestamp)

        // Liver production offsets normal basal insulin.
        // Liver output is in grams of carbs per hour.
        val endogenousImpact = (liverGlucoseOutputGph / ic) * isf * durationHours

        // Exercise and Stress impact on BG level directly
        val exerciseImpact = exerciseIntensity * 60.0 * durationHours
        val stressImpact = stressLevel * 30.0 * durationHours

        // Total delta calculation
        val bgDelta = carbImpact - insulinImpact + endogenousImpact - exerciseImpact + stressImpact

        val newImpact = Impacts(carbImpact, insulinImpact, endogenousImpact, exerciseImpact, stressImpact, currentTimestamp)
        impactHistory.add(0, newImpact) // Add to top for history view

        // Update BG state
        val newMgDl = bloodGlucose.mgdl + bgDelta
        bloodGlucose = BgValue.fromMgDl(newMgDl.coerceIn(20.0, 500.0).toInt())

        lastTickTimestamp = currentTimestamp
        cleanup(currentTimestamp)
    }

    /**
     * Removes historical data older than 10 hours to keep the simulation performant.
     */
    private fun cleanup(currentTimestamp: Timestamp) {
        val horizonMs = 10 * 60 * 60 * 1000L
        val threshold = currentTimestamp.ms - horizonMs

        meals.removeIf { it.timestamp.ms < threshold }
        insulinApplications.removeIf { it.timestamp.ms < threshold }
        impactHistory.removeIf { it.currentTimestamp.ms < threshold }
    }

    /**
     * Simulates eating a meal.
     */
    fun eat(carbs: Double, type: MealType? = null) {
        meals.add(MealEntry(
            timestamp = Timestamp.now(),
            carbGrams = carbs,
            mealType = type ?: defaultMealType
        ))
    }

    /**
     * Simulates an insulin bolus.
     */
    fun bolus(amount: Double, type: InsulinType? = null) {
        insulinApplications.add(InsulinApplication(
            timestamp = Timestamp.now(),
            amount = amount,
            insulinType = type ?: defaultInsulinType,
            origin = InsulinOrigin.Pump
        ))
    }

    private fun calculateInsulinImpact(start: Timestamp, end: Timestamp): Double {
        var totalUnitsAbsorbed = 0.0

        for (bolus in insulinApplications) {
            val curve = InsulinCurve(
                diaMinutes = bolus.insulinType.dia.value.toDouble(),
                peakMinutes = bolus.insulinType.peak.value.toDouble()
            )

            val timeStart = (start.ms - bolus.timestamp.ms) / 60000.0
            val timeEnd = (end.ms - bolus.timestamp.ms) / 60000.0

            val fractionStart = curve.spentFraction(timeStart)
            val fractionEnd = curve.spentFraction(timeEnd)

            totalUnitsAbsorbed += bolus.amount * (fractionEnd - fractionStart)
        }

        // Apply resistance factors
        val currentResistance = illnessFactor + (stressLevel * 0.5)
        // Exercise increases insulin sensitivity
        val currentSensitivity = 1.0 + (exerciseIntensity * 0.5)

        return (totalUnitsAbsorbed * isf) * (currentSensitivity / currentResistance)
    }

    private fun calculateCarbImpact(start: Timestamp, end: Timestamp): Double {
        var totalCarbsAbsorbed = 0.0

        for (meal in meals) {
            val type = meal.mealType
            var mealAbsorbedInWindow = 0.0

            val timeStart = (start.ms - meal.timestamp.ms) / 60000.0
            val timeEnd = (end.ms - meal.timestamp.ms) / 60000.0

            for (comp in type.components) {
                val curve = CarbCurveComponent(comp.peakMinutes.value.toDouble())
                val fStart = curve.absorbedFraction(timeStart)
                val fEnd = curve.absorbedFraction(timeEnd)
                // Weights are stored as integers (e.g. 70 for 70%)
                mealAbsorbedInWindow += meal.carbGrams * (comp.weight / 100.0) * (fEnd - fStart)
            }
            totalCarbsAbsorbed += mealAbsorbedInWindow
        }

        // Rise from carbs depends on Insulin-to-Carb ratio (IC)
        return (totalCarbsAbsorbed / ic) * isf
    }
}
