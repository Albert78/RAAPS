package de.dh.raaps.plugin.simbody.repository.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinOrigin

@Entity(tableName = "sim_history")
data class SimHistoryEntity(
    @PrimaryKey
    val timestampMs: Long,
    val bgMgDl: Double,
    val carbImpact: Double,
    val insulinImpact: Double,
    val endogenousImpact: Double,
    val exerciseImpact: Double,
    val stressImpact: Double
)

@Entity(tableName = "simulation_state")
data class SimulationStateEntity(
    @PrimaryKey val id: Int = 0, // Only one state entry
    val lastSimulationTimestampMs: Long,
    val exerciseIntensity: Double,
    val stressLevel: Double,
    val illnessFactor: Double,
    val isSensorEnabled: Boolean = true,
    val sensorNoiseFactor: Double = 0.0,
    val sensorDrift: Double = 0.0
)

@Entity(tableName = "sim_events")
data class SimEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "MEAL" or "BOLUS"
    val timestampMs: Long,
    val amount: InsulinAmount,
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

@Entity(tableName = "pump_state")
data class PumpStateEntity(
    @PrimaryKey val id: Int = 0,
    val batteryLevel: Double,
    val reservoirLevel: Double,
    val isOccluded: Boolean,
    val isPrimed: Boolean,
    val hasHardwareError: Boolean,
    val isBroken: Boolean,
    val lastBasalDeliveryTimestampMs: Long,
    val tempBasalPercent: Int?,
    val tempBasalExpiryMs: Long? = null
)

enum class PumpDeliveryType {
    Bolus, Basal, Tbr
}

@Entity(tableName = "pump_history")
data class PumpHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMs: Long,
    val amount: InsulinAmount,
    val deliveryType: PumpDeliveryType
)