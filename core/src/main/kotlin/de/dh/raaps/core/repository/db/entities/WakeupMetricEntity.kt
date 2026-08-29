package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.Timestamp

@Entity(tableName = "wakeup_metrics")
data class WakeupMetricEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val tag: String,
    val wakeupId: Int?,
    val scheduledTime: Timestamp,
    val dispatchTime: Timestamp,
    val onWakeupStartTime: Timestamp?,
    val onWakeupEndTime: Timestamp?
)