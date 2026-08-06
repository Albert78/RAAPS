package de.dh.raaps.plugin.simbody.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PumpDao {
    // Pump State
    @Query("SELECT * FROM pump_state WHERE id = 0")
    suspend fun getPumpState(): PumpStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePumpState(state: PumpStateEntity)

    // Pump History
    @Query("SELECT * FROM pump_history ORDER BY timestampMs DESC")
    suspend fun getAllHistory(): List<PumpHistoryEntity>

    @Query("SELECT * FROM pump_history WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    suspend fun getHistorySince(sinceMs: Long): List<PumpHistoryEntity>

    @Insert
    suspend fun insertHistoryEntry(entry: PumpHistoryEntity): Long

    @Query("DELETE FROM pump_history WHERE timestampMs < :thresholdMs")
    suspend fun deleteOldHistory(thresholdMs: Long)
}
