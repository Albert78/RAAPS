package de.dh.raaps

import android.app.Application
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.core.aps.APS
import de.dh.raaps.plugin.glucose.receiver.ExternalSourceType
import de.dh.raaps.plugin.glucose.receiver.ReceiverGlucosePlugin
import de.dh.raaps.plugin.pump.SamplePumpPlugin

fun setupSystem(aps: APS, pluginManager: PluginManager, application: Application) {
    val glucosePlugin = ReceiverGlucosePlugin(
        application,
        ExternalSourceType.xDrip5Min
    )
    pluginManager.addPlugin(glucosePlugin)
    aps.glucoseSource = glucosePlugin
    val pumpPlugin = SamplePumpPlugin()
    aps.pump = pumpPlugin
}