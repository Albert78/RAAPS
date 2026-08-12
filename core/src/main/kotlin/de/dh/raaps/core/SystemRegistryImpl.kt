package de.dh.raaps.core

import android.app.Application
import android.app.Service
import android.content.Context
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.core.aps.Core
import de.dh.raaps.core.aps.GlucoseSourceManager
import de.dh.raaps.core.aps.SystemManager
import de.dh.raaps.core.aps.SystemManagerImpl
import de.dh.raaps.core.aps.TherapyManager
import de.dh.raaps.core.pump.PumpManager
import de.dh.raaps.core.pump.PumpManagerImpl
import de.dh.raaps.core.repository.DatabaseInitializer
import de.dh.raaps.core.repository.DeviceManagementRepository
import de.dh.raaps.core.repository.FoodRepository
import de.dh.raaps.core.repository.CoreInsightRepository
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.SettingsRepository
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.system.AndroidNotifications
import de.dh.raaps.core.system.SystemWakeService
import de.dh.raaps.core.system.SystemWakeServiceImpl
import de.dh.raaps.core.system.TimeServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Default implementation of the [SystemRegistry].
 * Manages the lifecycle and dependencies of all core components.
 */
class SystemRegistryImpl(
    override val appContext: Context,
    override val glucoseRepository: GlucoseRepository,
    override val therapyRepository: TherapyRepository,
    override val treatmentRepository: TreatmentRepository,
    override val foodRepository: FoodRepository,
    override val deviceManagementRepository: DeviceManagementRepository,
    override val settingsRepository: SettingsRepository,
    override val coreInsightRepository: CoreInsightRepository,
    override val appPreferencesRepository: AppPreferencesRepository,
    override val glucoseSourceManager: GlucoseSourceManager,
    override val therapyManager: TherapyManager,
    override val systemManager: SystemManager,
    override val pluginManager: PluginManager,
    override val wakeService: SystemWakeService,
    override val timeService: TimeService,
    override val pumpManager: PumpManager,
    override val carbsInsulinCalculationModel: CarbsInsulinCalculationModel,
    override val permissionsChangedHandler: PermissionsChangedHandler,
    override val apsServiceClass: Class<out Service>
) : SystemRegistry {
    companion object {
        /**
         * Factory method to create and initialize the [SystemRegistry].
         * This encapsulates the complex setup of all repositories, managers, and the APS core.
         */
        fun create(
            application: Application,
            scope: CoroutineScope,
            pluginManager: PluginManager,
            androidNotifications: AndroidNotifications,
            onPermissionsChanged: () -> Unit,
            apsServiceClass: Class<out Service>
        ): SystemRegistry {
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
            val coreInsightRepository = CoreInsightRepository(appDatabase)

            // Initialize Managers
            val wakeService = SystemWakeServiceImpl(application)
            val timeService = TimeServiceImpl(wakeService = wakeService, scope = scope)
            val pumpManager = PumpManagerImpl(scope = scope, wakeService = wakeService)

            val glucoseSourceManager = GlucoseSourceManager(
                glucoseRepository = glucoseRepository,
                timeService = timeService
            )
            runBlocking {
                glucoseSourceManager.initialize()
            }

            val systemManager = SystemManagerImpl(
                glucoseSourceManager = glucoseSourceManager,
                wakeService = wakeService,
                settingsRepository = settingsRepository,
                timeService = timeService,
                androidNotifications = androidNotifications,
                scope = scope
            )

            val therapyManager = TherapyManager(
                therapyRepository = therapyRepository,
                treatmentRepository = treatmentRepository,
                appPreferencesRepository = appPreferencesRepository,
                pumpManager = pumpManager,
                systemManager = systemManager,
                scope = scope
            )
            val carbsInsulinCalculationModel = CarbsInsulinCalculationModel(timeService.tickInterval)

            runBlocking {
                treatmentRepository.load()
                DatabaseInitializer.initialize(application, treatmentRepository, therapyRepository, settingsRepository)
            }

            therapyManager.startInitialization()

            systemManager.startInitialization(
                treatmentRepository = treatmentRepository,
                therapyManager = therapyManager,
                appPreferencesRepository = appPreferencesRepository,
                carbsInsulinCalculationModel = carbsInsulinCalculationModel,
                coreInsightRepository = coreInsightRepository,
                context = application
            )

            val permissionsHandler = PermissionsChangedHandler {
                pluginManager.triggerUpdatesAfterPermissionsChange()
                onPermissionsChanged()
            }

            return SystemRegistryImpl(
                appContext = application,
                glucoseRepository = glucoseRepository,
                therapyRepository = therapyRepository,
                treatmentRepository = treatmentRepository,
                foodRepository = foodRepository,
                deviceManagementRepository = deviceManagementRepository,
                settingsRepository = settingsRepository,
                coreInsightRepository = coreInsightRepository,
                appPreferencesRepository = appPreferencesRepository,
                therapyManager = therapyManager,
                glucoseSourceManager = glucoseSourceManager,
                systemManager = systemManager,
                pluginManager = pluginManager,
                wakeService = wakeService,
                timeService = timeService,
                pumpManager = pumpManager,
                carbsInsulinCalculationModel = carbsInsulinCalculationModel,
                permissionsChangedHandler = permissionsHandler,
                apsServiceClass = apsServiceClass
            )
        }
    }
}
