package de.dh.raaps.core.repository.db.mappers

import de.dh.raaps.common.model.InsulinConcentration
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.core.repository.db.entities.CurrentTherapySettingsEntity
import de.dh.raaps.core.repository.db.entities.DBBgBlock
import de.dh.raaps.core.repository.db.entities.DBBlock
import de.dh.raaps.core.repository.db.entities.InsulinProfileEntity

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