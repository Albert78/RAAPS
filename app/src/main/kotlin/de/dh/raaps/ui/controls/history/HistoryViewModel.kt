package de.dh.raaps.ui.controls.history

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.MainApplication
import de.dh.raaps.common.model.ToDo
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.model.APS
import de.dh.raaps.model.ApsHistorySnapshot
import de.dh.raaps.model.CoreState
import de.dh.raaps.model.PredictionTickState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val currentBgValue: CurrentBgData? = null
)

data class HistoryUiState(
    val isLoading: Boolean,
    val isError: Boolean,
    val historyTicks: List<PredictionTickState?> = listOf(),
    val tickInterval: Minutes = Minutes(5)
)

class HistoryViewModel(
    val application: MainApplication
) : AndroidViewModel(application) {
    private val _currentBgUiState = MutableStateFlow(CurrentBgUiState(
        isLoading = true,
        isError = false
    ))
    val currentBgUiState = _currentBgUiState.asStateFlow()

    private val _historyUiState = MutableStateFlow(HistoryUiState(isLoading = true, isError = false))
    val historyUiState = _historyUiState.asStateFlow()

    private val aps: APS = application.aps
    private val dataRepository = application.dataRepository

    init {
        viewModelScope.launch {
            // This will block the thread until the core is idle
            aps.coreState.first { it == CoreState.Idle }
            reload_suspend()
            aps.lastDataTime.collect {
                reload_suspend()
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            reload_suspend()
        }
    }

    private suspend fun reload_suspend() {
        updateUiModel(aps.rollingHistory.getSnapshot())
    }

    private fun updateUiModel(apsHistory: ApsHistorySnapshot) {
        // TODO: Read from preferences
        ToDo.toBeImplemented("Read glucose unit from preferences")
        val glucoseUnit = GlucoseUnit.MG_DL

        val timestampNowMs = Timestamp.now().ms
        val limitMs = timestampNowMs - 20 * 60 * 1000L

        val recentTicksWithBg = apsHistory.ticks.filterNotNull().filter {
            it.bg != null && it.bg!!.timestamp.ms >= limitMs
        }
        // Prefer the absolute current value from the core, if it's fresh enough
        val currentBg = aps.getCurrentBg()
        val latest = if (currentBg != null && currentBg.timestamp.ms >= limitMs) {
            currentBg
        } else {
            // Fallback to history if current core value is missing or too old
            recentTicksWithBg.lastOrNull()?.bg
        }

        _currentBgUiState.update {
            if (latest == null) {
                val limit2HoursMs = timestampNowMs - 2 * 60 * 60 * 1000L
                val olderTickData = apsHistory.ticks
                    .map({ tickState -> Pair(tickState?.bg, tickState?.bg?.timestamp) })
                    .lastOrNull(
                        { pair ->
                            val bg = pair.first
                            val timestamp = pair.second
                            return@lastOrNull bg != null && timestamp != null && timestamp.ms > limit2HoursMs
                        }
                    )
                if (olderTickData == null) {
                    // Invalid value
                    CurrentBgUiState(isLoading = false, isError = false, currentBgValue = CurrentBgData.invalid())
                } else {
                    // Older tick state present
                    val bg = olderTickData.first!!
                    val timestamp = olderTickData.second!!

                    // Old value
                    CurrentBgUiState(
                        isLoading = false,
                        isError = false,
                        currentBgValue = CurrentBgData.oldValue(
                            bgValue = bg.value,
                            timestamp = timestamp
                        )
                    )
                }
            } else {
                val bgValue = latest.value

                // Calculate trend using linear regression over the points in the window
                val n = recentTicksWithBg.size
                val regressionDelta5m: Double? = if (n >= 2) {
                    val firstTs = recentTicksWithBg.first().bg!!.timestamp.ms
                    var sumX = 0.0
                    var sumY = 0.0
                    var sumXY = 0.0
                    var sumXX = 0.0
                    recentTicksWithBg.forEach { tick ->
                        val x = (tick.bg!!.timestamp.ms - firstTs) / 60000.0
                        val y = tick.bg!!.value.mgdl.toDouble()
                        sumX += x
                        sumY += y
                        sumXY += x * y
                        sumXX += x * x
                    }
                    val denominator = n * sumXX - sumX * sumX
                    if (denominator != 0.0) {
                        val slopePerMin = (n * sumXY - sumX * sumY) / denominator
                        slopePerMin * 5.0 // Normalize to a 5-minute interval
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

                // TODO: Mark a BG value as "old" if the last known trend was rising or falling and
                // the last known BG value is some time ago

                // Valid value
                CurrentBgUiState(
                    isLoading = false,
                    isError = false,
                    currentBgValue = CurrentBgData.valid(
                        bgValue = bgValue,
                        delta = regressionDelta5m?.let { BgDelta.fromMgDl(it.toInt()) },
                        trend = trend,
                        timestamp = latest.timestamp,
                        glucoseUnit = glucoseUnit
                    )
                )
            }
        }

        Log.d(TAG, "Updating history data")
        _historyUiState.update {
            HistoryUiState(
                isLoading = false,
                isError = false,
                historyTicks = apsHistory.ticks,
                tickInterval = apsHistory.tickInterval
            )
        }
    }


    companion object {
        val TAG = HistoryViewModel::class.simpleName

        class Factory(
            private val application: Application,
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = application as MainApplication
                return HistoryViewModel(app) as T
            }
        }
    }
}