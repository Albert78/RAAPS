package de.dh.raaps.core

import android.app.Application
import android.content.Context
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.core.aps.APS
import de.dh.raaps.core.aps.Core
import de.dh.raaps.core.aps.TherapyManager
import de.dh.raaps.core.system.SystemTimeService
import de.dh.raaps.core.system.SystemWakeService
import de.dh.raaps.core.system.SystemWakeServiceImpl
import de.dh.raaps.core.pump.PumpCoordinator
import de.dh.raaps.core.repository.DatabaseInitializer
import de.dh.raaps.core.repository.DeviceManagementRepository
import de.dh.raaps.core.repository.FoodRepository
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.SettingsRepository
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository
import de.dh.raaps.core.repository.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Default implementation of the [RAAPSRegistry].
 * Manages the lifecycle and dependencies of all core components.
 */
class RAAPSRegistryImpl(
    override val appContext: Context,
    override val glucoseRepository: GlucoseRepository,
    override val therapyRepository: TherapyRepository,
    override val treatmentRepository: TreatmentRepository,
    override val foodRepository: FoodRepository,
    override val deviceManagementRepository: DeviceManagementRepository,
    override val settingsRepository: SettingsRepository,
    override val appPreferencesRepository: AppPreferencesRepository,
    override val therapyManager: TherapyManager,
    override val aps: APS,
    override val pluginManager: PluginManager,
    override val wakeService: SystemWakeService,
    override val timeService: TimeService,
    override val permissionsChangedHandler: PermissionsChangedHandler
) : RAAPSRegistry {

    override val pumpCoordinator: PumpCoordinator?
        get() = aps.pumpCoordinator

    companion object {
        /**
         * Factory method to create and initialize the [RAAPSRegistry].
         * This encapsulates the complex setup of all repositories, managers, and the APS core.
         */
        fun create(
            application: Application,
            scope: CoroutineScope,
            pluginManager: PluginManager,
            onPermissionsChanged: () -> Unit
        ): RAAPSRegistry {
            val appPreferencesRepository = AppPreferencesRepository(context = application, scope = scope)
            val appDatabase = AppDatabase.getInstance(application)

            // Initialize repositories
            val glucoseRepository = GlucoseRepository(appDatabase)
            val therapyRepository = TherapyRepository(appDatabase)
            val treatmentRepository = TreatmentRepository(
                historySize = Minutes.ofHours(Core.METABOLIC_EVENTS_HISTORY_HOURS),
                appDatabase = appDatabase
            )
            val foodRepository = FoodRepository(appDatabase)
            val deviceManagementRepository = DeviceManagementRepository(appDatabase)
            val settingsRepository = SettingsRepository(appDatabase)

            // Initialize Managers
            val therapyManager = TherapyManager(therapyRepository, appPreferencesRepository)
            val wakeService = SystemWakeServiceImpl(application)
            val timeService = SystemTimeService(scope = scope)

            runBlocking {
                treatmentRepository.load()
                DatabaseInitializer.initialize(application, treatmentRepository, therapyRepository, settingsRepository)
            }

            val aps = APS(
                glucoseRepository = glucoseRepository,
                therapyRepository = therapyRepository,
                settingsRepository = settingsRepository,
                treatmentRepository = treatmentRepository,
                appPreferencesRepository = appPreferencesRepository,
                therapyManager = therapyManager,
                wakeService = wakeService,
                timeService = timeService,
                context = application
            )
            aps.startInitialization()

            val permissionsHandler = PermissionsChangedHandler {
                pluginManager.triggerUpdatesAfterPermissionsChange()
                onPermissionsChanged()
            }

            return RAAPSRegistryImpl(
                appContext = application,
                glucoseRepository = glucoseRepository,
                therapyRepository = therapyRepository,
                treatmentRepository = treatmentRepository,
                foodRepository = foodRepository,
                deviceManagementRepository = deviceManagementRepository,
                settingsRepository = settingsRepository,
                appPreferencesRepository = appPreferencesRepository,
                therapyManager = therapyManager,
                aps = aps,
                pluginManager = pluginManager,
                wakeService = wakeService,
                timeService = timeService,
                permissionsChangedHandler = permissionsHandler
            )
        }
    }
}