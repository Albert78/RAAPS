package de.dh.raaps.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.data.db.entities.DataProviderEntity
import de.dh.raaps.data.db.entities.GlucoseReadingEntity
import de.dh.raaps.data.db.entities.SensorTypeEntity
import de.dh.raaps.data.db.entities.TherapyDataEntity
import de.dh.raaps.data.db.entities.TickStateEntity
import java.util.concurrent.Executors

@Dao
interface ProviderDao {
    @Query("SELECT * FROM sensor_type ORDER BY name ASC")
    suspend fun getAllSensorTypes(): List<SensorTypeEntity>

    @Query("SELECT * FROM sensor_type where name = :name")
    suspend fun getSensorTypeByName(name: String): SensorTypeEntity?

    @Insert
    suspend fun insertSensorType(value: SensorTypeEntity): Long

    @Query("SELECT * FROM data_provider ORDER BY name ASC")
    suspend fun getAllDataProviders(): List<DataProviderEntity>

    @Query("SELECT * FROM data_provider where name = :name")
    suspend fun getDataProviderByName(name: String): DataProviderEntity?

    @Insert
    suspend fun insertDataProvider(value: DataProviderEntity): Long

    @Query("SELECT * FROM glucose_reading where timestamp_ms > :timestampMs ORDER BY timestamp_ms ASC")
    suspend fun getReadingsFromTime(timestampMs: Long): List<GlucoseReadingEntity>

    @Insert
    suspend fun insertGlucoseReading(reading: GlucoseReadingEntity): Long
}

@Dao
interface TherapyDao {

}

@Dao
interface StateDao {
    /**
     * Inserts the given tick state into the DB, if it not yet exists. Else, the tick state in the
     * DB will be updated. Returns the new or existing database ID of the entity.
     */
    @Upsert
    suspend fun insertOrUpdateTickState(tickState: TickStateEntity): Long

    @Query("SELECT * FROM tick_state WHERE tick >= :fromTick AND tick <= :toTick ORDER BY tick")
    suspend fun getTickStates(fromTick: Tick, toTick: Tick): List<TickStateEntity>
}

@Database(entities = [
    // Providers
    SensorTypeEntity::class,
    DataProviderEntity::class,
    GlucoseReadingEntity::class,

    // Therapy
    TherapyDataEntity::class,

    // States
    TickStateEntity::class
], version = 1)
@TypeConverters(
    DbTypeConverters::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun therapyDao(): TherapyDao
    abstract fun stateDao(): StateDao

    companion object {
        const val CURRENT_DATABASE_VERSION = "1.0"
        private const val DATABASE_NAME = "ApsDatabase.db"
        private const val LOG_DB_STATEMENTS = false

        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val sLock = Any()

        fun getInstance(context: Context): AppDatabase {
            synchronized(sLock) {
                if (INSTANCE == null) {
                    val dbBuilder = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DATABASE_NAME
                    )
                    // .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_1_4)

                    if (LOG_DB_STATEMENTS) {
                        dbBuilder.setQueryCallback({ sqlQuery, bindArgs ->
                            println("SQL Query: $sqlQuery SQL Args: $bindArgs")
                        }, Executors.newSingleThreadExecutor())
                    }
                    INSTANCE = dbBuilder.build()
                }
                return INSTANCE!!
            }
        }
    }
}