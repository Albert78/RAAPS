package de.dh.raaps.plugin.pump

import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.Plugin
import de.dh.raaps.common.model.PluginManager

class SampleInsulinPumpPlugin : InsulinPump, Plugin {
    override val neededPermissions: Collection<String> = emptyList()

    override val name: String = "Sample Pump Plugin"
    // TODO

    override fun initialize(pluginManager: PluginManager) {
        // Nothing to do
    }

    override fun stop() {
        // TODO
    }
}