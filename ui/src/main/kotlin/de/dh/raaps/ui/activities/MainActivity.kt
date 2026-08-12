package de.dh.raaps.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import de.dh.raaps.common.navigation.BolusHistoryRoute
import de.dh.raaps.common.navigation.DashboardRoute
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.FoodDatabaseRoute
import de.dh.raaps.common.navigation.MealsRoute
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.common.navigation.SystemControlRoute
import de.dh.raaps.common.navigation.combineEntryProviders
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.system.RegistryProvider
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.composables.EdgeToEdgeHandler
import de.dh.raaps.ui.common.icons.Icon_Menu_Bolus_History
import de.dh.raaps.ui.common.icons.Icon_Menu_Food_Database
import de.dh.raaps.ui.common.icons.Icon_Menu_Meals
import de.dh.raaps.ui.common.icons.Icon_Menu_System_Control
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.common.theme.rememberUseDarkTheme
import de.dh.raaps.ui.navigation.MainFeatureNavGraph
import kotlinx.coroutines.delay
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
        val currentRoute = backStack.lastOrNull()

        val extraGraphs = getExtraNavGraphs?.let { it(navViewModel) } ?: emptyList()

        val extraDashboardContent: @Composable () -> Unit = @Composable {
            extraGraphs.forEach { it.DashboardExtension() }
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current

        val mainGraph = MainFeatureNavGraph(
            activity = this,
            navViewModel = navViewModel,
            registry = registry,
            extraDashboardContent = extraDashboardContent
        )
        val allGraphs = listOf(mainGraph) + extraGraphs

        val combinedProvider = combineEntryProviders(*allGraphs.toTypedArray())

        val isTopLevel = currentRoute in listOf(
            DashboardRoute
        )

        var showHamburger by remember { mutableStateOf(isTopLevel) }
        LaunchedEffect(isTopLevel) {
            if (isTopLevel) {
                delay(400) // Delay to wait for screen transition
                showHamburger = true
            } else {
                showHamburger = false
            }
        }

        val drawerWidth = 320.dp
        val safeInsets = WindowInsets.safeDrawing.asPaddingValues(density)
        val verticalPadding = safeInsets.calculateBottomPadding()
        val statusBarHeight = safeInsets.calculateTopPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = isTopLevel,
                drawerContent = {
                    RaapsDrawerContent(
                        currentRoute = currentRoute,
                        onRouteSelected = { route ->
                            scope.launch { drawerState.close() }
                            navViewModel.push(route)
                        },
                        drawerWidth = drawerWidth,
                        statusBarHeight = statusBarHeight,
                        verticalPadding = verticalPadding
                    )
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

            if (showHamburger) {
                val rotation by animateFloatAsState(
                    targetValue = if (drawerState.targetValue == DrawerValue.Open) 90f else 0f,
                    label = "HamburgerRotation"
                )

                IconButton(
                    onClick = {
                        scope.launch {
                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                        }
                    },
                    modifier = Modifier
                        .safeDrawingPadding()
                        .padding(start = 8.dp, top = 8.dp)
                        .graphicsLayer {
                            rotationZ = rotation
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(id = R.string.cd_open_navigation_drawer)
                    )
                }
            }
        }
    }

    companion object {
        // Hack to transport extra nav graphs from MainApplication into MainActivity. Any better solution is welcome...
        var getExtraNavGraphs: ((navViewModel: NavigationViewModel) -> List<FeatureNavGraph>)? = null

        fun createStartDashboardIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = "app://raaps.dh.de/dashboard".toUri()
            }
        }
    }
}

@Composable
fun RaapsDrawerContent(
    currentRoute: NavKey?,
    onRouteSelected: (NavKey) -> Unit,
    drawerWidth: androidx.compose.ui.unit.Dp = 320.dp,
    statusBarHeight: androidx.compose.ui.unit.Dp = 0.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    ModalDrawerSheet(
        modifier = Modifier.width(drawerWidth),
        drawerContainerColor = Color.Transparent,
        drawerTonalElevation = 0.dp,
        windowInsets = WindowInsets(0.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarHeight + 2.dp, bottom = verticalPadding),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .padding(top = 50.dp)
                        .height(100.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(id = R.string.drawer_header_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                HorizontalDivider()

                RaapsDrawerItem(
                    label = stringResource(id = R.string.menu_meals_label),
                    icon = Icon_Menu_Meals,
                    selected = currentRoute == MealsRoute,
                    onClick = { onRouteSelected(MealsRoute) }
                )
                RaapsDrawerItem(
                    label = stringResource(id = R.string.menu_bolus_history_label),
                    icon = Icon_Menu_Bolus_History,
                    selected = currentRoute == BolusHistoryRoute,
                    onClick = { onRouteSelected(BolusHistoryRoute) }
                )
                RaapsDrawerItem(
                    label = stringResource(id = R.string.menu_food_database_label),
                    icon = Icon_Menu_Food_Database,
                    selected = currentRoute == FoodDatabaseRoute,
                    onClick = { onRouteSelected(FoodDatabaseRoute) }
                )
                RaapsDrawerItem(
                    label = stringResource(id = R.string.menu_system_control_label),
                    icon = Icon_Menu_System_Control,
                    selected = currentRoute is SystemControlRoute,
                    onClick = { onRouteSelected(SystemControlRoute()) }
                )
            }
        }
    }
}

@Composable
private fun RaapsDrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 320)
@Composable
fun RaapsDrawerPreview() {
    AppTheme {
        RaapsDrawerContent(
            currentRoute = DashboardRoute,
            onRouteSelected = {},
            statusBarHeight = 24.dp,
            verticalPadding = 16.dp
        )
    }
}
