package de.dh.raaps.plugin.simbody.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.ui.components.SimBodyDashboardCard
import de.dh.raaps.plugin.simbody.ui.screens.SimBodyDetailScreen

class SimBodyNavGraph(
    private val navViewModel: NavigationViewModel,
    private val bodyModel: BodyModel?
) : FeatureNavGraph {
    override fun getEntry(key: NavKey): NavEntry<NavKey>? {
        return when (key) {
            is SimBodyMainRoute -> NavEntry(key) {
                SimBodyDetailScreen(bodyModel)
            }
            else -> null
        }
    }

    @Composable
    override fun DashboardExtension() {
        if (bodyModel == null) return

        SimBodyDashboardCard(
            bodyModel = bodyModel,
            onDetailsClick = { navViewModel.push(SimBodyMainRoute) }
        )
    }
}
