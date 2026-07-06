package de.dh.raaps.plugin.simbody.repository.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [
    ImpactHistoryEntity::class,
    SimulationStateEntity::class,
    SimEventEntity::class,
    BodyProfileEntity::class,
    SimulationConfigEntity::class
], version = 1)
abstract class SimBodyDatabase : RoomDatabase() {
    abstract fun impactDao(): SimBodyDao

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
                ).build().also { INSTANCE = it }
            }
        }
    }
}
