package de.dh.raaps.core

import android.app.Service
import android.content.Context
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.core.aps.GlucoseSourceManager
import de.dh.raaps.core.aps.SystemManager
import de.dh.raaps.core.aps.TherapyManager
import de.dh.raaps.core.pump.PumpManager
import de.dh.raaps.core.repository.DeviceManagementRepository
import de.dh.raaps.core.repository.FoodRepository
import de.dh.raaps.core.repository.AlgorithmInsightRepository
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.SettingsRepository
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository
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
    /**
     * The global application context.
     */
    val appContext: Context

    // Data Repositories

    /**
     * Repository for data providers, sensors and glucose values. The glucose source plugin
     * might have its own database, if needed.
     */
    val glucoseRepository: GlucoseRepository

    /**
     * Repository for therapy settings, profiles, and active therapy configurations.
     */
    val therapyRepository: TherapyRepository

    /**
     * Repository for active metabolic treatments, including insulin applications and carb intake.
     */
    val treatmentRepository: TreatmentRepository

    /**
     * Repository for managing known food items and nutritional data.
     */
    val foodRepository: FoodRepository

    /**
     * Repository for tracking device-related events, such as cannula or reservoir changes.
     */
    val deviceManagementRepository: DeviceManagementRepository

    /**
     * Repository for general system and application settings.
     */
    val settingsRepository: SettingsRepository

    /**
     * Repository for algorithm internal metrics and decision reasoning history.
     */
    val algorithmInsightRepository: AlgorithmInsightRepository

    /**
     * Repository for lightweight application preferences and key-value pairs.
     */
    val appPreferencesRepository: AppPreferencesRepository

    // System Managers and Services

    /**
     * Central coordinator for managing system plugins (e.g., pump or glucose source drivers).
     */
    val pluginManager: PluginManager

    /**
     * Manages system wakeups and wake locks to ensure critical background tasks are executed.
     */
    val wakeService: SystemWakeService

    /**
     * Provides the system-wide time reference and handles synchronized ticking for background processes.
     */
    val timeService: TimeService

    // Domain Managers and Services

    /**
     * The mathematical core for calculating insulin-on-board (IOB) and carbs-on-board (COB).
     */
    val carbsInsulinCalculationModel: CarbsInsulinCalculationModel

    /**
     * Manages the active glucose data source and processes incoming blood glucose readings.
     */
    val glucoseSourceManager: GlucoseSourceManager

    /**
     * Central interface for monitoring and interacting with the insulin pump hardware.
     */
    val pumpManager: PumpManager

    /**
     * Core coordinator for therapy logic, combining data from various sources to generate APS recommendations.
     */
    val therapyManager: TherapyManager

    /**
     * Manages the overall application state, including the active APS mode and system-wide issues.
     */
    val systemManager: SystemManager

    // Other stuff

    /**
     * Handler for permission change events.
     */
    val permissionsChangedHandler: PermissionsChangedHandler

    /**
     * The class reference for the background service that hosts the APS core logic.
     */
    val apsServiceClass: Class<out Service>
}
