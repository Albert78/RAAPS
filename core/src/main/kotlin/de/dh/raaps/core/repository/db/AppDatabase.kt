package de.dh.raaps.core.repository.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.core.repository.db.entities.CoreInsightEntity
import de.dh.raaps.core.repository.db.entities.CurrentSettingsEntity
import de.dh.raaps.core.repository.db.entities.CurrentTherapySettingsEntity
import de.dh.raaps.core.repository.db.entities.DataProviderEntity
import de.dh.raaps.core.repository.db.entities.DeferredBolusEntity
import de.dh.raaps.core.repository.db.entities.GlucoseReadingEntity
import de.dh.raaps.core.repository.db.entities.InsulinEntity
import de.dh.raaps.core.repository.db.entities.InsulinProfileEntity
import de.dh.raaps.core.repository.db.entities.InsulinTypeEntity
import de.dh.raaps.core.repository.db.entities.MealEntity
import de.dh.raaps.core.repository.db.entities.MealTypeEntity
import de.dh.raaps.core.repository.db.entities.SensorTypeEntity
import de.dh.raaps.core.repository.db.entities.TickMetricEntity
import de.dh.raaps.core.repository.db.entities.WakeupMetricEntity
import kotlinx.coroutines.flow.Flow
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

    @Query("SELECT * FROM glucose_reading ORDER BY timestamp_ms ASC")
    fun observeAllReadings(): Flow<List<GlucoseReadingEntity>>

    @Insert
    suspend fun insertGlucoseReading(reading: GlucoseReadingEntity): Long
}

@Dao
interface TherapyDao {
    // Profiles
    @Query("SELECT * FROM insulin_profiles ORDER BY name ASC")
    suspend fun getAllInsulinProfiles(): List<InsulinProfileEntity>

    @Query("SELECT * FROM insulin_profiles ORDER BY name ASC")
    fun observeAllInsulinProfiles(): Flow<List<InsulinProfileEntity>>

    @Query("SELECT * FROM insulin_profiles WHERE id = :id")
    suspend fun getInsulinProfileById(id: Long): InsulinProfileEntity?

    @Insert
    suspend fun insertInsulinProfile(profile: InsulinProfileEntity): Long

    @Update
    suspend fun updateInsulinProfile(profile: InsulinProfileEntity)

    @Query("DELETE FROM insulin_profiles WHERE id = :id")
    suspend fun deleteInsulinProfile(id: Long)

    // Current Therapy Settings
    @Query("SELECT * FROM current_therapy_settings LIMIT 1")
    suspend fun getCurrentTherapySettings(): CurrentTherapySettingsEntity?

    @Query("SELECT * FROM current_therapy_settings LIMIT 1")
    fun observeCurrentTherapySettings(): Flow<CurrentTherapySettingsEntity?>

    @Insert
    suspend fun insertCurrentTherapySettings(data: CurrentTherapySettingsEntity): Long

    @Update
    suspend fun updateCurrentTherapySettings(data: CurrentTherapySettingsEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM current_settings LIMIT 1")
    suspend fun getCurrentSettings(): CurrentSettingsEntity?

    @Query("SELECT * FROM current_settings LIMIT 1")
    fun observeCurrentSettings(): Flow<CurrentSettingsEntity?>

    @Insert
    suspend fun insertCurrentSettings(data: CurrentSettingsEntity): Long

    @Update
    suspend fun updateCurrentSettings(data: CurrentSettingsEntity)
}

@Dao
interface MetabolicEventsDao {
    // Meal Types
    @Query("SELECT * FROM meal_type")
    suspend fun getAllMealTypes(): List<MealTypeEntity>

    @Query("SELECT * FROM meal_type WHERE id = :id")
    suspend fun getMealTypeById(id: String): MealTypeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMealType(mealType: MealTypeEntity)

    @Update
    suspend fun updateMealType(mealType: MealTypeEntity)

    @Query("DELETE FROM meal_type where id = :mealTypeId")
    suspend fun deleteMealType(mealTypeId: String)

    // Meals
    @Query("SELECT * FROM meal ORDER BY timestamp ASC")
    suspend fun getAllMeals(): List<MealEntity>

    @Query("SELECT * FROM meal ORDER BY timestamp ASC")
    fun observeAllMeals(): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getMealsSince(since: Long): List<MealEntity>

    @Query("SELECT * FROM meal WHERE id = :id")
    suspend fun getMealById(id: Long): MealEntity?

    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Query("UPDATE meal SET insulinAdministered = 1 WHERE id IN (:mealIds)")
    suspend fun markMealsAsInsulinAdministered(mealIds: List<Long>)

    @Query("DELETE FROM meal where id = :mealId")
    suspend fun deleteMeal(mealId: Long)

