package de.dh.raaps.core.repository

import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp

data class WakeupMetric(
    val tag: String,
    val wakeupId: UInt?,
    val scheduledTime: Timestamp,
    val dispatchTime: Timestamp,
    val onWakeupStartTime: Timestamp?,
    val onWakeupEndTime: Timestamp?
)

data class TickHandlerMetric(
    val tick: Tick,
    val handlerName: String,
    val startTime: Timestamp,
    val endTime: Timestamp
)