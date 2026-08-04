package de.dh.raaps.plugin.simbody.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.core.RAAPSRegistry
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.ui.components.SimBodyDashboardCard
import de.dh.raaps.plugin.simbody.ui.screens.SimBodyDetailScreen
import de.dh.raaps.plugin.simbody.ui.screens.SimBodyImpactsScreen
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel

/**
 * Navigation graph for the Sim-Body plugin.
 */
class SimBodyNavGraph(
    private val navViewModel: NavigationViewModel,
    private val raapsRegistry: RAAPSRegistry,
    private val bodyModel: BodyModel?
) : FeatureNavGraph {
    override fun getEntry(key: NavKey): NavEntry<NavKey>? {
        return when (key) {
            is SimBodyImpactsRoute -> NavEntry(key) {
                SimBodyImpactsScreen(
                    bodyModel = bodyModel,
                    onNavigateUp = { navViewModel.pop() }
                )
            }
            is SimBodyHistoryRoute -> NavEntry(key) {
                SimBodyDetailScreen(
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

        val therapyVM: CurrentTherapyViewModel = viewModel(
            factory = CurrentTherapyViewModel.Companion.Factory(raapsRegistry)
        )
        val therapyState by therapyVM.uiState.collectAsState()

        val therapyProfileName = therapyState.activeProfile.name
        val therapyBasal = therapyState.activeProfile.currentBasal
        val therapyIsf = therapyState.activeProfile.currentIsf.mgdl.toDouble()
        val therapyCr = therapyState.activeProfile.currentCr
        val therapyTarget = therapyState.activeProfile.target.mgdl.toDouble()
        val lowThreshold = therapyState.activeProfile.lowThreshold.mgdl.toDouble()

        Text(
            text = "Sim Body",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        SimBodyDashboardCard(
            bodyModel = bodyModel,
            onDetailsClick = { navViewModel.push(SimBodyImpactsRoute) },
            onHistoryClick = { navViewModel.push(SimBodyHistoryRoute) },
            treatmentProfileName = if (therapyProfileName.isNotEmpty()) therapyProfileName else null,
            treatmentBasal = therapyBasal,
            treatmentIsf = therapyIsf,
            treatmentCr = therapyCr,
            treatmentTarget = therapyTarget,
            treatmentLowThreshold = lowThreshold
        )
    }
}