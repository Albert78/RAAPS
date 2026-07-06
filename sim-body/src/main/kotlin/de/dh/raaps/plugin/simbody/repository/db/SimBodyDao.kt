package de.dh.raaps.plugin.simbody.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SimBodyDao {
    // Impact History
    @Query("SELECT * FROM impact_history ORDER BY timestampMs DESC")
    fun observeAllImpacts(): Flow<List<ImpactHistoryEntity>>

    @Insert
    suspend fun insertImpact(impact: ImpactHistoryEntity): Long

    @Query("DELETE FROM impact_history WHERE timestampMs < :thresholdMs")
    suspend fun deleteOldImpacts(thresholdMs: Long)

    // Simulation State
    @Query("SELECT * FROM simulation_state WHERE id = 0")
    suspend fun getSimulationState(): SimulationStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSimulationState(state: SimulationStateEntity)

    // Simulation Events
    @Query("SELECT * FROM sim_events WHERE timestampMs >= :sinceMs ORDER BY timestampMs ASC")
    suspend fun getEventsSince(sinceMs: Long): List<SimEventEntity>

    @Insert
    suspend fun insertEvent(event: SimEventEntity): Long

    @Query("DELETE FROM sim_events WHERE timestampMs < :thresholdMs")
    suspend fun deleteOldEvents(thresholdMs: Long)

    // Body Profiles
    @Query("SELECT * FROM body_profiles")
    fun observeAllBodyProfiles(): Flow<List<BodyProfileEntity>>

    @Query("SELECT * FROM body_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveBodyProfile(): BodyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyProfile(profile: BodyProfileEntity): Long

    @Update
    suspend fun updateBodyProfile(profile: BodyProfileEntity)

    // Simulation Config
    @Query("SELECT * FROM simulation_config WHERE id = 0")
    suspend fun getSimulationConfig(): SimulationConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSimulationConfig(config: SimulationConfigEntity)
}
