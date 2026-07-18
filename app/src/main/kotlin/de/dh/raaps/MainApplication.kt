package de.dh.raaps

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import androidx.core.content.ContextCompat
import de.dh.raaps.core.RAAPSRegistry
import de.dh.raaps.core.RAAPSRegistryImpl
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

/**
 * Main application class for RAAPS.
 * Responsibility is limited to system entry points and lifecycle management.
 */
class MainApplication : Application() {
    lateinit var notificationManager: ApsNotificationManager
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this

        notificationManager = ApsNotificationManager(this)
        
        val pluginManager = PluginManagerImpl(this)
        
        val registry = RAAPSRegistryImpl.create(
            application = this,
            scope = applicationScope,
            pluginManager = pluginManager,
            onPermissionsChanged = { startApsService() }
        )
        RAAPSRegistry.setInstance(registry)

        startApsService()

        setupSystem(registry.aps, pluginManager, this)

        installNotificationUpdater()

        BootReceiver.enableBootReceiver(this)
    }

    override fun onTerminate() {
        RAAPSRegistry.instance.aps.stop()
        applicationScope.cancel()
        super.onTerminate()
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
            RAAPSRegistry.instance.aps.lastDataTime.collect { _ ->
                val notificationData = ApsNotificationData.create(RAAPSRegistry.instance.aps)
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