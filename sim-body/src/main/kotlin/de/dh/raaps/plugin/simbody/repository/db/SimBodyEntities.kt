package de.dh.raaps.plugin.simbody.repository.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.InsulinOrigin

@Entity(tableName = "impact_history")
data class ImpactHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carbImpact: Double,
    val insulinImpact: Double,
    val endogenousImpact: Double,
    val exerciseImpact: Double,
    val stressImpact: Double,
    val timestampMs: Long
)

@Entity(tableName = "simulation_state")
data class SimulationStateEntity(
    @PrimaryKey val id: Int = 0, // Only one state entry
    val currentBgMgDl: Double,
    val lastTickTimestampMs: Long,
    val exerciseIntensity: Double,
    val stressLevel: Double,
    val illnessFactor: Double
)

@Entity(tableName = "sim_events")
data class SimEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "MEAL" or "BOLUS"
    val timestampMs: Long,
    val amount: Double,
    val detailId: String? = null, // MealType ID or InsulinType ID
    val insulinOrigin: InsulinOrigin? = null
)

@Entity(tableName = "body_profiles")
data class BodyProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isfBlocks: String, // JSON
    val crBlocks: String, // JSON
    val liverGlucoseOutputBlocks: String, // JSON
    val isActive: Boolean = false
)

@Entity(tableName = "simulation_config")
data class SimulationConfigEntity(
    @PrimaryKey val id: Int = 0,
    val sensorNoiseLevel: Double,
    val sensorDrift: Double
)