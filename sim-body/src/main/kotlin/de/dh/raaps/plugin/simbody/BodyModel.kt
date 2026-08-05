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
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.getAmountForMinute
import de.dh.raaps.plugin.simbody.model.BodyProfile
import de.dh.raaps.plugin.simbody.repository.db.ImpactHistoryEntity
import de.dh.raaps.plugin.simbody.repository.db.SimBodyDao
import de.dh.raaps.plugin.simbody.repository.db.SimEventEntity
import de.dh.raaps.plugin.simbody.repository.db.SimulationStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

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
class BodyModel(
    initialProfile: BodyProfile,
    private val simBodyDao: SimBodyDao? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)

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
        set(value) {
            _exerciseIntensity.value = value
            persistState()
        }
    val exerciseIntensityFlow: StateFlow<Double> = _exerciseIntensity.asStateFlow()

    private val _illnessFactor = MutableStateFlow(1.0)
    var illnessFactor: Double
        get() = _illnessFactor.value
        set(value) {
            _illnessFactor.value = value
            persistState()
        }
    val illnessFactorFlow: StateFlow<Double> = _illnessFactor.asStateFlow()

    private val _stressLevel = MutableStateFlow(0.0)
    var stressLevel: Double
        get() = _stressLevel.value
        set(value) {
            _stressLevel.value = value
            persistState()
        }
    val stressLevelFlow: StateFlow<Double> = _stressLevel.asStateFlow()

    private val _isSensorEnabled = MutableStateFlow(true)
    var isSensorEnabled: Boolean
        get() = _isSensorEnabled.value
        set(value) {
            _isSensorEnabled.value = value
            persistState()
        }
    val isSensorEnabledFlow: StateFlow<Boolean> = _isSensorEnabled.asStateFlow()

    private val _sensorNoiseFactor = MutableStateFlow(0.0)
    var sensorNoiseFactor: Double
        get() = _sensorNoiseFactor.value
        set(value) {
            _sensorNoiseFactor.value = value
            persistState()
        }
    val sensorNoiseFactorFlow: StateFlow<Double> = _sensorNoiseFactor.asStateFlow()

    // Simulated Person Profile (metabolic parameters)
    private val _activeProfile = MutableStateFlow(initialProfile)
    var activeProfile: BodyProfile
        get() = _activeProfile.value
        private set(value) { _activeProfile.value = value }
    val activeProfileFlow: StateFlow<BodyProfile> = _activeProfile.asStateFlow()

    val isf: Double
        get() = activeProfile.isfBlocks.getAmountForMinute(Timestamp.now().minutesSinceMidnight())

    val cr: Double
        get() = activeProfile.crBlocks.getAmountForMinute(Timestamp.now().minutesSinceMidnight())

    val liverGlucoseOutputGph: Double
        get() = activeProfile.liverGlucoseOutputBlocks.getAmountForMinute(Timestamp.now().minutesSinceMidnight())

    val iob: Double
        get() {
            val now = Timestamp.now()
            return insulinApplications.sumOf { bolus ->
                val curve = InsulinCurve(
                    diaMinutes = bolus.insulinType.dia.value.toDouble(),
                    peakMinutes = bolus.insulinType.peak.value.toDouble()
                )
                val timeSinceBolus = (now.ms - bolus.timestamp.ms) / 60000.0
                bolus.amount * (1.0 - curve.spentFraction(timeSinceBolus)).coerceAtLeast(0.0)
            }
        }

    val cob: Double
        get() {
            val now = Timestamp.now()
            return meals.sumOf { meal ->
                meal.mealType.components.sumOf { comp ->
                    val curve = CarbCurveComponent(comp.peakMinutes.value.toDouble())
                    val timeSinceMeal = (now.ms - meal.timestamp.ms) / 60000.0
                    meal.carbGrams * (comp.weight / 100.0) * (1.0 - curve.absorbedFraction(timeSinceMeal)).coerceAtLeast(0.0)
                }
            }
        }

    fun setProfile(profile: BodyProfile) {
        activeProfile = profile
    }

    // Loading state
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: Boolean get() = _isLoaded.value
    val isLoadedFlow: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // Current state
    private val _bloodGlucose = MutableStateFlow(0.0)
    var bloodGlucose: Double
        get() = _bloodGlucose.value
        set(value) {
            _bloodGlucose.value = value
            persistState()
        }
    val bloodGlucoseFlow: StateFlow<Double> = _bloodGlucose.asStateFlow()

    private val _lastTickTimestamp = MutableStateFlow(Timestamp.now())
    var lastTickTimestamp: Timestamp
        get() = _lastTickTimestamp.value
        set(value) {
            _lastTickTimestamp.value = value
            persistState()
        }

    fun loadState() {
        val dao = simBodyDao ?: run {
            _isLoaded.value = true
            return
        }
        scope.launch {
            // Load simulation state
            val state = dao.getSimulationState()
            if (state != null) {
                _bloodGlucose.value = state.currentBgMgDl
                _lastTickTimestamp.value = Timestamp(state.lastTickTimestampMs)
                _exerciseIntensity.value = state.exerciseIntensity
                _stressLevel.value = state.stressLevel
                _illnessFactor.value = state.illnessFactor
                _isSensorEnabled.value = state.isSensorEnabled
                _sensorNoiseFactor.value = state.sensorNoiseFactor
            } else {
                // First run defaults
                _bloodGlucose.value = 120.0
                _lastTickTimestamp.value = Timestamp.now()
            }

            // Load active profile if exists
            dao.getActiveBodyProfile()?.let { entity ->
                activeProfile = BodyProfile(
                    crBlocks = parseBlocks(entity.crBlocks),
                    isfBlocks = parseBlocks(entity.isfBlocks),
                    liverGlucoseOutputBlocks = parseBlocks(entity.liverGlucoseOutputBlocks)
                )
            }

            val horizonMs = 10 * 60 * 60 * 1000L
            val threshold = Timestamp.now().ms - horizonMs

            // Load events (meals and boluses) from the last 10 hours
            val events = dao.getEventsSince(threshold)

            val loadedMeals = events.filter { it.type == "MEAL" }.map { event ->
                MealEntry(
                    timestamp = Timestamp(event.timestampMs),
                    carbGrams = event.amount,
                    mealType = if (event.detailId == defaultMealType.id) defaultMealType else defaultMealType
                )
            }
            meals.clear()
            meals.addAll(loadedMeals)

            val loadedInsulin = events.filter { it.type == "BOLUS" }.map { event ->
                InsulinApplication(
                    timestamp = Timestamp(event.timestampMs),
                    amount = event.amount,
                    insulinType = if (event.detailId == defaultInsulinType.id) defaultInsulinType else defaultInsulinType,
                    origin = event.insulinOrigin ?: InsulinOrigin.Pump,
                    provisional = false
                )
            }
            insulinApplications.clear()
            insulinApplications.addAll(loadedInsulin)

            // Load impact history
            val history = dao.getImpactsSince(threshold).map { entity ->
                Impacts(
                    carbImpact = entity.carbImpact,
                    insulinImpact = entity.insulinImpact,
                    endogenousImpact = entity.endogenousImpact,
                    exerciseImpact = entity.exerciseImpact,
                    stressImpact = entity.stressImpact,
                    currentTimestamp = Timestamp(entity.timestampMs)
                )
            }
            impactHistory.clear()
            impactHistory.addAll(history)

            _isLoaded.value = true
        }
    }

    private fun parseBlocks(json: String): List<Block> {
        val array = JSONArray(json)
        val list = mutableListOf<Block>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(Block(
                duration = Minutes(obj.getInt("duration").toShort()),
                amount = obj.getDouble("amount")
            ))
        }
        return list
    }

    private fun persistState() {
        val dao = simBodyDao ?: return
        scope.launch {
            dao.updateSimulationState(
                SimulationStateEntity(
                    currentBgMgDl = bloodGlucose,
                    lastTickTimestampMs = lastTickTimestamp.ms,
                    exerciseIntensity = exerciseIntensity,
                    stressLevel = stressLevel,
                    illnessFactor = illnessFactor,
                    isSensorEnabled = isSensorEnabled,
                    sensorNoiseFactor = sensorNoiseFactor
                )
            )
        }
    }

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
        val endogenousImpact = (liverGlucoseOutputGph / cr) * isf * durationHours

        // Exercise and Stress impact on BG level directly
        val exerciseImpact = exerciseIntensity * 60.0 * durationHours
        val stressImpact = stressLevel * 30.0 * durationHours

        // Total delta calculation
        val bgDelta = carbImpact - insulinImpact + endogenousImpact - exerciseImpact + stressImpact

        val newImpact = Impacts(carbImpact, insulinImpact, endogenousImpact, exerciseImpact, stressImpact, currentTimestamp)
        impactHistory.add(0, newImpact) // Add to top for history view

        simBodyDao?.let { dao ->
            scope.launch {
                dao.insertImpact(
                    ImpactHistoryEntity(
                        carbImpact = newImpact.carbImpact,
                        insulinImpact = newImpact.insulinImpact,
                        endogenousImpact = newImpact.endogenousImpact,
                        exerciseImpact = newImpact.exerciseImpact,
                        stressImpact = newImpact.stressImpact,
                        timestampMs = newImpact.currentTimestamp.ms
                    )
                )
            }
        }

        // Update BG state
        val newMgDl = bloodGlucose + bgDelta
        bloodGlucose = newMgDl.coerceIn(20.0, 500.0)

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

        simBodyDao?.let { dao ->
            scope.launch {
                dao.deleteOldImpacts(threshold)
                dao.deleteOldEvents(threshold)
            }
        }
    }

    /**
     * Simulates eating a meal.
     */
    fun eat(carbs: Double, type: MealType? = null) {
        val entry = MealEntry(
            timestamp = Timestamp.now(),
            carbGrams = carbs,
            mealType = type ?: defaultMealType
        )
        meals.add(entry)

        simBodyDao?.let { dao ->
            scope.launch {
                dao.insertEvent(
                    SimEventEntity(
                        type = "MEAL",
                        timestampMs = entry.timestamp.ms,
                        amount = entry.carbGrams,
                        detailId = entry.mealType.id
                    )
                )
            }
        }
    }

    /**
     * Simulates an insulin bolus.
     */
    fun bolus(
        amount: Double,
        type: InsulinType? = null,
        timestamp: Timestamp = Timestamp.now()
    ) {
        if (amount < SimBodyInsulinPump.SIM_PUMP_MIN_BOLUS_INCREMENT) return

        val entry = InsulinApplication(
            timestamp = timestamp,
            amount = amount,
            insulinType = type ?: defaultInsulinType,
            origin = InsulinOrigin.Pump,
            provisional = false
        )
        insulinApplications.add(entry)

        simBodyDao?.let { dao ->
            scope.launch {
                dao.insertEvent(
                    SimEventEntity(
                        type = "BOLUS",
                        timestampMs = entry.timestamp.ms,
                        amount = entry.amount,
                        detailId = entry.insulinType.id,
                        insulinOrigin = entry.origin
                    )
                )
            }
        }
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

        // Rise from carbs depends on Insulin-to-Carb ratio (CR)
        return (totalCarbsAbsorbed / cr) * isf
    }
}