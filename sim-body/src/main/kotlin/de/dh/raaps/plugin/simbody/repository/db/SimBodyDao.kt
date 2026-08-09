package de.dh.raaps.plugin.simbody.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SimBodyDao {
    // Simulation History (Combined BG and Impacts)
    @Query("SELECT * FROM sim_history ORDER BY timestampMs DESC")
    fun observeAllHistory(): Flow<List<SimHistoryEntity>>

    @Query("SELECT * FROM sim_history WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    suspend fun getHistorySince(sinceMs: Long): List<SimHistoryEntity>

    @Query("SELECT * FROM sim_history ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLatestHistoryEntry(): SimHistoryEntity?

    @Query("SELECT * FROM sim_history WHERE timestampMs <= :sinceMs ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getHistoryNear(sinceMs: Long): SimHistoryEntity?

    @Query("SELECT * FROM sim_history ORDER BY timestampMs ASC LIMIT 1")
    suspend fun getEarliestHistoryEntry(): SimHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: SimHistoryEntity): Long

    @Query("DELETE FROM sim_history WHERE timestampMs < :thresholdMs")
    suspend fun deleteOldHistory(thresholdMs: Long)

    // Simulation State
    @Query("SELECT * FROM simulation_state WHERE id = 0")
    suspend fun getSimulationState(): SimulationStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSimulationState(state: SimulationStateEntity)

    // Simulation Events
    @Query("SELECT * FROM sim_events WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
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
}
