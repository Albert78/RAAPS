package de.dh.raaps.core

import android.app.Service
import android.content.Context
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.core.aps.APS
import de.dh.raaps.core.aps.SystemManager
import de.dh.raaps.core.aps.TherapyManager
import de.dh.raaps.core.pump.PumpManager
import de.dh.raaps.core.repository.DeviceManagementRepository
import de.dh.raaps.core.repository.FoodRepository
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.SettingsRepository
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository
import de.dh.raaps.core.system.NotificationManager
import de.dh.raaps.core.system.SystemWakeService

/**
 * Functional interface for handling permission change events.
 */
fun interface PermissionsChangedHandler {
    /**
     * Called when the application permissions have changed and dependent services
     * need to be updated.
     */
    fun onPermissionsChanged()
}

/**
 * Central registry for all core services, repositories, and coordinators.
 * This acts as the single source of truth for component access within the application.
 */
interface SystemRegistry {
    val appContext: Context

    // Data Repositories
    val glucoseRepository: GlucoseRepository
    val therapyRepository: TherapyRepository
    val treatmentRepository: TreatmentRepository
    val foodRepository: FoodRepository
    val deviceManagementRepository: DeviceManagementRepository
    val settingsRepository: SettingsRepository
    val appPreferencesRepository: AppPreferencesRepository

    // System Managers and Services
    val pluginManager: PluginManager
    val wakeService: SystemWakeService
    val timeService: TimeService
    val notificationManager: NotificationManager

    // Domain Managers and Services
    val therapyManager: TherapyManager
    val aps: APS
    val systemManager: SystemManager
    val pumpManager: PumpManager
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel

    // Other stuff

    /**
     * Handler for permission change events.
     */
    val permissionsChangedHandler: PermissionsChangedHandler

    val apsServiceClass: Class<out Service>
}