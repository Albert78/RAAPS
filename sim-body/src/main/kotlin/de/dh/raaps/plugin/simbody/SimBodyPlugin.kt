package de.dh.raaps.plugin.simbody

import android.app.Application
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.Plugin
import de.dh.raaps.common.model.PluginManager

/**
 * A plugin which provides a glucose source and a pump instance which are connected to a
 * "virtual human body", simulating the influence of meals and insulin.
 */
class SimBodyPlugin(
    val application: Application
) : Plugin {
    val bodyModel = BodyModel(DEFAULT_SIM_BODY_PROFILE)
    val pumpDevice = SimBodyPumpDevice(bodyModel, DEFAULT_SIM_THERAPY_PROFILE)
    override val name: String = "Sim Body CGM Plugin"
    override val neededPermissions: Collection<String> = emptyList()

    override fun initialize(pluginManager: PluginManager) {
        // Nothing to do
    }

    fun getGlucoseSource(): GlucoseSource = SimBodyCgmSource(bodyModel, pumpDevice, application)
    fun getInsulinPump(): InsulinPump = SimBodyInsulinPump(pumpDevice, application)
}
