package de.dh.raaps.data.db

import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.data.db.entities.DataProviderEntity
import de.dh.raaps.data.db.entities.GlucoseReadingEntity
import de.dh.raaps.data.db.entities.SensorTypeEntity
import de.dh.raaps.data.db.entities.TickStateEntity
import de.dh.raaps.model.TickState

fun BgReading.toNewEntity(dataProviderId: Long, sourceSensorId: Long) = GlucoseReadingEntity(
    value_mgdl = this.value.mgdl,
    sample_kind = this.sampleKind,
    timestamp_ms = this.timestamp.ms,
    fk_data_provider = dataProviderId,
    fk_source_sensor = sourceSensorId
)

fun SensorType.toEntity() = SensorTypeEntity(
    id = this.id,
    name = this.name
)

fun SensorTypeEntity.toModel() = SensorType(
    id = this.id,
    name = this.name
)

fun DataProvider.toEntity() = DataProviderEntity(
    id = this.id,
    name = this.name,
    type = this.type
)

fun DataProviderEntity.toModel() = DataProvider(
    id = this.id,
    name = this.name,
    type = this.type
)

fun TickState.toEntity() = TickStateEntity(
    id = this.id,
    tick = this.tick,
    bg_value = this.bg?.value,
    bg_sample_kind = this.bg?.sampleKind,
    bg_readig_timestamp = this.bg?.timestamp
)

fun TickStateEntity.toModel(): TickState {
    val bgValue = this.bg_value
    val sampleKind = this.bg_sample_kind
    val timestamp = this.bg_readig_timestamp
    val bg = if (bgValue != null && sampleKind != null && timestamp != null)
        BgReading(
            bgValue,
            sampleKind,
            timestamp
        )
    else null
    return TickState(
        id = this.id,
        tick = this.tick,
        bg = bg
    )
}

fun List<TickStateEntity>.toModel(): List<TickState> {
    return List(this.size, { index -> this[index].toModel() })
}

fun List<TickState>.toEntity(): List<TickStateEntity> {
    return List(this.size, { index -> this[index].toEntity() })
}