package de.dh.raaps.core.repository

import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.CoreInsight
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.entities.CoreInsightEntity
import de.dh.raaps.core.repository.db.entities.TickMetricEntity
import de.dh.raaps.core.repository.db.entities.WakeupMetricEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SystemMetricsRepository(private val appDatabase: AppDatabase) {
    private val dao = appDatabase.systemMetricsDao()

    suspend fun saveInsight(insight: CoreInsight) {
        dao.insert(insight.toEntity())
    }

    suspend fun saveWakeupMetric(metric: WakeupMetric) {
        dao.insertWakeupMetric(metric.toEntity())
    }

    suspend fun saveTickMetric(metric: TickHandlerMetric) {
        dao.insertTickMetric(metric.toEntity())
    }

    fun observeInsights(): Flow<List<CoreInsight>> {
        return dao.observeAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun pruneOldInsights(olderThan: Timestamp) {
        dao.pruneOlderThan(olderThan.ms)
        dao.pruneWakeupMetricsOlderThan(olderThan.ms)
        dao.pruneTickMetricsOlderThan(olderThan.ms)
    }

    private fun WakeupMetric.toEntity() = WakeupMetricEntity(
        tag = tag,
        wakeupId = wakeupId?.toInt(),
        scheduledTime = scheduledTime,
        dispatchTime = dispatchTime,
        onWakeupStartTime = onWakeupStartTime,
        onWakeupEndTime = onWakeupEndTime
    )

    private fun TickHandlerMetric.toEntity() = TickMetricEntity(
        tick = tick,
        handlerName = handlerName,
        startTime = startTime,
        endTime = endTime
    )

    private fun CoreInsight.toEntity() = CoreInsightEntity(
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

    private fun CoreInsightEntity.toDomain() = CoreInsight(
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
}