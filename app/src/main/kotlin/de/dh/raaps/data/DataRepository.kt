package de.dh.raaps.data

import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.ToDo
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.mock.mockSimpleTherapyData
import de.dh.raaps.data.db.AppDatabase
import de.dh.raaps.data.db.entities.DataProviderEntity
import de.dh.raaps.data.db.entities.SensorTypeEntity
import de.dh.raaps.data.db.toEntity
import de.dh.raaps.data.db.toModel

class DataRepository(val database: AppDatabase) {
    suspend fun getOrCreateSensorTypeByName(name: String): SensorType {
        val dao = database.providerDao()
        var entity = dao.getSensorTypeByName(name)
        if (entity == null) {
            entity = SensorTypeEntity(
                name = name
            )
            val id = dao.insertSensorType(entity)
            if (id != -1L) {
                entity.id = id
            }
        }
        return entity.toModel()
    }

    suspend fun getOrCreateDataProviderByName(name: String, type: String): DataProvider {
        val dao = database.providerDao()
        var entity = dao.getDataProviderByName(name)
        if (entity == null) {
            entity = DataProviderEntity(
                name = name,
                type = type
            )
            val id = dao.insertDataProvider(entity)
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
        val id = database.providerDao().insertGlucoseReading(entity)
        if (id != -1L) {
            reading.id = id
        }
    }

    /**
     * Loads glucose readings from the database that were recorded after the given timestamp.
     */
    suspend fun loadBgReadings(from: Timestamp): List<BgReading> {
        return database.providerDao()
            .getReadingsFromTime(from.ms)
            .map { it.toModel() }
    }

    // --- Meal Operations ---

    suspend fun getAllMealTypes(): List<MealType> {
        return database.metabolicEventsDao().getAllMealTypes().map { it.toModel() }
    }

    suspend fun insertMealType(mealType: MealType) {
        database.metabolicEventsDao().insertMealType(mealType.toEntity())
    }

    suspend fun deleteMealType(mealType: MealType) {
        database.metabolicEventsDao().deleteMealType(mealType.id)
    }

    suspend fun loadMeals(from: Timestamp? = null, to: Timestamp? = null): List<MealEntry> {
        val dao = database.metabolicEventsDao()
        val entities = dao.getMealsInRange(from?.ms ?: 0, to?.ms ?: Long.MAX_VALUE)
        val types = dao.getAllMealTypes().associateBy { it.id }
        return entities.mapNotNull { entity ->
            val typeEntity = types[entity.meal_type_id]
            typeEntity?.let { entity.toModel(it.toModel()) }
        }
    }

    suspend fun insertMeal(meal: MealEntry): Long {
        val entity = meal.toEntity()
        val id = database.metabolicEventsDao().insertMeal(entity)
        if (id != -1L) {
            meal.id = id
        }
        return id
    }

    suspend fun deleteMeal(meal: MealEntry) {
        database.metabolicEventsDao().deleteMeal(meal.id)
    }

    // --- Insulin Operations ---

    suspend fun getAllInsulinTypes(): List<InsulinType> {
        return database.metabolicEventsDao().getAllInsulinTypes().map { it.toModel() }
    }

    suspend fun getInsulinTypeById(id: String): InsulinType? {
        return database.metabolicEventsDao().getInsulinTypeById(id)?.toModel()
    }

    suspend fun getInsulinTypeByName(name: String): InsulinType? {
        return database.metabolicEventsDao().getInsulinTypeByName(name)?.toModel()
    }

    suspend fun insertInsulinType(type: InsulinType) {
        database.metabolicEventsDao().insertInsulinType(type.toEntity())
    }

    suspend fun deleteInsulinType(insulinType: InsulinType) {
        database.metabolicEventsDao().deleteInsulinType(insulinType.id)
    }

    suspend fun loadInsulinApplications(from: Timestamp? = null, to: Timestamp? = null): List<InsulinApplication> {
        val dao = database.metabolicEventsDao()
        val entities = dao.getInsulinApplicationsInRange(from?.ms ?: 0, to?.ms ?: Long.MAX_VALUE)
        val types = dao.getAllInsulinTypes().associateBy { it.id }
        return entities.mapNotNull { entity ->
            val typeEntity = types[entity.insulin_type_id]
            typeEntity?.let { entity.toModel(it.toModel()) }
        }
    }

    suspend fun insertInsulinApplication(insulinApplication: InsulinApplication): Long {
        val entity = insulinApplication.toEntity()
        val id = database.metabolicEventsDao().insertInsulinApplication(entity)
        if (id != -1L) {
            insulinApplication.id = id
        }
        return id
    }

    suspend fun deleteInsulinApplication(insulinApplication: InsulinApplication) {
        database.metabolicEventsDao().deleteInsulinApplication(insulinApplication.id)
    }

    /**
     * Gets the therapy data which is or was active at the given time.
     */
    suspend fun getTherapyDataForTimeInstant(time: Timestamp): TherapyData {
        ToDo.toBeImplemented("Calculate/get therapy data for given timestamp")
        return mockSimpleTherapyData()
    }
}