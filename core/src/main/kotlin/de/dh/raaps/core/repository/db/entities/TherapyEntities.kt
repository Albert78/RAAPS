package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
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