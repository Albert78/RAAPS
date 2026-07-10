package de.dh.raaps.plugin.simbody.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.core.RAAPSApplication
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.ui.components.SimBodyDashboardCard
import de.dh.raaps.plugin.simbody.ui.screens.SimBodyDetailScreen
import de.dh.raaps.plugin.simbody.ui.screens.SimBodyImpactsScreen
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel

class SimBodyNavGraph(
    private val navViewModel: NavigationViewModel,
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

        val context = LocalContext.current
        val raapsApp = context.applicationContext as? RAAPSApplication
        val app = context.applicationContext as android.app.Application

        var therapyProfileName: String? = null
        var therapyIsf: String? = null
        var therapyIc: String? = null
        var therapyTarget: String? = null

        if (raapsApp != null) {
            val therapyVM: CurrentTherapyViewModel = viewModel(factory = CurrentTherapyViewModel.Companion.Factory(app))
            val therapyState by therapyVM.uiState.collectAsState()

            therapyProfileName = therapyState.profileName
            therapyIsf = therapyState.currentIsf?.let { "${it.mgdl} mg/dL/U" }
            therapyIc = therapyState.currentIc?.let { "$it g/U" }
            therapyTarget = therapyState.currentTarget?.let { "${it.lower.mgdl} - ${it.upper.mgdl} mg/dL" }
        }

        SimBodyDashboardCard(
            bodyModel = bodyModel,
            onDetailsClick = { navViewModel.push(SimBodyImpactsRoute) },
            onHistoryClick = { navViewModel.push(SimBodyHistoryRoute) },
            treatmentProfileName = therapyProfileName,
            treatmentIsf = therapyIsf,
            treatmentIc = therapyIc,
            treatmentTarget = therapyTarget
        )
    }
}