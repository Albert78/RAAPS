package de.dh.raaps.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.BgSampleKind

/**
 * Types of glucose sensors like "Libre3", "Dexcom G6", ...
 */
@Entity(
    tableName = "sensor_type"
)
data class SensorTypeEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val name: String
)

/**
 * Providers for glucose values.
 */
@Entity(
    tableName = "data_provider"
)
data class DataProviderEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val name: String,
    val type: String
)

@Entity(
    tableName = "glucose_reading",
    foreignKeys = [
        ForeignKey(
            entity = SensorTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["fk_source_sensor"]
        ),
        ForeignKey(
            entity = DataProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["fk_data_provider"]
        ),
    ],
    indices = [
        Index("timestamp_ms")
    ]
)
data class GlucoseReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = ID_UNDEFINED,
    val value_mgdl: Short,
    val sample_kind: BgSampleKind,
    val timestamp_ms: Long,
    val fk_data_provider: Long,
    val fk_source_sensor: Long
)