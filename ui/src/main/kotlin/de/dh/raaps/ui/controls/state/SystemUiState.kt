package de.dh.raaps.ui.controls.state

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.ApsIssue
import de.dh.raaps.core.aps.CoreState

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
    val nextExpectedTimestamp: Timestamp = Timestamp.INVALID,
    val readingsTimeDelay: Minutes = Minutes(5),
    val coreState: CoreState = CoreState.Uninitialized,
    val apsIssues: Set<ApsIssue> = emptySet()
)