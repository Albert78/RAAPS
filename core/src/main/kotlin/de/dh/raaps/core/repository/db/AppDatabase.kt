package de.dh.raaps.core.repository.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.core.repository.db.entities.BasalHistoryEntity
import de.dh.raaps.core.repository.db.entities.BolusEntity
import de.dh.raaps.core.repository.db.entities.CurrentTherapyDataEntity
import de.dh.raaps.core.repository.db.entities.DataProviderEntity
import de.dh.raaps.core.repository.db.entities.GlucoseReadingEntity
import de.dh.raaps.core.repository.db.entities.InsulinTypeEntity
import de.dh.raaps.core.repository.db.entities.MealEntity
import de.dh.raaps.core.repository.db.entities.MealTypeEntity
import de.dh.raaps.core.repository.db.entities.ProfileEntity
import de.dh.raaps.core.repository.db.entities.SensorTypeEntity
import de.dh.raaps.core.repository.db.entities.TherapyDataEntity
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

    @Insert
    suspend fun insertGlucoseReading(reading: GlucoseReadingEntity): Long
}

@Dao
interface TherapyDao {
    // Therapy Data
    @Query("SELECT * FROM therapy_data WHERE id = :id")
    suspend fun getTherapyDataById(id: Long): TherapyDataEntity?

    @Insert
    suspend fun insertTherapyData(data: TherapyDataEntity): Long

    @Update
    suspend fun updateTherapyData(data: TherapyDataEntity)

    @Query("DELETE FROM therapy_data WHERE id = :id")
    suspend fun deleteTherapyData(id: Long)

    // Profiles
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    suspend fun getAllProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ProfileEntity?

    @Insert
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    // Current Therapy Data
    @Query("SELECT * FROM current_therapy_data LIMIT 1")
    suspend fun getCurrentTherapyData(): CurrentTherapyDataEntity?

    @Query("SELECT * FROM current_therapy_data LIMIT 1")
    fun observeCurrentTherapyData(): Flow<CurrentTherapyDataEntity?>

    @Insert
    suspend fun insertCurrentTherapyData(data: CurrentTherapyDataEntity): Long

    @Update
    suspend fun updateCurrentTherapyData(data: CurrentTherapyDataEntity)
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

    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Update
    suspend fun updateMeal(meal: MealEntity)

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

    // Bolus
    @Query("SELECT * FROM bolus ORDER BY timestamp ASC")
    suspend fun getAllBoluses(): List<BolusEntity>

    @Query("SELECT * FROM bolus ORDER BY timestamp ASC")
    fun observeAllBoluses(): Flow<List<BolusEntity>>

    @Query("SELECT * FROM bolus WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getBolusesSince(since: Long): List<BolusEntity>

    @Insert
    suspend fun insertBolus(bolus: BolusEntity): Long

    @Update
    suspend fun updateBolus(bolus: BolusEntity)

    @Query("DELETE FROM bolus where id = :bolusId")
    suspend fun deleteBolus(bolusId: Long)

    @Query("DELETE FROM bolus WHERE origin = :origin")
    suspend fun deleteBolusesByOrigin(origin: InsulinOrigin)

    @Query("SELECT * FROM basal_history WHERE startTick = :tick LIMIT 1")
    suspend fun getBasalHistoryEntry(tick: Int): BasalHistoryEntity?

    @Query("SELECT * FROM basal_history ORDER BY startTick DESC LIMIT 1")
    suspend fun getLastBasalHistoryEntry(): BasalHistoryEntity?

    @Query("SELECT * FROM basal_history WHERE startTick >= :since ORDER BY startTick ASC")
    suspend fun getBasalHistoryEntriesSince(since: Int): List<BasalHistoryEntity>

    @Query("SELECT * FROM basal_history ORDER BY startTick ASC")
    suspend fun getAllBasalHistoryEntries(): List<BasalHistoryEntity>

    @Query("SELECT * FROM basal_history ORDER BY startTick ASC")
    fun observeAllBasalHistoryEntries(): Flow<List<BasalHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBasalHistoryEntry(basal: BasalHistoryEntity)
}

@Database(entities = [
    // Providers
    SensorTypeEntity::class,
    DataProviderEntity::class,
    GlucoseReadingEntity::class,

    // Therapy
    TherapyDataEntity::class,
    ProfileEntity::class,
    CurrentTherapyDataEntity::class,

    // Metabolic events
    MealTypeEntity::class,
    MealEntity::class,
    InsulinTypeEntity::class,
    BolusEntity::class,
    BasalHistoryEntity::class
], version = 1)
@TypeConverters(
    DbTypeConverters::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun therapyDao(): TherapyDao
    abstract fun metabolicEventsDao(): MetabolicEventsDao

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