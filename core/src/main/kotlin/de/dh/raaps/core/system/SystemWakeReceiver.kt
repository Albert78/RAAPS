package de.dh.raaps.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.dh.raaps.core.RAAPSRegistry

/**
 * Central receiver for system alarms to wake up different components of the application.
 * It forwards the wakeup event to the [SystemWakeService] for dispatching.
 */
class SystemWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Access registry through the application context if possible, 
        // or a global provider if the project has one.
        // Assuming the application class provides access to the registry.
        val registry = (context.applicationContext as? RegistryProvider)?.registry
        registry?.wakeService?.dispatchWakeup(intent)
    }

    /**
     * Interface to be implemented by the Application class to provide access to the registry.
     */
    interface RegistryProvider {
        val registry: RAAPSRegistry
    }
}