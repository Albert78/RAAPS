package de.dh.raaps.core.repository.db

import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.common.model.data.TargetBlock
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.db.entities.CurrentTherapySettingsEntity
import de.dh.raaps.core.repository.db.entities.DBBlock
import de.dh.raaps.core.repository.db.entities.DBTargetBlock
import de.dh.raaps.core.repository.db.entities.DataProviderEntity
import de.dh.raaps.core.repository.db.entities.GlucoseReadingEntity
import de.dh.raaps.core.repository.db.entities.InsulinEntity
import de.dh.raaps.core.repository.db.entities.InsulinTypeEntity
import de.dh.raaps.core.repository.db.entities.MealEntity
import de.dh.raaps.core.repository.db.entities.MealTypeEntity
import de.dh.raaps.core.repository.db.entities.ProfileEntity
import de.dh.raaps.core.repository.db.entities.SensorTypeEntity
import de.dh.raaps.core.repository.db.entities.TherapyDataEntity

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
    if (componentsStr.isBlank()) return emptyList()
    return componentsStr.split(");(").map { componentStr ->
        val cleanStr = componentStr.removePrefix("(").removeSuffix(")")
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

fun InsulinApplication.toEntity() = InsulinEntity(
    id = this.id,
    timestamp = this.timestamp,
    amount = this.amount,
    scheduledAmount = this.scheduledAmount,
    insulin_type_id = this.insulinType.id,
    origin = this.origin,
    reason = this.reason
)

fun InsulinEntity.toModel(type: InsulinType) = InsulinApplication(
    id = this.id,
    timestamp = this.timestamp,
    amount = this.amount,
    scheduledAmount = this.scheduledAmount,
    insulinType = type,
    origin = this.origin,
    reason = this.reason
)

// Therapy Converters
fun Block.toDb() = DBBlock(
    duration = this.duration.value,
    amount = this.amount
)

fun DBBlock.toModel() = Block(
    duration = Minutes(this.duration),
    amount = this.amount
)

fun TargetBlock.toDb() = DBTargetBlock(
    duration = this.duration.value,
    lowTarget = this.lowTarget.mgdl,
    highTarget = this.highTarget.mgdl
)

fun DBTargetBlock.toModel() = TargetBlock(
    duration = Minutes(this.duration),
    lowTarget = BgValue.fromMgDl(this.lowTarget),
    highTarget = BgValue.fromMgDl(this.highTarget)
)

fun TherapyData.toEntity() = TherapyDataEntity(
    id = this.id,
    basal_blocks = this.basalBlocks.map { it.toDb() },
    isf_blocks = this.isfBlocks.map { it.toDb() },
    ic_blocks = this.icBlocks.map { it.toDb() },
    target_blocks = this.targetBlocks.map { it.toDb() }
)

fun TherapyDataEntity.toModel() = TherapyData(
    id = this.id,
    basalBlocks = this.basal_blocks.map { it.toModel() },
    isfBlocks = this.isf_blocks.map { it.toModel() },
    icBlocks = this.ic_blocks.map { it.toModel() },
    targetBlocks = this.target_blocks.map { it.toModel() }
)

fun Profile.toEntity() = ProfileEntity(
    id = this.id,
    name = this.name,
    therapy_data_id = this.therapyData.id
)

fun ProfileEntity.toModel(therapyData: TherapyData) = Profile(
    id = this.id,
    name = this.name,
    therapyData = therapyData
)

fun CurrentTherapySettings.toEntity() = CurrentTherapySettingsEntity(
    id = this.id,
    profile_id = this.profile.id,
    insulin_type_id = this.insulinType.id,
    aps_mode = this.apsMode
)

fun CurrentTherapySettingsEntity.toModel(profile: Profile, insulinType: InsulinType) = CurrentTherapySettings(
    id = this.id,
    profile = profile,
    insulinType = insulinType,
    apsMode = this.aps_mode
)