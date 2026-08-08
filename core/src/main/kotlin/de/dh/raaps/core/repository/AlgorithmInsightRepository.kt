package de.dh.raaps.core.repository

import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.AlgorithmInsight
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.entities.AlgorithmInsightEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlgorithmInsightRepository(private val appDatabase: AppDatabase) {
    private val dao = appDatabase.algorithmInsightDao()

    suspend fun saveInsight(insight: AlgorithmInsight) {
        dao.insert(insight.toEntity())
    }

    fun observeInsights(): Flow<List<AlgorithmInsight>> {
        return dao.observeAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun pruneOldInsights(olderThan: Timestamp) {
        dao.pruneOlderThan(olderThan.ms)
    }

    private fun AlgorithmInsight.toEntity() = AlgorithmInsightEntity(
        timestamp = timestamp,
        bgOriginal = bgOriginal,
        bgFiltered = bgFiltered,
        deviationPerTick = deviationPerTick,
        iobAtPeak = iobAtPeak,
        cobAtPeak = cobAtPeak,
        cobEquivalentOfBasalAtPeak = cobEquivalentOfBasalAtPeak,
        predictedBgAtPeak = predictedBgAtPeak,
        targetBg = targetBg,
        isf = isf,
        cr = cr,
        reasoning = reasoning,
        actionBolus = actionBolus,
        actionTempBasalUnitsPerHour = actionTempBasalUnitsPerHour,
        actionTempBasalDurationInHours = actionTempBasalDurationInHours
    )

    private fun AlgorithmInsightEntity.toDomain() = AlgorithmInsight(
        timestamp = timestamp,
        bgOriginal = bgOriginal,
        bgFiltered = bgFiltered,
        deviationPerTick = deviationPerTick,
        iobAtPeak = iobAtPeak,
        cobAtPeak = cobAtPeak,
        cobEquivalentOfBasalAtPeak = cobEquivalentOfBasalAtPeak,
        predictedBgAtPeak = predictedBgAtPeak,
        targetBg = targetBg,
        isf = isf,
        cr = cr,
        reasoning = reasoning,
        actionBolus = actionBolus,
        actionTempBasalUnitsPerHour = actionTempBasalUnitsPerHour,
        actionTempBasalDurationInHours = actionTempBasalDurationInHours
    )
}
