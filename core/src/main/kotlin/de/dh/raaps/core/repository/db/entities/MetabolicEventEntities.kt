package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.InsulinCategory
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp

import de.dh.raaps.common.model.InsulinStatus

@Entity(
    tableName = "meal_type"
)
data class MealTypeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val curve_components: String,
    val cat: Minutes
)

@Entity(
    tableName = "meal",
    foreignKeys = [
        ForeignKey(
            entity = MealTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["meal_type_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val meal_type_id: String,
    val timestamp: Timestamp,
    val carbGrams: Double,
    val description: String = "",
    val insulinAdministered: Boolean = false
)

@Entity(
    tableName = "insulin_type"
)
data class InsulinTypeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val peak: Minutes,
    val dia: Minutes,
    val default_concentration: Double = 1.0
)

@Entity(
    tableName = "insulin",
    foreignKeys = [
        ForeignKey(
            entity = InsulinTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["insulin_type_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("timestamp")]
)
data class InsulinEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val insulin_type_id: String,
    val timestamp: Timestamp,
    val amount: InsulinAmount,
    val origin: InsulinOrigin,
    val basal: Boolean = false,
    val correction: Boolean = false,
    val meal: Boolean = false,
    val status: InsulinStatus = InsulinStatus.Confirmed
)

@Entity(
    tableName = "deferred_bolus",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["meal_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("meal_id")]
)
data class DeferredBolusEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val amount: Double,
    val meal_id: Long? = null
)