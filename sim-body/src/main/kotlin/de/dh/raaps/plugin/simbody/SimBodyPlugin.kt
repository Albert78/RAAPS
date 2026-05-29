package de.dh.raaps.plugin.simbody

import android.app.Application
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.Plugin
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.Pump

class SimBodyPlugin(
    val application: Application
) : Plugin {
    override val name: String = "Sim Body CGM Plugin"

    override val neededPermissions: Collection<String> = emptyList()

    override fun initialize(pluginManager: PluginManager) {
        // Nothing to do
    }

    fun getGlucoseSource(): GlucoseSource = SimBodyCgmSource(application)
    fun getPump(): Pump = SimBodyPump(application)
}