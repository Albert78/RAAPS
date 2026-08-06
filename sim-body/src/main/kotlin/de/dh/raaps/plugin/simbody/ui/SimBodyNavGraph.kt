package de.dh.raaps.plugin.simbody.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.ui.components.SimBodyDashboardCard
import de.dh.raaps.plugin.simbody.ui.screens.SimBodyHistoryScreen
import de.dh.raaps.plugin.simbody.ui.screens.SimBodyImpactsScreen
import de.dh.raaps.plugin.simbody.ui.screens.SimBodyMainScreen

/**
 * Navigation graph for the Sim-Body plugin.
 */
class SimBodyNavGraph(
    private val navViewModel: NavigationViewModel,
    private val bodyModel: BodyModel?
) : FeatureNavGraph {
    override fun getEntry(key: NavKey): NavEntry<NavKey>? {
        return when (key) {
            is SimBodyMainRoute -> NavEntry(key) {
                SimBodyMainScreen(
                    bodyModel = bodyModel,
                    onNavigateUp = { navViewModel.pop() },
                    onNavigateToImpacts = { navViewModel.push(SimBodyImpactsRoute) },
                    onNavigateToHistory = { navViewModel.push(SimBodyHistoryRoute) }
                )
            }
            is SimBodyImpactsRoute -> NavEntry(key) {
                SimBodyImpactsScreen(
                    bodyModel = bodyModel,
                    onNavigateUp = { navViewModel.pop() }
                )
            }
            is SimBodyHistoryRoute -> NavEntry(key) {
                SimBodyHistoryScreen(
                    bodyModel = bodyModel,
                    onNavigateUp = { navViewModel.pop() }
                )
            }
            else -> null
        }
    }

    @Composable
    override fun DashboardExtension() {
        if (bodyModel == null) return

        Text(
            text = "Sim Body",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        SimBodyDashboardCard(
            bodyModel = bodyModel,
            onDetailsClick = { navViewModel.push(SimBodyMainRoute) }
        )
    }
}