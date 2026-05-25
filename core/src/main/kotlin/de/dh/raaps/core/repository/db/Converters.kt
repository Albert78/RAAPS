package de.dh.raaps.core.repository.db

import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.db.entities.DataProviderEntity
import de.dh.raaps.core.repository.db.entities.GlucoseReadingEntity
import de.dh.raaps.core.repository.db.entities.InsulinApplicationEntity
import de.dh.raaps.core.repository.db.entities.InsulinTypeEntity
import de.dh.raaps.core.repository.db.entities.MealEntity
import de.dh.raaps.core.repository.db.entities.MealTypeEntity
import de.dh.raaps.core.repository.db.entities.SensorTypeEntity

// BgReading Converters
fun BgReading.toEntity(dataProviderId: Long, sourceSensorId: Long) = GlucoseReadingEntity(
    id = this.id,
    value_mgdl = this.value.mgdl,
    sample_kind = this.sampleKind,
    timestamp_ms = this.timestamp.ms,
    fk_data_provider = dataProviderId,
    fk_source_sensor = sourceSensorId
)

fun GlucoseReadingEntity.toModel() = BgReading(
    id = this.id,
    value = BgValue.fromMgDl(this.value_mgdl),
    sampleKind = this.sample_kind,
    timestamp = Timestamp(timestamp_ms)
)

// SensorType Converters
fun SensorType.toEntity() = SensorTypeEntity(
    id = this.id,
    name = this.name
)

fun SensorTypeEntity.toModel() = SensorType(
    id = this.id,
    name = this.name
)

// DataProvider Converters
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

// Meal Converters
fun carbCurveComponentListToString(components: List<CarbCurveComponentData>): String {
    return components.joinToString(";", prefix = "[", postfix = "]") {
        "(weight=${it.weight};peak=${it.peakMinutes.value})"
    }
}

fun stringToCarbCurveComponentList(value: String): List<CarbCurveComponentData> {
    val componentsStr = value.removeSurrounding("[", "]")
    return componentsStr.split(");(").map { componentStr ->
        val cleanStr = componentStr.removeSurrounding("(", ")")
        val props = cleanStr.split(";")
        val weight = props[0].substringAfter("weight=").toInt()
        val peak = props[1].substringAfter("peak=").toShort()
        CarbCurveComponentData(weight, Minutes(peak))
    }
}

fun MealType.toEntity() = MealTypeEntity(
    id = this.id,
    name = this.name,
    curve_components = carbCurveComponentListToString(this.components),
    cat = this.cat
)

fun MealTypeEntity.toModel() = MealType(
    id = this.id,
    name = this.name,
    components = stringToCarbCurveComponentList(this.curve_components),
    cat = this.cat
)

fun MealEntry.toEntity() = MealEntity(
    id = this.id,
    meal_type_id = this.mealType.id,
    timestamp = this.timestamp,
    carbGrams = this.carbGrams
)

fun MealEntity.toModel(type: MealType) = MealEntry(
    id = this.id,
    timestamp = this.timestamp,
    carbGrams = this.carbGrams,
    mealType = type
)

// Insulin Converters
fun InsulinType.toEntity() = InsulinTypeEntity(
    id = this.id,
    name = this.name,
    peak = this.peak,
    dia = this.dia
)

fun InsulinTypeEntity.toModel() = InsulinType(
    id = this.id,
    name = this.name,
    peak = this.peak,
    dia = this.dia
)

fun InsulinApplication.toEntity() = InsulinApplicationEntity(
    id = this.id,
    insulin_type_id = this.insulinType.id,
    timestamp = this.timestamp,
    insulinUnits = this.insulinUnits
)

fun InsulinApplicationEntity.toModel(type: InsulinType) = InsulinApplication(
    id = this.id,
    timestamp = this.timestamp,
    insulinUnits = this.insulinUnits,
    insulinType = type
)