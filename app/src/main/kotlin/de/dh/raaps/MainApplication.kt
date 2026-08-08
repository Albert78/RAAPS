package de.dh.raaps

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import androidx.core.content.ContextCompat
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.SystemRegistryImpl
import de.dh.raaps.common.util.PersistentLogger
import de.dh.raaps.core.system.RegistryProvider
import de.dh.raaps.notifications.AndroidNotificationsImpl
import de.dh.raaps.pluginmanager.PluginManagerImpl
import de.dh.raaps.services.ApsService
import de.dh.raaps.services.BootReceiver
import de.dh.raaps.ui.activities.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main application class for RAAPS.
 * Responsibility is limited to system entry points and lifecycle management.
 */
class MainApplication : Application(), RegistryProvider {
    lateinit var androidNotifications: AndroidNotificationsImpl
        private set

    override lateinit var registry: SystemRegistry
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this

        PersistentLogger.init(this)

        androidNotifications = AndroidNotificationsImpl(this)

        val pluginManager = PluginManagerImpl(this)

        registry = SystemRegistryImpl.create(
            application = this,
            scope = applicationScope,
            pluginManager = pluginManager,
            androidNotifications = androidNotifications,
            onPermissionsChanged = { startApsService() },

            // Hack to make things visible in modules without sharing interna of app module
            apsServiceClass = ApsService::class.java
        )

        startApsService()

        // Dynamic injection setup - called function depends on chosen flavor
        setupSystem(registry, pluginManager, this)

        MainActivity.getExtraNavGraphs = ::getExtraNavGraphs

        BootReceiver.enableBootReceiver(this)
    }

    override fun onTerminate() {
        if (::registry.isInitialized) {
            registry.glucoseSourceManager.stop()
            registry.systemManager.stop()
        }
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

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    companion object {
        lateinit var instance: MainApplication
            private set
    }
}
