package de.dh.raaps.core.repository.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp

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
)

@Entity(
    tableName = "insulin_type"
)
data class InsulinTypeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val peak: Minutes,
    val dia: Minutes
)

@Entity(
    tableName = "insulin_application",
    foreignKeys = [
        ForeignKey(
            entity = InsulinTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["insulin_type_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class InsulinApplicationEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = ID_UNDEFINED,
    val insulin_type_id: String,
    val timestamp: Timestamp,
    val insulinUnits: Double
)