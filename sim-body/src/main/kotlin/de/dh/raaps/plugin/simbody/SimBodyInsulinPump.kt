package de.dh.raaps.plugin.simbody

import android.app.Application
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.ToDo

class SimBodyInsulinPump(
    val bodyModel: BodyModel,
    val application: Application
): InsulinPump {
    override val name: String = "Sim Body Pump Plugin"

    // TODO

    override fun start() {
        ToDo.toBeImplemented("SamplePumpPlugin")
    }

    override fun stop() {
        // TODO
    }
}