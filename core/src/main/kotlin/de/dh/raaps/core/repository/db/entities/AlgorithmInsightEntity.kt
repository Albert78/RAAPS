package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.AlgorithmReasoning

@Entity(tableName = "algorithm_insights")
data class AlgorithmInsightEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val bgOriginal: Short,
    val bgFiltered: Short,
    val deviationPerTick: Double,
    val iobAtPeak: Double,
    val cobAtPeak: Double,
    val cobEquivalentOfBasalAtPeak: Double,
    val predictedBgAtPeak: Short,
    val targetBg: Short,
    val isf: Double,
    val cr: Double,
    val reasoning: AlgorithmReasoning,
    val actionBolus: Double? = null,
    val actionTempBasalPercent: Int? = null,
    val actionTempBasalDurationInHours: Int? = null
)
