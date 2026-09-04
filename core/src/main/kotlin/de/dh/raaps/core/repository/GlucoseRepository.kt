package de.dh.raaps.core.repository

import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.ApsAlgorithmImpl
import de.dh.raaps.core.aps.RecentBgReadingsHistory
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.ProviderDao
import de.dh.raaps.core.repository.db.entities.DataProviderEntity
import de.dh.raaps.core.repository.db.entities.SensorTypeEntity
import de.dh.raaps.core.repository.db.mappers.toEntity
import de.dh.raaps.core.repository.db.mappers.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Repository for glucose related data, including sensor types and data providers.
 */
class GlucoseRepository(appDatabase: AppDatabase) {
    private val providerDao: ProviderDao = appDatabase.providerDao()

    private val _currentBg = MutableStateFlow<BgReading?>(null)
    val currentBg: StateFlow<BgReading?> = _currentBg.asStateFlow()

    var lastBg: BgReading? = null
        private set

    val history = RecentBgReadingsHistory(ApsAlgorithmImpl.BG_HISTORY_TIME)

    suspend fun initialize() {
        val readings = loadBgReadings(from = Timestamp.now().minus(ApsAlgorithmImpl.BG_HISTORY_TIME))
        history.setAll(readings)

        val readingsHistory = history.toList()
        _currentBg.value = readingsHistory.lastOrNull()
        lastBg = if (readingsHistory.size >= 2) readingsHistory[readingsHistory.size - 2] else null
    }

    suspend fun addReading(
        reading: BgReading,
        dataProvider: DataProvider,
        sourceSensor: SensorType
    ) {
        insertDataProviderGlucoseReading(reading, dataProvider, sourceSensor)

        history.add(reading)

        if (_currentBg.value == null || reading.timestamp >= _currentBg.value!!.timestamp) {
            lastBg = _currentBg.value
            _currentBg.value = reading
        }
    }

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