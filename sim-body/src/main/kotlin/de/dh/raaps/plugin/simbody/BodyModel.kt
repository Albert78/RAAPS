package de.dh.raaps.plugin.simbody

import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.calculation.CarbCurveComponent
import de.dh.raaps.common.model.calculation.InsulinCurve
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Models a diabetic's body for simulation purposes.
 * It tracks blood glucose levels influenced by meals, insulin, exercise, and health states.
 */
class BodyModel {
    // Simulation Defaults (independent of database)
    private val defaultInsulinType = InsulinType(
        name = "Sim Aspart",
        dia = Minutes.ofHours(5),
        peak = Minutes(75)
    )

    private val defaultMealType = MealType(
        name = "Sim Standard Meal",
        components = listOf(
            CarbCurveComponentData(weight = 70, peakMinutes = Minutes(75)),
            CarbCurveComponentData(weight = 30, peakMinutes = Minutes(150))
        ),
        cat = Minutes.ofHours(4)
    )

    // Inputs (historical data)
    val meals = CopyOnWriteArrayList<MealEntry>()
    val insulinApplications = CopyOnWriteArrayList<InsulinApplication>()

    // Health factors (external influences, controlled via UI)
    var exerciseIntensity: Double = 0.0 // 0.0 (rest) to 1.0 (max)
    var illnessFactor: Double = 1.0     // > 1.0 increases insulin resistance
    var stressLevel: Double = 0.0      // 0.0 to 1.0, increases BG and resistance

    // Simulated Person Profile (metabolic parameters)
    var isf: Double = 50.0             // Insulin Sensitivity Factor (mg/dL per Unit)
    var ic: Double = 10.0              // Insulin to Carb ratio (g Carbs per Unit)
    var basalRateUph: Double = 1.0     // Normal basal insulin delivery (Units per hour)

    // Current state
    var bloodGlucose: BgValue = BgValue.fromMgDl(120)
    var lastTickTimestamp: Timestamp = Timestamp.now()

    /**
     * Advances the simulation state to the [currentTimestamp].
     * Calculates the delta in blood glucose based on all active influences.
     */
    fun tick(currentTimestamp: Timestamp = Timestamp.now()) {
        val durationMs = currentTimestamp.ms - lastTickTimestamp.ms
        if (durationMs <= 0) return

        val durationHours = durationMs / (1000.0 * 60 * 60)

        // Calculate deltas
        val insulinImpact = calculateInsulinImpact(lastTickTimestamp, currentTimestamp)
        val carbImpact = calculateCarbImpact(lastTickTimestamp, currentTimestamp)
        
        // Liver production offsets normal basal insulin. 
        // We assume liver production matches the profile basal rate.
        val endogenousImpact = (basalRateUph * isf) * durationHours
        
        // Exercise and Stress impact on BG level directly
        val exerciseImpact = exerciseIntensity * 60.0 * durationHours
        val stressImpact = stressLevel * 30.0 * durationHours

        // Total delta calculation
        val bgDelta = carbImpact - insulinImpact + endogenousImpact - exerciseImpact + stressImpact

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
    fun bolus(units: Double, type: InsulinType? = null) {
        insulinApplications.add(InsulinApplication(
            timestamp = Timestamp.now(),
            insulinUnits = units,
            insulinType = type ?: defaultInsulinType
        ))
    }

    private fun calculateInsulinImpact(start: Timestamp, end: Timestamp): Double {
        var totalUnitsAbsorbed = 0.0
        
        for (app in insulinApplications) {
            val curve = InsulinCurve(
                diaMinutes = app.insulinType.dia.value.toDouble(),
                peakMinutes = app.insulinType.peak.value.toDouble()
            )
            
            val timeStart = (start.ms - app.timestamp.ms) / 60000.0
            val timeEnd = (end.ms - app.timestamp.ms) / 60000.0
            
            val fractionStart = curve.spentFraction(timeStart)
            val fractionEnd = curve.spentFraction(timeEnd)
            
            totalUnitsAbsorbed += app.insulinUnits * (fractionEnd - fractionStart)
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