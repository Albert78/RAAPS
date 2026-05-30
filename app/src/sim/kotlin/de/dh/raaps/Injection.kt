package de.dh.raaps

import android.app.Application
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.core.aps.APS
import de.dh.raaps.plugin.simbody.SimBodyPlugin

/**
 * Setup for a system where glucose source and insulin pump are provided by the [SimBodyPlugin],
 * which simulates real situations like food intake, sports, illness, stress etc.
 * With this plugin, we can interactively test our core calculation algorithms and the behavior
 * of the app in simulated, "real" situations.
 */
fun setupSystem(aps: APS, pluginManager: PluginManager, application: Application) {
    val plugin = SimBodyPlugin(application)
    pluginManager.addPlugin(plugin)
    val glucoseSource = plugin.getGlucoseSource()
    aps.glucoseSource = glucoseSource
    val insulinPump = plugin.getInsulinPump()
    aps.insulinPump = insulinPump
}