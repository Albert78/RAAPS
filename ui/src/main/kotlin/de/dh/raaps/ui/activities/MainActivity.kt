package de.dh.raaps.ui.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import de.dh.raaps.common.navigation.AlarmsRoute
import de.dh.raaps.common.navigation.BolusHistoryRoute
import de.dh.raaps.common.navigation.DashboardRoute
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.MealsRoute
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.common.navigation.SystemControlRoute
import de.dh.raaps.common.navigation.combineEntryProviders
import de.dh.raaps.common.ui.composables.EdgeToEdgeHandler
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.rememberUseDarkTheme
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.system.RegistryProvider
import de.dh.raaps.ui.R
import de.dh.raaps.ui.navigation.MainFeatureNavGraph
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var navViewModel: NavigationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val registry = (application as RegistryProvider).registry

        // Start the background service
        startForegroundService(Intent(this, registry.apsServiceClass))

        navViewModel = ViewModelProvider(
            this,
            NavigationViewModel.Companion.NavigationViewModelFactory(listOf(DashboardRoute))
        )[NavigationViewModel::class.java]

        handleIntent(intent)

        setContent {
            val useDarkTheme = rememberUseDarkTheme(registry.appPreferencesRepository)
            EdgeToEdgeHandler(useDarkTheme)
            AppTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(registry)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data?.scheme == "app" && data.host == "raaps.dh.de") {
                if (data.path == "/dashboard") {
                    navViewModel.reset(listOf(DashboardRoute))
                }
            }
        }
    }

    @Composable
    fun MainApp(registry: SystemRegistry) {
        val backStack by navViewModel.backstack.collectAsState()
        val currentRoute = navViewModel.currentRoute

        val extraGraphs = getExtraNavGraphs?.let { it(navViewModel) } ?: emptyList()

        val extraDashboardContent: @Composable () -> Unit = @Composable {
            extraGraphs.forEach { it.DashboardExtension() }
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val mainGraph = MainFeatureNavGraph(
            activity = this,
            navViewModel = navViewModel,
            registry = registry,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            extraDashboardContent = extraDashboardContent
        )
        val allGraphs = listOf(mainGraph) + extraGraphs

        val combinedProvider = combineEntryProviders(*allGraphs.toTypedArray())

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(id = R.string.drawer_header_title),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    HorizontalDivider()

                    NavigationDrawerItem(
                        label = { Text(stringResource(id = R.string.menu_meals_label)) },
                        selected = currentRoute == MealsRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navViewModel.push(MealsRoute)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(id = R.string.menu_bolus_history_label)) },
                        selected = currentRoute == BolusHistoryRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navViewModel.push(BolusHistoryRoute)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(id = R.string.menu_system_control_label)) },
                        selected = currentRoute == SystemControlRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navViewModel.push(SystemControlRoute)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(id = R.string.menu_alarms_label)) },
                        selected = currentRoute == AlarmsRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navViewModel.push(AlarmsRoute)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { navViewModel.pop() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = combinedProvider
            )
        }
    }

    companion object {
        // Hack to transport extra nav graphs from MainApplication into MainActivity. Any better solution is welcome...
        var getExtraNavGraphs: ((navViewModel: NavigationViewModel) -> List<FeatureNavGraph>)? = null

        fun createStartDashboardIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("app://raaps.dh.de/dashboard")
            }
        }
    }
}