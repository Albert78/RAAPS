package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Minutes

data class ApsHistorySnapshot(
    val ticks: List<BgReading?>,
    val tickInterval: Minutes
)