package de.dh.raaps.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp

@Entity(
    tableName = "tick_state",
    indices = [
        Index("tick")
    ]
)
data class TickStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = ID_UNDEFINED,
    val tick: Tick,
    val bg_value: BgValue?,
    val bg_sample_kind: BgSampleKind?,
    val bg_readig_timestamp: Timestamp?
)