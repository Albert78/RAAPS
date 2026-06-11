package de.dh.raaps.core.repository

import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.CurrentTherapyData
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.MetabolicEventsDao
import de.dh.raaps.core.repository.db.TherapyDao
import de.dh.raaps.core.repository.db.toEntity
import de.dh.raaps.core.repository.db.toModel

/**
 * Repository for therapy settings, profiles and active therapy data.
 */
class TherapyRepository(
    appDatabase: AppDatabase
) {
    private val therapyDao: TherapyDao = appDatabase.therapyDao()
    private val metabolicEventsDao: MetabolicEventsDao = appDatabase.metabolicEventsDao()

    // --- Insulin Types (Lookup Helper) ---

    /**
     * Internal lookup to resolve InsulinType for CurrentTherapyData.
     * Master CRUD for InsulinTypes is in TreatmentRepository.
     */
    private suspend fun getInsulinTypeById(id: String): InsulinType? {
        return metabolicEventsDao.getInsulinTypeById(id)?.toModel()
    }

    /**
     * Helper for initialization or scenarios where only TherapyRepository is available.
     */
    suspend fun getAllInsulinTypes(): List<InsulinType> {
        return metabolicEventsDao.getAllInsulinTypes().map { it.toModel() }
    }

    // --- Therapy Data Operations ---

    suspend fun insertTherapyData(therapyData: TherapyData): Long {
        val id = therapyDao.insertTherapyData(therapyData.toEntity())
        if (id != -1L) {
            therapyData.id = id
        }
        return id
    }

    suspend fun updateTherapyData(therapyData: TherapyData) {
        therapyDao.updateTherapyData(therapyData.toEntity())
    }

    suspend fun getTherapyDataById(id: Long): TherapyData? {
        return therapyDao.getTherapyDataById(id)?.toModel()
    }

    suspend fun deleteTherapyData(id: Long) {
        therapyDao.deleteTherapyData(id)
    }

    // --- Profile Operations ---

    suspend fun getAllProfiles(): List<Profile> {
        return therapyDao.getAllProfiles().mapNotNull { entity ->
            val therapyData = therapyDao.getTherapyDataById(entity.therapy_data_id)?.toModel()
            therapyData?.let { entity.toModel(it) }
        }
    }

    suspend fun getProfileById(id: Long): Profile? {
        val entity = therapyDao.getProfileById(id) ?: return null
        val therapyData = therapyDao.getTherapyDataById(entity.therapy_data_id)?.toModel() ?: return null
        return entity.toModel(therapyData)
    }

    suspend fun insertProfile(profile: Profile): Long {
        if (profile.therapyData.id == ID_UNDEFINED) {
            val therapyDataId = insertTherapyData(profile.therapyData)
            profile.therapyData.id = therapyDataId
        }
        val id = therapyDao.insertProfile(profile.toEntity())
        if (id != -1L) {
            profile.id = id
        }
        return id
    }

    suspend fun updateProfile(profile: Profile) {
        updateTherapyData(profile.therapyData)
        therapyDao.updateProfile(profile.toEntity())
    }

    suspend fun deleteProfile(profile: Profile) {
        therapyDao.deleteProfile(profile.id)
    }

    // --- Current Therapy Data Operations ---

    suspend fun getCurrentTherapyData(): CurrentTherapyData? {
        val entity = therapyDao.getCurrentTherapyData() ?: return null
        val therapyData = therapyDao.getTherapyDataById(entity.therapy_data_id)?.toModel() ?: return null
        val insulinType = getInsulinTypeById(entity.insulin_type_id) ?: return null
        return entity.toModel(therapyData, insulinType)
    }

    suspend fun updateCurrentTherapyData(currentTherapyData: CurrentTherapyData) {
        if (currentTherapyData.therapyData.id == ID_UNDEFINED) {
            insertTherapyData(currentTherapyData.therapyData)
        } else {
            updateTherapyData(currentTherapyData.therapyData)
        }

        val entity = currentTherapyData.toEntity()
        val existing = therapyDao.getCurrentTherapyData()
        if (existing == null) {
            val id = therapyDao.insertCurrentTherapyData(entity)
            if (id != -1L) {
                currentTherapyData.id = id
            }
        } else {
            therapyDao.updateCurrentTherapyData(entity.copy(id = existing.id))
            currentTherapyData.id = existing.id
        }
    }
}