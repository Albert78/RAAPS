package de.dh.raaps.plugin.pump

import de.dh.raaps.common.model.PumpPlugin
import de.dh.raaps.common.model.ToDo

class SamplePumpPlugin : PumpPlugin {
    override val name: String = "Sample Pump Plugin"

    // TODO

    override fun start() {
        ToDo.toBeImplemented("SamplePumpPlugin")
    }

    override fun stop() {
        // TODO
    }
}