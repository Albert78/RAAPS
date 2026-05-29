package de.dh.raaps.plugin.simbody

import android.app.Application
import de.dh.raaps.common.model.Pump
import de.dh.raaps.common.model.ToDo

class SimBodyPump(
    val application: Application
): Pump {
    override val name: String = "Sim Body Pump Plugin"

    // TODO

    override fun start() {
        ToDo.toBeImplemented("SamplePumpPlugin")
    }

    override fun stop() {
        // TODO
    }
}