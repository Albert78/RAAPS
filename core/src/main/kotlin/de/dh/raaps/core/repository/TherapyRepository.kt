package de.dh.raaps.core.repository

import de.dh.raaps.common.model.DEFAULT_BG_LOW_THRESHOLD_MGDL
import de.dh.raaps.common.model.DEFAULT_BG_TARGET_MGDL
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.InsulinProfile
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

    // --- Profile Operations ---

    suspend fun getAllProfiles(): List<InsulinProfile> {
        return therapyDao.getAllProfiles().mapNotNull { entity ->
            val insulinType = getInsulinTypeById(entity.insulin_type_id)
            insulinType?.let { entity.toModel(it) }
        }
    }

    fun observeAllProfiles(): Flow<List<InsulinProfile>> {
        return therapyDao.observeAllProfiles().map { entities ->
            entities.mapNotNull { entity ->
                val insulinType = getInsulinTypeById(entity.insulin_type_id)
                insulinType?.let { entity.toModel(it) }
            }
        }
    }

    suspend fun getProfileById(id: Long): InsulinProfile? {
        val entity = therapyDao.getProfileById(id) ?: return null
        val insulinType = getInsulinTypeById(entity.insulin_type_id) ?: return null
        return entity.toModel(insulinType)
    }

    suspend fun insertProfile(profile: InsulinProfile): Long {
        val id = therapyDao.insertProfile(profile.toEntity())
        if (id != -1L) {
            profile.id = id
        }
        return id
    }

    suspend fun updateProfile(profile: InsulinProfile) {
        therapyDao.updateProfile(profile.toEntity())
    }

    suspend fun deleteProfile(profile: InsulinProfile) {
        therapyDao.deleteProfile(profile.id)
    }

    // --- Current Therapy Settings Operations ---

    fun observeCurrentTherapySettings(): Flow<CurrentTherapySettings?> = therapyDao.observeCurrentTherapySettings()
        .map { getCurrentTherapySettings() }

    suspend fun getCurrentTherapySettings(): CurrentTherapySettings? {
        val entity = therapyDao.getCurrentTherapySettings() ?: return null
        val profile = getProfileById(entity.profile_id) ?: return null
        val insulinType = getInsulinTypeById(entity.insulin_type_id) ?: return null
        val settings = entity.toModel(profile, insulinType)
        
        return if (settings.defaultBgBlocks.isEmpty()) {
            settings.copy(
                defaultBgBlocks = listOf(
                    BgBlock(
                        Minutes.ofHours(24),
                        BgValue.fromMgDl(DEFAULT_BG_TARGET_MGDL),
                        BgValue.fromMgDl(DEFAULT_BG_LOW_THRESHOLD_MGDL)
                    )
                )
            )
        } else {
            settings
        }
    }

    suspend fun updateCurrentTherapySettings(currentTherapySettings: CurrentTherapySettings) {
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