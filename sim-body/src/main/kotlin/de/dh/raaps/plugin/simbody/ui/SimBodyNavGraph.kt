package de.dh.raaps.plugin.simbody.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
    private fun SimBodyDetailScreen(bodyModel: BodyModel?) {
        if (bodyModel == null) {
            Text("Body Model not available")
            return
        }

        Scaffold(
            topBar = {
                Text(
                    "Sim Body Detailed Controls",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                item {
                    Text("Historical Inputs", style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                item {
                    Text("Meals", style = MaterialTheme.typography.titleMedium)
                }
                items(bodyModel.meals) { meal ->
                    Text("${meal.carbGrams}g at ${meal.timestamp}")
                }

                item {
                    Text("Insulin", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                }
                items(bodyModel.insulinApplications) { insulin ->
                    Text("${insulin.amount}U at ${insulin.timestamp} (${insulin.origin})")
                }

                item {
                    Button(
                        onClick = {
                            bodyModel.meals.clear()
                            bodyModel.insulinApplications.clear()
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Clear History")
                    }
                }
            }
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
