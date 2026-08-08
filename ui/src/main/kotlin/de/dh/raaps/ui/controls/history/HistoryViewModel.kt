package de.dh.raaps.ui.controls.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.ToDo
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TickPriority
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.util.PersistentLogger
import de.dh.raaps.core.SystemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BgTrend {
    DoubleUp,
    SingleUp,
    FortyFiveUp,
    Flat,
    FortyFiveDown,
    SingleDown,
    DoubleDown,
    NotComputable
}

data class CurrentBgData (
    val isValueOld: Boolean = false,
    val bgValue: BgValue = BgValue.INVALID,
    val delta: BgDelta? = null,
    val trend: BgTrend? = BgTrend.Flat,
    val timestamp: Timestamp = Timestamp(0),
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL
) {
    companion object {
        fun valid(
            bgValue: BgValue,
            delta: BgDelta? = null,
            trend: BgTrend? = BgTrend.Flat,
            timestamp: Timestamp = Timestamp(0),
            glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL
        ) = CurrentBgData(
            isValueOld = false,
            bgValue = bgValue,
            delta = delta,
            trend = trend,
            timestamp = timestamp,
            glucoseUnit = glucoseUnit
        )

        fun oldValue(
            bgValue: BgValue,
            timestamp: Timestamp = Timestamp(0),
            glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL
        ) = CurrentBgData(
            isValueOld = true,
            bgValue = bgValue,
            delta = null,
            trend = null,
            timestamp = timestamp,
            glucoseUnit = glucoseUnit
        )

        fun invalid(): CurrentBgData? = null
    }
}

data class CurrentBgUiState(
    val isLoading: Boolean,
    val isError: Boolean,
    val currentBgValue: CurrentBgData? = null,
    val nextExpectedTimestamp: Timestamp = Timestamp(0),
    val readingsTimeDelay: Minutes = Minutes(5)
)

data class HistoryUiState(
    val isLoading: Boolean,
    val isError: Boolean,
    val readings: List<BgReading> = listOf(),
    val insulinApplications: List<InsulinApplication> = listOf(),
    val meals: List<MealEntry> = listOf()
)

/**
 * ViewModel for blood glucose history and current status.
 */
class HistoryViewModel(
    private val systemRegistry: SystemRegistry
) : ViewModel(), TickHandler {
    private val _currentBgUiState = MutableStateFlow(CurrentBgUiState(
        isLoading = true,
        isError = false
    ))
    val currentBgUiState = _currentBgUiState.asStateFlow()

    private val _historyUiState = MutableStateFlow(HistoryUiState(isLoading = true, isError = false))
    val historyUiState = _historyUiState.asStateFlow()

    private val _iob = MutableStateFlow(0.0)
    val iob = _iob.asStateFlow()

    private val _cob = MutableStateFlow(0.0)
    val cob = _cob.asStateFlow()

    private val glucoseRepository = systemRegistry.glucoseRepository
    private val treatmentRepository = systemRegistry.treatmentRepository
    private val therapyManager = systemRegistry.therapyManager

    val calculationModel = systemRegistry.carbsInsulinCalculationModel

    private val _tickCounter = MutableStateFlow(0)

    override suspend fun onTick(tick: Tick) {
        _tickCounter.value++
    }

    init {
PersistentLogger.log("HistoryViewModel", "------------ calling registerTickHandler: priority=${TickPriority.UI}, handler=HistoryViewModel")
        systemRegistry.timeService.registerTickHandler(TickPriority.UI, this)

        viewModelScope.launch {
            combine(
                glucoseRepository.observeBgReadings(),
                treatmentRepository.observeInsulinApplications(),
                treatmentRepository.observeMeals(),
                therapyManager.currentTherapySettingsFlow,
                _tickCounter
            ) { readings, insulin, meals, settings, _ ->
                val historyLimit = Timestamp.now().minusHours(25)
                val filteredReadings = readings.filter { it.timestamp >= historyLimit }
                val filteredInsulin = insulin.filter { it.timestamp >= historyLimit }
                val filteredMeals = meals.filter { it.timestamp >= historyLimit }

                val dia = settings.insulinProfile.dia
                val peak = settings.insulinProfile.peak

                val now = Timestamp.now()
                _iob.value = calculationModel.iob(filteredInsulin, now, dia, peak)
                _cob.value = calculationModel.cob(filteredMeals, now)

                updateUiModel(filteredReadings, filteredInsulin, filteredMeals)
            }.collect { }
        }
    }

    private fun updateUiModel(
        readings: List<BgReading>,
        insulin: List<InsulinApplication>,
        meals: List<MealEntry>
    ) {
        ToDo.toBeImplemented("Read glucose unit from preferences")
        val glucoseUnit = GlucoseUnit.MG_DL

        val timestampNowMs = Timestamp.now().ms
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
                        readingsTimeDelay = readingsTimeDelay
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
                        readingsTimeDelay = readingsTimeDelay
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
                    readingsTimeDelay = readingsTimeDelay
                )
            }
        }

        Log.d(TAG, "Updating history data with ${readings.size} readings, ${insulin.size} insulin apps, ${meals.size} meals")
        _historyUiState.update {
            HistoryUiState(
                isLoading = false,
                isError = false,
                readings = readings,
                insulinApplications = insulin,
                meals = meals
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        systemRegistry.timeService.unregisterTickHandler(this)
    }

    companion object {
        val TAG = HistoryViewModel::class.simpleName

        class Factory(
            private val registry: SystemRegistry,
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return HistoryViewModel(registry) as T
            }
        }
    }
}
