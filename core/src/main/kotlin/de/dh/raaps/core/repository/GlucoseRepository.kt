package de.dh.raaps.core.repository

import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.ProviderDao
import de.dh.raaps.core.repository.db.entities.DataProviderEntity
import de.dh.raaps.core.repository.db.entities.SensorTypeEntity
import de.dh.raaps.core.repository.db.toEntity
import de.dh.raaps.core.repository.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for glucose related data, including sensor types and data providers.
 */
class GlucoseRepository(appDatabase: AppDatabase) {
    private val providerDao: ProviderDao = appDatabase.providerDao()

    fun observeBgReadings(): Flow<List<BgReading>> = providerDao.observeAllReadings()
        .map { entities -> entities.map { it.toModel() } }

    suspend fun getOrCreateSensorTypeByName(name: String): SensorType {
        var entity = providerDao.getSensorTypeByName(name)
        if (entity == null) {
            entity = SensorTypeEntity(name = name)
            val id = providerDao.insertSensorType(entity)
            if (id != -1L) {
                entity.id = id
            }
        }
        return entity.toModel()
    }

    suspend fun getOrCreateDataProviderByName(name: String, type: String): DataProvider {
        var entity = providerDao.getDataProviderByName(name)
        if (entity == null) {
            entity = DataProviderEntity(name = name, type = type)
            val id = providerDao.insertDataProvider(entity)
            if (id != -1L) {
                entity.id = id
            }
        }
        return entity.toModel()
    }

    /**
     * Insert the given glucose reading from a data provider to the database.
     */
    suspend fun insertDataProviderGlucoseReading(reading: BgReading, dataProvider: DataProvider, sourceSensor: SensorType) {
        val entity = reading.toEntity(dataProvider.id, sourceSensor.id)
        val id = providerDao.insertGlucoseReading(entity)
        if (id != -1L) {
            reading.id = id
        }
    }

    /**
     * Loads glucose readings from the database that were recorded after the given timestamp.
     */
    suspend fun loadBgReadings(from: Timestamp): List<BgReading> {
        return providerDao.getReadingsFromTime(from.ms)
            .map { it.toModel() }
    }
}