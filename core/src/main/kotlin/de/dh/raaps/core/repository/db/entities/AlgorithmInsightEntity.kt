package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.AlgorithmReasoning

@Entity(tableName = "algorithm_insights")
data class AlgorithmInsightEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val bgOriginal: BgValue,
    val bgFiltered: BgValue,
    val deviationPerTick: BgDelta,
    val iobAtPeak: Double,
    val cobAtPeak: Double,
    val predictedBgAtPeak: BgValue,
    val targetBg: BgValue,
    val isf: BgDelta,
    val cr: Double,
    val actionBolus: Double? = null,
    val actionTempBasalPercent: Int? = null,
    val actionTempBasalDurationInHours: Int? = null,
    val reasoning: AlgorithmReasoning
)
