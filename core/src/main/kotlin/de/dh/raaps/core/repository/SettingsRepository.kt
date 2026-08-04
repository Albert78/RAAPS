package de.dh.raaps.core.repository

import de.dh.raaps.common.model.data.CurrentSettings
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.SettingsDao
import de.dh.raaps.core.repository.db.toEntity
import de.dh.raaps.core.repository.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for general system settings.
 */
class SettingsRepository(
    appDatabase: AppDatabase
) {
    private val settingsDao: SettingsDao = appDatabase.settingsDao()

    fun observeCurrentSettings(): Flow<CurrentSettings?> = settingsDao.observeCurrentSettings()
        .map { it?.toModel() }

    suspend fun getCurrentSettings(): CurrentSettings? {
        return settingsDao.getCurrentSettings()?.toModel()
    }

    suspend fun updateCurrentSettings(currentSettings: CurrentSettings) {
        val entity = currentSettings.toEntity()
        val existing = settingsDao.getCurrentSettings()
        if (existing == null) {
            val id = settingsDao.insertCurrentSettings(entity)
            if (id != -1L) {
                currentSettings.id = id
            }
        } else {
            settingsDao.updateCurrentSettings(entity.copy(id = existing.id))
            currentSettings.id = existing.id
        }
    }
}