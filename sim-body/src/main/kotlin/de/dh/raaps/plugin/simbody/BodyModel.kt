package de.dh.raaps.plugin.simbody

import androidx.compose.runtime.mutableStateListOf
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.ID_MEAL_FAST
import de.dh.raaps.common.model.ID_MEAL_HIGH_FAT
import de.dh.raaps.common.model.ID_MEAL_SLOW
import de.dh.raaps.common.model.ID_MEAL_STANDARD
import de.dh.raaps.common.model.InsulinAmount
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
import de.dh.raaps.plugin.simbody.repository.db.SimBodyDao
import de.dh.raaps.plugin.simbody.repository.db.SimEventEntity
import de.dh.raaps.plugin.simbody.repository.db.SimHistoryEntity
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

data class SimInsulinApplication(
    val timestamp: Timestamp,
    val amount: InsulinAmount,
    val peak: Minutes,
    val dia: Minutes
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

    private val defaultMealType = SIM_MEAL_TYPES.first { it.id == ID_MEAL_STANDARD }

    // Inputs (historical data) - using Compose state for UI updates
    val meals = mutableStateListOf<MealEntry>()
    val insulinApplications = mutableStateListOf<SimInsulinApplication>()
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

    val iob: InsulinAmount
        get() {
            val now = Timestamp.now()
            return insulinApplications.fold(InsulinAmount.ZERO) { acc, bolus ->
                val curve = InsulinCurve(
                    diaMinutes = bolus.dia.value.toDouble(),
                    peakMinutes = bolus.peak.value.toDouble()
                )
                val timeSinceBolus = (now.ms - bolus.timestamp.ms) / 60000.0
                acc + bolus.amount * (1.0 - curve.spentFraction(timeSinceBolus)).coerceAtLeast(0.0)
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
            // Explicitly persist a history entry when BG is set manually
            persistHistory(Timestamp.now(), value)
            persistState()
        }
    val bloodGlucoseFlow: StateFlow<Double> = _bloodGlucose.asStateFlow()

    /**
     * Retrieves the blood glucose value from the past (delayed).
     * Falls back to the earliest available value if the simulation hasn't run long enough.
     */
    suspend fun getDelayedBloodGlucose(delayMinutes: Int): Double {
        val dao = simBodyDao ?: return bloodGlucose
        val targetMs = Timestamp.now().ms - delayMinutes * 60 * 1000L

        // Try to find the value closest to the target time
        val delayedEntry = dao.getHistoryNear(targetMs)
        if (delayedEntry != null) {
            return delayedEntry.bgMgDl
        }

        // Fallback: earliest available value
        return dao.getEarliestHistoryEntry()?.bgMgDl ?: bloodGlucose
    }

    private val _lastSimulationTimestamp = MutableStateFlow(Timestamp.now())
    var lastSimulationTimestamp: Timestamp
        get() = _lastSimulationTimestamp.value
        set(value) {
            _lastSimulationTimestamp.value = value
            persistState()
        }

    fun loadState() {
        val dao = simBodyDao ?: run {
            _isLoaded.value = true
            return
        }
        scope.launch {
            try {
                // Load simulation state (UI settings)
                val state = dao.getSimulationState()
                if (state != null) {
                    _lastSimulationTimestamp.value = Timestamp(state.lastSimulationTimestampMs)
                    _exerciseIntensity.value = state.exerciseIntensity
                    _stressLevel.value = state.stressLevel
                    _illnessFactor.value = state.illnessFactor
                    _isSensorEnabled.value = state.isSensorEnabled
                    _sensorNoiseFactor.value = state.sensorNoiseFactor
                } else {
                    // First run defaults
                    // Set last simulation timestamp to 5 minutes ago so heartbeat triggers immediately on start
                    _lastSimulationTimestamp.value = Timestamp(Timestamp.now().ms - 5 * 60 * 1000L)
                    _isSensorEnabled.value = true
                    _sensorNoiseFactor.value = 0.0
                }

                // Load latest BG from history
                val latestHistory = dao.getLatestHistoryEntry()
                if (latestHistory != null) {
                    _bloodGlucose.value = latestHistory.bgMgDl
                } else {
                    _bloodGlucose.value = 120.0
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
                        carbGrams = event.amount.iu,
                        mealType = if (event.detailId == defaultMealType.id) defaultMealType else defaultMealType
                    )
                }
                meals.clear()
                meals.addAll(loadedMeals)

                val loadedInsulin = events.filter { it.type == "BOLUS" }.map { event ->
                    val type = defaultInsulinType
                    SimInsulinApplication(
                        timestamp = Timestamp(event.timestampMs),
                        amount = event.amount,
                        peak = type.peak,
                        dia = type.dia
                    )
                }
                insulinApplications.clear()
                insulinApplications.addAll(loadedInsulin)

                // Load impact history from unified history table
                val history = dao.getHistorySince(threshold).map { entity ->
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
            } catch (e: Exception) {
                android.util.Log.e("BodyModel", "Error loading state from database", e)
                // Fallback defaults if DB fails
                if (_bloodGlucose.value == 0.0) _bloodGlucose.value = 120.0
            } finally {
                _isLoaded.value = true
            }
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
                    lastSimulationTimestampMs = lastSimulationTimestamp.ms,
                    exerciseIntensity = exerciseIntensity,
                    stressLevel = stressLevel,
                    illnessFactor = illnessFactor,
                    isSensorEnabled = isSensorEnabled,
                    sensorNoiseFactor = sensorNoiseFactor
                )
            )
        }
    }

    private fun persistHistory(
        timestamp: Timestamp,
        bg: Double,
        carbImpact: Double = 0.0,
        insulinImpact: Double = 0.0,
        endogenousImpact: Double = 0.0,
        exerciseImpact: Double = 0.0,
        stressImpact: Double = 0.0
    ) {
        val dao = simBodyDao ?: return
        scope.launch {
            dao.insertHistory(
                SimHistoryEntity(
                    timestampMs = timestamp.ms,
                    bgMgDl = bg,
                    carbImpact = carbImpact,
                    insulinImpact = insulinImpact,
                    endogenousImpact = endogenousImpact,
                    exerciseImpact = exerciseImpact,
                    stressImpact = stressImpact
                )
            )
        }
    }

    /**
     * Advances the simulation state to the [currentTimestamp].
     * Calculates the delta in blood glucose based on all active influences.
     */
    fun advanceTo(currentTimestamp: Timestamp = Timestamp.now()) {
        val durationMs = currentTimestamp.ms - lastSimulationTimestamp.ms

        val durationHours = durationMs / (1000.0 * 60 * 60)

        // Calculate deltas
        val insulinImpact = calculateInsulinImpact(lastSimulationTimestamp, currentTimestamp)
        val carbImpact = calculateCarbImpact(lastSimulationTimestamp, currentTimestamp)

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

        // Update BG state - bypass setter to avoid duplicate history entry
        val newMgDl = (bloodGlucose + bgDelta).coerceIn(20.0, 500.0)
        _bloodGlucose.value = newMgDl

        // Persist history entry
        persistHistory(
            timestamp = currentTimestamp,
            bg = newMgDl,
            carbImpact = newImpact.carbImpact,
            insulinImpact = newImpact.insulinImpact,
            endogenousImpact = newImpact.endogenousImpact,
            exerciseImpact = newImpact.exerciseImpact,
            stressImpact = newImpact.stressImpact
        )

        // Bypass setter to avoid redundant persistState calls
        _lastSimulationTimestamp.value = currentTimestamp
        persistState()
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
                dao.deleteOldHistory(threshold)
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
        meals.add(0, entry)

        simBodyDao?.let { dao ->
            scope.launch {
                dao.insertEvent(
                    SimEventEntity(
                        type = "MEAL",
                        timestampMs = entry.timestamp.ms,
                        amount = InsulinAmount(entry.carbGrams),
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
        amount: InsulinAmount,
        type: InsulinType? = null,
        timestamp: Timestamp = Timestamp.now()
    ) {
        if (amount < SimBodyInsulinPump.SIM_PUMP_MIN_BOLUS_INCREMENT) return

        val insulinType = type ?: defaultInsulinType
        val entry = SimInsulinApplication(
            timestamp = timestamp,
            amount = amount,
            peak = insulinType.peak,
            dia = insulinType.dia
        )
        insulinApplications.add(0, entry)

        simBodyDao?.let { dao ->
            scope.launch {
                dao.insertEvent(
                    SimEventEntity(
                        type = "BOLUS",
                        timestampMs = entry.timestamp.ms,
                        amount = entry.amount,
                        detailId = insulinType.id,
                        insulinOrigin = InsulinOrigin.Pump
                    )
                )
            }
        }
    }

    private fun calculateInsulinImpact(start: Timestamp, end: Timestamp): Double {
        var totalUnitsAbsorbed = InsulinAmount.ZERO

        for (bolus in insulinApplications) {
            val curve = InsulinCurve(
                diaMinutes = bolus.dia.value.toDouble(),
                peakMinutes = bolus.peak.value.toDouble()
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

        return (totalUnitsAbsorbed.iu * isf) * (currentSensitivity / currentResistance)
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

    companion object {
        val SIM_MEAL_TYPES = listOf(
            MealType(
                id = ID_MEAL_FAST,
                name = "Schnelle KE",
                components = listOf(CarbCurveComponentData(weight = 100, peakMinutes = Minutes(45))),
                cat = Minutes.ofHours(2)
            ),
            MealType(
                id = ID_MEAL_STANDARD,
                name = "Standard-Essen",
                components = listOf(
                    CarbCurveComponentData(weight = 70, peakMinutes = Minutes(75)),
                    CarbCurveComponentData(weight = 30, peakMinutes = Minutes(150))
                ),
                cat = Minutes.ofHours(4)
            ),
            MealType(
                id = ID_MEAL_HIGH_FAT,
                name = "Fettreiches Essen",
                components = listOf(
                    CarbCurveComponentData(weight = 40, peakMinutes = Minutes(90)),
                    CarbCurveComponentData(weight = 60, peakMinutes = Minutes(240))
                ),
                cat = Minutes.ofHours(8)
            ),
            MealType(
                id = ID_MEAL_SLOW,
                name = "Langsames Essen",
                components = listOf(CarbCurveComponentData(weight = 100, peakMinutes = Minutes(180))),
                cat = Minutes.ofHours(6)
            )
        )

        fun findSimMealType(id: String): MealType? = SIM_MEAL_TYPES.find { it.id == id }
    }
}