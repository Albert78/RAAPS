package de.dh.raaps.plugin.pump

import de.dh.raaps.common.model.Plugin
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.Pump
import de.dh.raaps.common.model.ToDo

class SamplePumpPlugin : Pump, Plugin {
    override val neededPermissions: Collection<String> = emptyList()

    override val name: String = "Sample Pump Plugin"
    // TODO

    override fun initialize(pluginManager: PluginManager) {
        // Nothing to do
    }

    override fun start() {
        ToDo.toBeImplemented("SamplePumpPlugin")
    }

    override fun stop() {
        // TODO
    }
}