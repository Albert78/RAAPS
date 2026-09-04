package de.dh.raaps

import android.app.Application
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.plugin.simbody.SimBodyPlugin
import de.dh.raaps.plugin.simbody.ui.SimBodyNavGraph

private var simBodyPlugin: SimBodyPlugin? = null

/**
 * Setup for a system where glucose source and insulin pump are provided by the [SimBodyPlugin],
 * which simulates real situations like food intake, sports, illness, stress etc.
 * With this plugin, we can interactively test our core calculation algorithms and the behavior
 * of the app in simulated, "real" situations.
 */
fun setupSystem(registry: SystemRegistry, pluginManager: PluginManager, application: Application) {
    val pumpManager = registry.pumpManager
    val plugin = SimBodyPlugin(application, registry.wakeService)
    simBodyPlugin = plugin
    pluginManager.addPlugin(plugin)
    val glucoseSource = plugin.getGlucoseSource()
    registry.glucoseSourceManager.glucoseSource = glucoseSource
    val insulinPump = plugin.getInsulinPump()
    pumpManager.insulinPump = insulinPump
}

fun getExtraNavGraphs(
    navViewModel: NavigationViewModel
): List<FeatureNavGraph> {
    val bodyModel = simBodyPlugin?.bodyModel
    return listOf(SimBodyNavGraph(navViewModel, bodyModel))
}