package de.dh.raaps

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import androidx.core.content.ContextCompat
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.core.aps.APS
import de.dh.raaps.core.aps.Core
import de.dh.raaps.core.repository.DatabaseInitializer
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.notifications.ApsNotificationData
import de.dh.raaps.notifications.ApsNotificationManager
import de.dh.raaps.pluginmanager.PluginManagerImpl
import de.dh.raaps.services.ApsService
import de.dh.raaps.services.BootReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainApplication : Application() {
    lateinit var notificationManager: ApsNotificationManager
        private set
    lateinit var appPreferencesRepository: AppPreferencesRepository
        private set
    lateinit var pluginManager: PluginManager
        private set
    lateinit var glucoseRepository: GlucoseRepository
        private set
    lateinit var therapyRepository: TherapyRepository
        private set
    lateinit var treatmentRepository: TreatmentRepository
        private set
    lateinit var aps: APS
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this

        notificationManager = ApsNotificationManager(this)
        appPreferencesRepository = AppPreferencesRepository(context = this, scope = applicationScope)
        val appDatabase = AppDatabase.getInstance(this)

        glucoseRepository = GlucoseRepository(appDatabase)
        therapyRepository = TherapyRepository(appDatabase)
        treatmentRepository = TreatmentRepository(
            historySize = Minutes.ofHours(Core.METABOLIC_EVENTS_HISTORY_HOURS),
            appDatabase = appDatabase
        )

        runBlocking {
            DatabaseInitializer.initialize(
                context = this@MainApplication,
                treatmentRepository = treatmentRepository,
                therapyRepository = therapyRepository)
        }

        startApsService()

        aps = APS(glucoseRepository, therapyRepository, treatmentRepository, appPreferencesRepository, this)
        aps.startInitialization()

        pluginManager = PluginManagerImpl(this)

        setupSystem(aps, pluginManager, this)

        installNotificationUpdater()

        BootReceiver.enableBootReceiver(this)
    }

    override fun onTerminate() {
        aps.stop()
        applicationScope.cancel()
        super.onTerminate()
    }

    fun triggerUpdatesAfterPermissionsChange() {
        pluginManager.triggerUpdatesAfterPermissionsChange()
        startApsService()
    }

    fun startApsService() {
        val intent = Intent(this, ApsService::class.java)
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Android 12+ may throw ForegroundServiceStartNotAllowedException
            // if called from background without proper exemptions (like ignoring battery optimizations).
            // TODO: Handle
        } catch (e: IllegalStateException) {
            // TODO: Handle
        }
    }

    fun installNotificationUpdater() {
        applicationScope.launch {
            aps.lastDataTime.collect { timestamp ->
                val notificationData = ApsNotificationData.create(aps)
                notificationManager.updateNotification(notificationData)
            }
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    companion object {
        lateinit var instance: MainApplication
            private set
    }
}