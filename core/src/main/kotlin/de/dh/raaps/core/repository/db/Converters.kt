package de.dh.raaps.core.repository.db

import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinConcentration
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.core.aps.DeferredBolus
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.CurrentSettings
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.db.entities.CurrentSettingsEntity
import de.dh.raaps.core.repository.db.entities.CurrentTherapySettingsEntity
import de.dh.raaps.core.repository.db.entities.DBBgBlock
import de.dh.raaps.core.repository.db.entities.DBBlock
import de.dh.raaps.core.repository.db.entities.DataProviderEntity
import de.dh.raaps.core.repository.db.entities.DeferredBolusEntity
import de.dh.raaps.core.repository.db.entities.GlucoseReadingEntity
import de.dh.raaps.core.repository.db.entities.InsulinEntity
import de.dh.raaps.core.repository.db.entities.InsulinProfileEntity
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
    dia = this.dia,
    default_concentration = this.defaultConcentration.factor
)

fun InsulinTypeEntity.toModel() = InsulinType(
    id = this.id,
    name = this.name,
    peak = this.peak,
    dia = this.dia,
    defaultConcentration = InsulinConcentration(this.default_concentration)
)

fun InsulinApplication.toEntity() = InsulinEntity(
    id = this.id,
    timestamp = this.timestamp,
    amount = this.amount,
    insulin_type_id = this.insulinType.id,
    category = this.category,
    origin = this.origin
)

fun InsulinEntity.toModel(type: InsulinType) = InsulinApplication(
    id = this.id,
    timestamp = this.timestamp,
    amount = this.amount,
    insulinType = type,
    category = this.category,
    origin = this.origin
)

// Deferred Bolus Converters
fun DeferredBolus.toEntity() = DeferredBolusEntity(
    id = this.id,
    timestamp = this.timestamp,
    amount = this.amount.iu
)

fun DeferredBolusEntity.toModel() = DeferredBolus(
    id = this.id,
    amount = InsulinAmount(this.amount),
    timestamp = this.timestamp
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

fun BgBlock.toDb() = DBBgBlock(
    duration = this.duration.value,
    target = this.target.mgdl,
    lowThreshold = this.lowThreshold.mgdl
)

fun DBBgBlock.toModel() = BgBlock(
    duration = Minutes(this.duration),
    target = BgValue.fromMgDl(this.target),
    lowThreshold = BgValue.fromMgDl(this.lowThreshold)
)

fun InsulinProfile.toEntity() = InsulinProfileEntity(
    id = this.id,
    name = this.name,
    basal_blocks = this.basalBlocks.map { it.toDb() },
    isf_blocks = this.isfBlocks.map { it.toDb() },
    cr_blocks = this.crBlocks.map { it.toDb() },
    insulin_type_id = this.insulinType.id,
    insulin_concentration = this.insulinConcentration.factor,
    dia = this.dia,
    peak = this.peak
)

fun InsulinProfileEntity.toModel(insulinType: InsulinType) = InsulinProfile(
    id = this.id,
    name = this.name,
    basalBlocks = this.basal_blocks.map { it.toModel() },
    isfBlocks = this.isf_blocks.map { it.toModel() },
    crBlocks = this.cr_blocks.map { it.toModel() },
    insulinType = insulinType,
    insulinConcentration = InsulinConcentration(this.insulin_concentration),
    dia = this.dia,
    peak = this.peak
)

fun CurrentTherapySettings.toEntity() = CurrentTherapySettingsEntity(
    id = this.id,
    insulin_profile_id = this.insulinProfile.id,
    default_bg_blocks = this.defaultBgBlocks.map { it.toDb() },
    insulin_adjustment_percentage = this.insulinAdjustmentPercentage,
    target_bg_override = this.targetBgOverride?.mgdl,
    low_threshold_override = this.lowThresholdOverride?.mgdl,
    adjustment_hint = this.adjustmentHint,
)

fun CurrentTherapySettingsEntity.toModel(profile: InsulinProfile) = CurrentTherapySettings(
    id = this.id,
    insulinProfile = profile,
    defaultBgBlocks = this.default_bg_blocks.map { it.toModel() },
    insulinAdjustmentPercentage = this.insulin_adjustment_percentage,
    targetBgOverride = this.target_bg_override?.let { BgValue.fromMgDl(it) },
    lowThresholdOverride = this.low_threshold_override?.let { BgValue.fromMgDl(it) },
    adjustmentHint = this.adjustment_hint,
)

// Settings Converters
fun CurrentSettings.toEntity() = CurrentSettingsEntity(
    id = this.id,
    aps_mode = this.apsMode
)

fun CurrentSettingsEntity.toModel() = CurrentSettings(
    id = this.id,
    apsMode = this.aps_mode
)