    // Insulin Types
    @Query("SELECT * FROM insulin_type")
    suspend fun getAllInsulinTypes(): List<InsulinTypeEntity>

    @Query("SELECT * FROM insulin_type WHERE id = :id")
    suspend fun getInsulinTypeById(id: String): InsulinTypeEntity?

    @Query("SELECT * FROM insulin_type WHERE name = :name")
    suspend fun getInsulinTypeByName(name: String): InsulinTypeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInsulinType(insulinType: InsulinTypeEntity)

    @Update
    suspend fun updateInsulinType(insulinType: InsulinTypeEntity)

    @Query("DELETE FROM INSULIN_TYPE where id = :insulinTypeId")
    suspend fun deleteInsulinType(insulinTypeId: String)

    // Insulin
    @Query("SELECT * FROM insulin ORDER BY timestamp ASC")
    suspend fun getAllInsulinApplications(): List<InsulinEntity>

    @Query("SELECT * FROM insulin ORDER BY timestamp ASC")
    fun observeAllInsulinApplications(): Flow<List<InsulinEntity>>

    @Query("SELECT * FROM insulin WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getInsulinApplicationsSince(since: Long): List<InsulinEntity>

    @Insert
    suspend fun insertInsulinApplication(insulin: InsulinEntity): Long

    @Insert
    suspend fun insertInsulinApplications(insulin: List<InsulinEntity>)

    @Update
    suspend fun updateInsulinApplication(insulin: InsulinEntity)

    @Query("DELETE FROM insulin where id = :id")
    suspend fun deleteInsulinApplication(id: Long)

    @Query("DELETE FROM insulin WHERE origin = :origin AND timestamp >= :from AND timestamp <= :to")
    suspend fun deleteInsulinApplicationsInRange(from: Long, to: Long, origin: InsulinOrigin)

    @Transaction
    suspend fun replaceInsulinApplicationsInRange(from: Long, to: Long, origin: InsulinOrigin, newApplications: List<InsulinEntity>) {
        deleteInsulinApplicationsInRange(from, to, origin)
        insertInsulinApplications(newApplications)
    }

    @Query("DELETE FROM meal WHERE timestamp >= :from AND timestamp <= :to")
    suspend fun deleteMealsInRange(from: Long, to: Long)

    // Deferred Bolus
    @Query("SELECT * FROM deferred_bolus ORDER BY timestamp ASC")
    suspend fun getAllDeferredBoluses(): List<DeferredBolusEntity>

    @Insert
    suspend fun insertDeferredBolus(deferredBolus: DeferredBolusEntity): Long

    @Update
    suspend fun updateDeferredBolus(deferredBolus: DeferredBolusEntity)

    @Query("DELETE FROM deferred_bolus WHERE id = :id")
    suspend fun deleteDeferredBolus(id: Long)

    @Query("DELETE FROM deferred_bolus WHERE id IN (:ids)")
    suspend fun deleteDeferredBoluses(ids: List<Long>)
}

@Dao
interface SystemMetricsDao {
    @Insert
    suspend fun insert(insight: CoreInsightEntity): Long

    @Query("SELECT * FROM core_insights ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CoreInsightEntity>>

    @Query("SELECT * FROM core_insights ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatest(limit: Int): List<CoreInsightEntity>

    @Query("DELETE FROM core_insights WHERE timestamp < :timestamp")
    suspend fun pruneOlderThan(timestamp: Long)

    @Insert
    suspend fun insertWakeupMetric(metric: WakeupMetricEntity): Long

    @Insert
    suspend fun insertTickMetric(metric: TickMetricEntity): Long

    @Query("DELETE FROM wakeup_metrics WHERE scheduledTime < :timestamp")
    suspend fun pruneWakeupMetricsOlderThan(timestamp: Long)

    @Query("DELETE FROM tick_metrics WHERE startTime < :timestamp")
    suspend fun pruneTickMetricsOlderThan(timestamp: Long)
}

@Database(entities = [
    // Providers
    SensorTypeEntity::class,
    DataProviderEntity::class,
    GlucoseReadingEntity::class,

    // Therapy
    InsulinProfileEntity::class,
    CurrentTherapySettingsEntity::class,
    CurrentSettingsEntity::class,

    // Metabolic events
    MealTypeEntity::class,
    MealEntity::class,
    InsulinTypeEntity::class,
    InsulinEntity::class,
    DeferredBolusEntity::class,
    CoreInsightEntity::class,
    WakeupMetricEntity::class,
    TickMetricEntity::class
], version = 1)
@TypeConverters(
    DbTypeConverters::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun therapyDao(): TherapyDao
    abstract fun metabolicEventsDao(): MetabolicEventsDao
    abstract fun settingsDao(): SettingsDao
    abstract fun systemMetricsDao(): SystemMetricsDao

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
                        .fallbackToDestructiveMigration(true)

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