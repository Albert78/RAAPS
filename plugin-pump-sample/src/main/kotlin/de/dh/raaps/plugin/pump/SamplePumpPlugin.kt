package de.dh.raaps.plugin.pump

import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.PumpPlugin
import de.dh.raaps.common.model.ToDo

class SamplePumpPlugin : PumpPlugin {
    override val name: String = "Sample Pump Plugin"
    override val neededPermissions: Collection<String> = emptyList()

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