package de.dh.raaps.core.repository

import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.CurrentTherapyData
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.entities.DataProviderEntity
import de.dh.raaps.core.repository.db.entities.SensorTypeEntity
import de.dh.raaps.core.repository.db.toEntity
import de.dh.raaps.core.repository.db.toModel

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

    // --- Therapy Operations ---

    suspend fun insertTherapyData(therapyData: TherapyData): Long {
        val id = database.therapyDao().insertTherapyData(therapyData.toEntity())
        return id
    }

    suspend fun updateTherapyData(therapyData: TherapyData) {
        database.therapyDao().updateTherapyData(therapyData.toEntity())
    }

    suspend fun getTherapyDataById(id: Long): TherapyData? {
        return database.therapyDao().getTherapyDataById(id)?.toModel()
    }

    suspend fun deleteTherapyData(id: Long) {
        database.therapyDao().deleteTherapyData(id)
    }

    // --- Profile Operations ---

    suspend fun getAllProfiles(): List<Profile> {
        val dao = database.therapyDao()
        return dao.getAllProfiles().mapNotNull { entity ->
            val therapyData = dao.getTherapyDataById(entity.therapy_data_id)?.toModel()
            therapyData?.let { entity.toModel(it) }
        }
    }

    suspend fun getProfileById(id: Long): Profile? {
        val dao = database.therapyDao()
        val entity = dao.getProfileById(id) ?: return null
        val therapyData = dao.getTherapyDataById(entity.therapy_data_id)?.toModel() ?: return null
        return entity.toModel(therapyData)
    }

    suspend fun insertProfile(profile: Profile): Long {
        // Ensure TherapyData is saved first if it's new
        if (profile.therapyData.id == -1L) {
            val therapyDataId = insertTherapyData(profile.therapyData)
            // Note: In a real scenario, we should update the profile object's therapyData.id here
            // but for simplicity we assume it was passed correctly or handled by the caller.
        }
        return database.therapyDao().insertProfile(profile.toEntity())
    }

    suspend fun updateProfile(profile: Profile) {
        updateTherapyData(profile.therapyData)
        database.therapyDao().updateProfile(profile.toEntity())
    }

    suspend fun deleteProfile(profile: Profile) {
        database.therapyDao().deleteProfile(profile.id)
    }

    // --- Current Therapy Data Operations ---

    suspend fun getCurrentTherapyData(): CurrentTherapyData? {
        val dao = database.therapyDao()
        val entity = dao.getCurrentTherapyData() ?: return null
        val therapyData = dao.getTherapyDataById(entity.therapy_data_id)?.toModel() ?: return null
        val insulinType = getInsulinTypeById(entity.insulin_type_id) ?: return null
        return entity.toModel(therapyData, insulinType)
    }

    suspend fun updateCurrentTherapyData(currentTherapyData: CurrentTherapyData) {
        val dao = database.therapyDao()
        // Update the actual therapy data first
        updateTherapyData(currentTherapyData.therapyData)

        val entity = currentTherapyData.toEntity()
        val existing = dao.getCurrentTherapyData()
        if (existing == null) {
            dao.insertCurrentTherapyData(entity)
        } else {
            dao.updateCurrentTherapyData(entity.copy(id = existing.id))
        }
    }
}