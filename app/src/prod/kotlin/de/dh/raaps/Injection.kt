package de.dh.raaps

import android.app.Application
import androidx.activity.ComponentActivity
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.core.RAAPSRegistry
import de.dh.raaps.plugin.glucose.receiver.ExternalSourceType
import de.dh.raaps.plugin.glucose.receiver.ReceiverGlucosePlugin
import de.dh.raaps.plugin.pump.SampleInsulinPumpPlugin

fun setupSystem(registry: RAAPSRegistry, pluginManager: PluginManager, application: Application) {
    val aps = registry.aps
    val glucosePlugin = ReceiverGlucosePlugin(
        application,
        ExternalSourceType.xDrip5Min
    )
    pluginManager.addPlugin(glucosePlugin)
    aps.glucoseSource = glucosePlugin
    val pumpPlugin = SampleInsulinPumpPlugin()
    aps.pumpManager.insulinPump = pumpPlugin
}

fun getExtraNavGraphs(
    navViewModel: NavigationViewModel
): List<FeatureNavGraph> {
    return emptyList()
}