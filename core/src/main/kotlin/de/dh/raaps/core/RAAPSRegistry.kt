package de.dh.raaps.core

import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.core.aps.APS
import de.dh.raaps.core.aps.TherapyManager
import de.dh.raaps.core.pump.PumpCoordinator
import de.dh.raaps.core.repository.DeviceManagementRepository
import de.dh.raaps.core.repository.FoodRepository
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Central registry for all core services, repositories, and coordinators.
 * This acts as the single source of truth for component access within the application.
 */
interface RAAPSRegistry {
    // Data Repositories
    val glucoseRepository: GlucoseRepository
    val therapyRepository: TherapyRepository
    val treatmentRepository: TreatmentRepository
    val foodRepository: FoodRepository
    val deviceManagementRepository: DeviceManagementRepository
    val appPreferencesRepository: AppPreferencesRepository

    // Logical Managers and Services
    val therapyManager: TherapyManager
    val aps: APS
    val pluginManager: PluginManager
    
    /**
     * Provides access to the current pump coordinator. 
     * Note that this might be null if no pump is configured or connected.
     */
    val pumpCoordinator: PumpCoordinator?
}