package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.Minutes

/**
 * Entity for a therapy profile.
 */
@Entity(
    tableName = "profiles"
)
data class InsulinProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = ID_UNDEFINED,
    val name: String,
    val basal_blocks: List<DBBlock>,
    val isf_blocks: List<DBBlock>,
    val ic_blocks: List<DBBlock>,
    val insulin_type_id: String,
    val dia: Minutes,
    val peak: Minutes
)

/**
 * Entity for the current active therapy settings.
 */
@Entity(
    tableName = "current_therapy_settings",
    foreignKeys = [
        ForeignKey(
            entity = InsulinProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InsulinTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["insulin_type_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("profile_id"), Index("insulin_type_id")]
)
data class CurrentTherapySettingsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = ID_UNDEFINED,
    val profile_id: Long,
    val insulin_type_id: String,
    val adjustment_percentage: Int,
    val default_bg_blocks: List<DBBgBlock>
)