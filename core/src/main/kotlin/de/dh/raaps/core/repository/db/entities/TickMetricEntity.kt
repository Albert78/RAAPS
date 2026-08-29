package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp

@Entity(tableName = "tick_metrics")
data class TickMetricEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val tick: Tick,
    val handlerName: String,
    val startTime: Timestamp,
    val endTime: Timestamp
)