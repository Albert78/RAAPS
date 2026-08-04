package de.dh.raaps

import android.app.Application
import androidx.activity.ComponentActivity
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.core.RAAPSRegistry
import de.dh.raaps.plugin.simbody.SimBodyPlugin
import de.dh.raaps.plugin.simbody.ui.SimBodyNavGraph

private var simBodyPlugin: SimBodyPlugin? = null

/**
 * Setup for a system where glucose source and insulin pump are provided by the [SimBodyPlugin],
 * which simulates real situations like food intake, sports, illness, stress etc.
 * With this plugin, we can interactively test our core calculation algorithms and the behavior
 * of the app in simulated, "real" situations.
 */
fun setupSystem(registry: RAAPSRegistry, pluginManager: PluginManager, application: Application) {
    val aps = registry.aps
    val plugin = SimBodyPlugin(application, registry.wakeService, registry.timeService)
    simBodyPlugin = plugin
    pluginManager.addPlugin(plugin)
    val glucoseSource = plugin.getGlucoseSource()
    aps.glucoseSource = glucoseSource
    val insulinPump = plugin.getInsulinPump()
    aps.pumpManager.insulinPump = insulinPump
}

fun getExtraNavGraphs(
    activity: ComponentActivity,
    navViewModel: NavigationViewModel,
    registry: RAAPSRegistry
): List<FeatureNavGraph> {
    val bodyModel = simBodyPlugin?.bodyModel
    return listOf(SimBodyNavGraph(navViewModel, registry, bodyModel))
}