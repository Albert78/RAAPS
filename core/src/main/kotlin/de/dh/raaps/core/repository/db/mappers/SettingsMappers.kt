package de.dh.raaps.core.repository.db.mappers

import de.dh.raaps.common.model.data.CurrentSettings
import de.dh.raaps.core.repository.db.entities.CurrentSettingsEntity

// Settings Converters
fun CurrentSettings.toEntity() = CurrentSettingsEntity(
    id = this.id,
    aps_mode = this.apsMode
)

fun CurrentSettingsEntity.toModel() = CurrentSettings(
    id = this.id,
    apsMode = this.aps_mode
)