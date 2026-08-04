package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.ID_UNDEFINED

/**
 * Entity for the current system settings.
 */
@Entity(
    tableName = "current_settings"
)
data class CurrentSettingsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = ID_UNDEFINED,
    val aps_mode: ApsMode
)