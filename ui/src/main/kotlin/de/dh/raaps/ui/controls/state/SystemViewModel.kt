package de.dh.raaps.ui.controls.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TickPriority
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.aps.ApsIssue
import de.dh.raaps.core.aps.CoreState
import de.dh.raaps.glucoseUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for real-time system status including current glucose, IOB, COB and APS state.
 */
class SystemViewModel(
    private val systemRegistry: SystemRegistry
) : ViewModel(), TickHandler {
    private val _currentBgUiState = MutableStateFlow(CurrentBgUiState(
        isLoading = true,
        isError = false
    ))
    val currentBgUiState = _currentBgUiState.asStateFlow()

    private val _iob = MutableStateFlow(InsulinAmount.ZERO)
    val iob = _iob.asStateFlow()

    private val _cob = MutableStateFlow(0.0)
    val cob = _cob.asStateFlow()

    private val glucoseRepository = systemRegistry.glucoseRepository
    private val treatmentRepository = systemRegistry.treatmentRepository
    private val therapyManager = systemRegistry.therapyManager
    private val systemManager = systemRegistry.systemManager

    val carbsInsulinCalculator = systemRegistry.carbsInsulinCalculator

    private val _tickCounter = MutableStateFlow(0)

    override suspend fun onTick(tick: Tick) {
        _tickCounter.value++
    }

    init {
        systemRegistry.timeService.registerTickHandler(TickPriority.UI, this, "SystemUI")

        viewModelScope.launch {
            combine(
                glucoseRepository.observeBgReadings(),
                treatmentRepository.observeInsulinApplications(),
                treatmentRepository.observeMeals(),
                therapyManager.currentTherapySettingsFlow,
                systemManager.coreState,
                systemManager.apsIssues,
                _tickCounter
            ) { args ->
                val readings = args[0] as List<BgReading>
                val insulin = args[1] as List<InsulinApplication>
                val meals = args[2] as List<MealEntry>
                val settings = args[3] as CurrentTherapySettings
                val coreState = args[4] as CoreState
                val apsIssues = args[5] as Set<ApsIssue>

                val now = Timestamp.now()
                val dia = settings.insulinProfile.dia
                val peak = settings.insulinProfile.peak

                // Filter for IOB/COB calculation (usually last few hours is enough, but calculator handles it)
                _iob.value = carbsInsulinCalculator.iob(insulinApplications = insulin, timestamp = now, dia = dia, peak = peak)
                _cob.value = carbsInsulinCalculator.cob(meals = meals, timestamp = now, includeFutureMeals = false)

                updateUiModel(readings, coreState, apsIssues)
            }.collect { }
        }
    }

    private fun updateUiModel(
        readings: List<BgReading>,
        coreState: CoreState,
        apsIssues: Set<ApsIssue>
    ) {
        val glucoseUnit = systemRegistry.appPreferencesRepository.cachedPreferences.value?.glucoseUnit ?: GlucoseUnit.MG_DL

        val now = Timestamp.now()
        val timestampNowMs = now.ms
        val limitMs = timestampNowMs - 20 * 60 * 1000L

        val recentReadings = readings.filter {
            it.sampleKind == BgSampleKind.Value && it.timestamp.ms >= limitMs
        }

        val latest = recentReadings.lastOrNull()

        val nextExpectedTimestamp = systemRegistry.glucoseSourceManager.predictNextValueTimestamp()
        val readingsTimeDelay = systemRegistry.glucoseSourceManager.readingsTimeDelay

        _currentBgUiState.update {
            if (latest == null) {
                val limit2HoursMs = timestampNowMs - 2 * 60 * 60 * 1000L
                val olderReading = readings
                    .lastOrNull { it.sampleKind == BgSampleKind.Value && it.timestamp.ms > limit2HoursMs }

                if (olderReading == null) {
                    CurrentBgUiState(
                        isLoading = false,
                        isError = false,
                        currentBgValue = CurrentBgData.invalid(),
                        nextExpectedTimestamp = nextExpectedTimestamp,
                        readingsTimeDelay = readingsTimeDelay,
                        coreState = coreState,
                        apsIssues = apsIssues
                    )
                } else {
                    CurrentBgUiState(
                        isLoading = false,
                        isError = false,
                        currentBgValue = CurrentBgData.oldValue(
                            bgValue = olderReading.value,
                            timestamp = olderReading.timestamp
                        ),
                        nextExpectedTimestamp = nextExpectedTimestamp,
                        readingsTimeDelay = readingsTimeDelay,
                        coreState = coreState,
                        apsIssues = apsIssues
                    )
                }
            } else {
                val bgValue = latest.value

                val n = recentReadings.size
                val regressionDelta5m: Double? = if (n >= 2) {
                    val firstTs = recentReadings.first().timestamp.ms
                    var sumX = 0.0
                    var sumY = 0.0
                    var sumXY = 0.0
                    var sumXX = 0.0
                    recentReadings.forEach { reading ->
                        val x = (reading.timestamp.ms - firstTs) / 60000.0
                        val y = reading.value.mgdl.toDouble()
                        sumX += x
                        sumY += y
                        sumXY += x * y
                        sumXX += x * x
                    }
                    val denominator = n * sumXX - sumX * sumX
                    if (denominator != 0.0) {
                        val slopePerMin = (n * sumXY - sumX * sumY) / denominator
                        slopePerMin * 5.0
                    } else 0.0
                } else {
                    null
                }

                val trend: BgTrend? = if (regressionDelta5m == null) null else when {
                    regressionDelta5m >= 14.0 -> BgTrend.DoubleUp
                    regressionDelta5m >= 10.0 -> BgTrend.SingleUp
                    regressionDelta5m >= 6.0 -> BgTrend.FortyFiveUp
                    regressionDelta5m <= -14.0 -> BgTrend.DoubleDown
                    regressionDelta5m <= -10.0 -> BgTrend.SingleDown
                    regressionDelta5m <= -6.0 -> BgTrend.FortyFiveDown
                    else -> BgTrend.Flat
                }

                CurrentBgUiState(
                    isLoading = false,
                    isError = false,
                    currentBgValue = CurrentBgData.valid(
                        bgValue = bgValue,
                        delta = regressionDelta5m?.let { BgDelta.fromMgDl(it.toInt()) },
                        trend = trend,
                        timestamp = latest.timestamp,
                        glucoseUnit = glucoseUnit
                    ),
                    nextExpectedTimestamp = nextExpectedTimestamp,
                    readingsTimeDelay = readingsTimeDelay,
                    coreState = coreState,
                    apsIssues = apsIssues
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        systemRegistry.timeService.unregisterTickHandler(this)
    }

    companion object {
        val TAG = SystemViewModel::class.simpleName

        class Factory(
            private val registry: SystemRegistry,
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return SystemViewModel(registry) as T
            }
        }
    }
}