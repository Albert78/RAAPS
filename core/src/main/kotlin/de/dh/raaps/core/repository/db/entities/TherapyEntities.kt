package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.ID_UNDEFINED

/**
 * Contains the actual therapy data which can be used to calculate our APS.
 * It contains factors for basal, ISF, IC and the target values.
 * This therapy data can be used as current active data and for profiles.
 */
@Entity(
    tableName = "therapy_data"
)
data class TherapyDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = ID_UNDEFINED,
    val basal_blocks: List<DBBlock>,
    val isf_blocks: List<DBBlock>,
    val ic_blocks: List<DBBlock>,
    val target_blocks: List<DBTargetBlock>
)

/**
 * Entity for a therapy profile.
 */
@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = TherapyDataEntity::class,
            parentColumns = ["id"],
            childColumns = ["therapy_data_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("therapy_data_id")]
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = ID_UNDEFINED,
    val name: String,
    val therapy_data_id: Long
)

/**
 * Entity for the current active therapy settings.
 */
@Entity(
    tableName = "current_therapy_settings",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
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
    val aps_mode: ApsMode
)
