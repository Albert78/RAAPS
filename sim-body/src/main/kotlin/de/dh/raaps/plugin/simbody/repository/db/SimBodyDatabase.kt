package de.dh.raaps.plugin.simbody.repository.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [
    SimHistoryEntity::class,
    SimulationStateEntity::class,
    SimEventEntity::class,
    BodyProfileEntity::class,
    PumpStateEntity::class,
    PumpHistoryEntity::class
], version = 1)
abstract class SimBodyDatabase : RoomDatabase() {
    abstract fun impactDao(): SimBodyDao
    abstract fun pumpDao(): PumpDao

    companion object {
        private const val DATABASE_NAME = "SimBodyDatabase.db"

        @Volatile
        private var INSTANCE: SimBodyDatabase? = null

        fun getInstance(context: Context): SimBodyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SimBodyDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
