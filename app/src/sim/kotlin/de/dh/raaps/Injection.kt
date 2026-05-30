package de.dh.raaps

import android.app.Application
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.core.aps.APS
import de.dh.raaps.plugin.simbody.SimBodyPlugin

fun setupSystem(aps: APS, pluginManager: PluginManager, application: Application) {
    val plugin = SimBodyPlugin(application)
    pluginManager.addPlugin(plugin)
    val glucoseSource = plugin.getGlucoseSource()
    aps.glucoseSource = glucoseSource
    val insulinPump = plugin.getInsulinPump()
    aps.insulinPump = insulinPump
}