package de.dh.raaps.core.repository

import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.MetabolicEventsDao
import de.dh.raaps.core.repository.db.TherapyDao
import de.dh.raaps.core.repository.db.toEntity
import de.dh.raaps.core.repository.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
     * Internal lookup to resolve InsulinType for CurrentTherapySettings.
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

    fun observeAllProfiles(): Flow<List<Profile>> {
        return therapyDao.observeAllProfiles().map { entities ->
            entities.mapNotNull { entity ->
                // Note: This is not perfectly reactive for TherapyData changes inside the list,
                // but usually TherapyData is updated with the profile.
                val therapyData = therapyDao.getTherapyDataById(entity.therapy_data_id)?.toModel()
                therapyData?.let { entity.toModel(it) }
            }
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

    // --- Current Therapy Settings Operations ---

    fun observeCurrentTherapySettings(): Flow<CurrentTherapySettings?> = therapyDao.observeCurrentTherapySettings()
        .map { getCurrentTherapySettings() }

    suspend fun getCurrentTherapySettings(): CurrentTherapySettings? {
        val entity = therapyDao.getCurrentTherapySettings() ?: return null
        val profile = getProfileById(entity.profile_id) ?: return null
        val insulinType = getInsulinTypeById(entity.insulin_type_id) ?: return null
        return entity.toModel(profile, insulinType)
    }

    suspend fun updateCurrentTherapySettings(currentTherapySettings: CurrentTherapySettings) {
        if (currentTherapySettings.profile.therapyData.id == ID_UNDEFINED) {
            insertTherapyData(currentTherapySettings.profile.therapyData)
        } else {
            updateTherapyData(currentTherapySettings.profile.therapyData)
        }

        val entity = currentTherapySettings.toEntity()
        val existing = therapyDao.getCurrentTherapySettings()
        if (existing == null) {
            val id = therapyDao.insertCurrentTherapySettings(entity)
            if (id != -1L) {
                currentTherapySettings.id = id
            }
        } else {
            therapyDao.updateCurrentTherapySettings(entity.copy(id = existing.id))
            currentTherapySettings.id = existing.id
        }
    }
}
