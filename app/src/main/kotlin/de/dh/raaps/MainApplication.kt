package de.dh.raaps

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import androidx.core.content.ContextCompat
import de.dh.raaps.data.DataRepository
import de.dh.raaps.data.db.AppDatabase
import de.dh.raaps.model.APS
import de.dh.raaps.notifications.ApsNotificationData
import de.dh.raaps.notifications.ApsNotificationManager
import de.dh.raaps.plugin.glucose.receiver.ExternalSourceType
import de.dh.raaps.plugin.glucose.receiver.ReceiverGlucosePlugin
import de.dh.raaps.plugin.pump.SamplePumpPlugin
import de.dh.raaps.services.ApsService
import de.dh.raaps.services.BootReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainApplication : Application() {
    lateinit var notificationManager: ApsNotificationManager
        private set
    lateinit var mAppPreferencesRepository: AppPreferencesRepository
        private set
    lateinit var dataRepository: DataRepository
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
        mAppPreferencesRepository = AppPreferencesRepository(context = this, scope = applicationScope)
        val appDatabase = AppDatabase.getInstance(this)
        dataRepository = DataRepository(appDatabase)

        startApsService()

        aps = APS(dataRepository, mAppPreferencesRepository, this)
        aps.startInitialization()

        // TODO: Read plugins from preferences
        aps.pumpPlugin = SamplePumpPlugin()
        aps.glucosePlugin = ReceiverGlucosePlugin(this, ExternalSourceType.xDrip5Min)

        installNotificationUpdater()

        BootReceiver.enableBootReceiver(this)
    }

    override fun onTerminate() {
        aps.stop()
        applicationScope.cancel()
        super.onTerminate()
    }

    fun triggerUpdatesAfterPermissionsChange() {
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