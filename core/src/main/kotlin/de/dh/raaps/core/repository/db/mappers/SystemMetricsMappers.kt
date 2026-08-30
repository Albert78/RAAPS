package de.dh.raaps.core.repository.db.mappers

import de.dh.raaps.core.aps.CoreInsight
import de.dh.raaps.core.repository.TickHandlerMetric
import de.dh.raaps.core.repository.WakeupMetric
import de.dh.raaps.core.repository.db.entities.CoreInsightEntity
import de.dh.raaps.core.repository.db.entities.TickMetricEntity
import de.dh.raaps.core.repository.db.entities.WakeupMetricEntity

fun WakeupMetric.toEntity() = WakeupMetricEntity(
    tag = tag,
    wakeupId = wakeupId?.toInt(),
    scheduledTime = scheduledTime,
    dispatchTime = dispatchTime,
    onWakeupStartTime = onWakeupStartTime,
    onWakeupEndTime = onWakeupEndTime
)

fun TickHandlerMetric.toEntity() = TickMetricEntity(
    tick = tick,
    handlerName = handlerName,
    startTime = startTime,
    endTime = endTime
)

fun CoreInsight.toEntity() = CoreInsightEntity(
    timestamp = timestamp,
    bgOriginal = bgOriginal,
    bgFiltered = bgFiltered,
    deviationPerTick = deviationPerTick,
    futureActiveInsulin = futureActiveInsulin,
    futureActiveCarbs = futureActiveCarbs,
    predictedBgAtPeak = predictedBgAtPeak,
    targetBg = targetBg,
    isf = isf,
    cr = cr,
    actionBolus = actionBolus,
    actionTempBasalPercent = actionTempBasalPercent,
    actionTempBasalDurationInHours = actionTempBasalDurationInHours,
    reasoning = reasoning
)

fun CoreInsightEntity.toDomain() = CoreInsight(
    timestamp = timestamp,
    bgOriginal = bgOriginal,
    bgFiltered = bgFiltered,
    deviationPerTick = deviationPerTick,
    futureActiveInsulin = futureActiveInsulin,
    futureActiveCarbs = futureActiveCarbs,
    predictedBgAtPeak = predictedBgAtPeak,
    targetBg = targetBg,
    isf = isf,
    cr = cr,
    reasoning = reasoning,
    actionBolus = actionBolus,
    actionTempBasalPercent = actionTempBasalPercent,
    actionTempBasalDurationInHours = actionTempBasalDurationInHours
)