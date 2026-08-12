package de.dh.raaps.core.repository

import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.CoreInsight
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.entities.CoreInsightEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CoreInsightRepository(private val appDatabase: AppDatabase) {
    private val dao = appDatabase.coreInsightDao()

    suspend fun saveInsight(insight: CoreInsight) {
        dao.insert(insight.toEntity())
    }

    fun observeInsights(): Flow<List<CoreInsight>> {
        return dao.observeAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun pruneOldInsights(olderThan: Timestamp) {
        dao.pruneOlderThan(olderThan.ms)
    }

    private fun CoreInsight.toEntity() = CoreInsightEntity(
        timestamp = timestamp,
        bgOriginal = bgOriginal,
        bgFiltered = bgFiltered,
        deviationPerTick = deviationPerTick,
        iobAtPeak = iobAtPeak,
        cobAtPeak = cobAtPeak,
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
        iobAtPeak = iobAtPeak,
        cobAtPeak = cobAtPeak,
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
