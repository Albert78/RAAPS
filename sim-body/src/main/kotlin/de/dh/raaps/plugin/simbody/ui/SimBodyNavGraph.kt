package de.dh.raaps.plugin.simbody.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.SimBodyMainRoute
import de.dh.raaps.ui.navigation.NavigationViewModel

class SimBodyNavGraph(
    private val navViewModel: NavigationViewModel
) : FeatureNavGraph {
    override fun getEntry(key: NavKey): NavEntry<NavKey>? {
        return when (key) {
            is SimBodyMainRoute -> NavEntry(key) {
                // Placeholder screen for sim settings
                Text("Sim Body Settings / Controls")
            }
            else -> null
        }
    }

    @Composable
    override fun DashboardExtension() {
        Button(
            onClick = { navViewModel.push(SimBodyMainRoute) },
            modifier = Modifier.padding(8.dp)
        ) {
            Text("Open Sim Body Controls")
        }
    }
}