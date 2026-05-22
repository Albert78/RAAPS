package de.dh.raaps.ui.screens.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import de.dh.raaps.MainApplication
import de.dh.raaps.common.ui.composables.EdgeToEdgeHandler
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.rememberUseDarkTheme
import de.dh.raaps.services.ApsService
import de.dh.raaps.setUserDeclinedPermissions
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.screens.dashboard.DashboardScreen
import de.dh.raaps.ui.screens.dashboard.DashboardViewModel
import de.dh.raaps.ui.screens.history.HistoryScreen
import de.dh.raaps.ui.screens.permissions.PermissionsScreen
import de.dh.raaps.ui.screens.permissions.PermissionsViewModel
import de.dh.raaps.ui.screens.permissions.canPostNotifications
import de.dh.raaps.ui.screens.permissions.isAutoRevokePermissions
import de.dh.raaps.ui.screens.permissions.openAutoRevokeSettings
import de.dh.raaps.ui.screens.permissions.openNotificationSettings
import de.dh.raaps.ui.screens.permissions.requestIgnoreBatteryOptimizations
import de.dh.raaps.ui.screens.preferences.PreferencesScreen
import de.dh.raaps.ui.screens.preferences.PreferencesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// --- Navigation Routes

@Serializable object DashboardRoute : NavKey

@Serializable object HistoryRoute : NavKey

@Serializable object PermissionsRoute : NavKey

@Serializable object PreferencesMainRoute : NavKey

class MainActivity : ComponentActivity() {
    private lateinit var navViewModel: NavigationViewModel
    private lateinit var permissionsViewModel: PermissionsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the background service
        startForegroundService(Intent(this, ApsService::class.java))

        navViewModel = ViewModelProvider(
            this,
            NavigationViewModel.Companion.NavigationViewModelFactory(listOf(DashboardRoute))
        )[NavigationViewModel::class.java]

        handleIntent(intent)

        val application = application as MainApplication

        permissionsViewModel = ViewModelProvider(
            this,
            PermissionsViewModel.Companion.Factory(application)
        )[PermissionsViewModel::class.java]

        setContent {
            val useDarkTheme = rememberUseDarkTheme(application.mAppPreferencesRepository)
            EdgeToEdgeHandler(useDarkTheme)
            AppTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
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
    fun MainApp() {
        val application = application as MainApplication

        val backStack by navViewModel.backstack.collectAsState()

        NavDisplay(
            backStack = backStack,
            onBack = { navViewModel.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<DashboardRoute> { _ ->
                    val vm: DashboardViewModel =
                        viewModel(factory = DashboardViewModel.Companion.Factory(application))
                    val historyVM: HistoryViewModel =
                        viewModel(factory = HistoryViewModel.Companion.Factory(application))
                    val permissionsViewModel: PermissionsViewModel =
                        viewModel(factory = PermissionsViewModel.Companion.Factory(application))

                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        vm.reload()
                        permissionsViewModel.updateAppPermissions()
                    }

                    DashboardScreen(
                        viewModel = vm,
                        historyViewModel = historyVM,
                        permissionsViewModel = permissionsViewModel,
                        onFixPermissions = { navViewModel.push(PermissionsRoute) },
                        onNavigateToPermissions = { navViewModel.push(PermissionsRoute) },
                        onNavigateToPreferences = { navViewModel.push(PreferencesMainRoute) },
                        onHistoryChartClick = {navViewModel.push(HistoryRoute)}
                    )
                }

                entry<HistoryRoute> { _ ->
                    val historyVM: HistoryViewModel =
                        viewModel(factory = HistoryViewModel.Companion.Factory(application))

                    HistoryScreen(
                        historyViewModel = historyVM
                    )
                }

                entry<PermissionsRoute> { _ ->
                    permissionsViewModel.updateAppPermissions()

                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        permissionsViewModel.updateAppPermissions()

                        lifecycleScope.launch(Dispatchers.IO) {
                            application.triggerUpdatesAfterPermissionsChange()
                        }
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val userDeclinedPermissions = isPermissionsMissing(this@MainActivity)
                                application.mAppPreferencesRepository.setUserDeclinedPermissions(userDeclinedPermissions)

                                // Not really the right place to trigger the update but there is no
                                // better place.
                                // The user could also close the app after he changed the system permissions and avoid
                                // to come back to this screen, which then will not trigger the effect.
                                application.triggerUpdatesAfterPermissionsChange()
                            }
                        }
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { _ ->
                        permissionsViewModel.updateAppPermissions()
                    }

                    PermissionsScreen(
                        viewModel = permissionsViewModel,
                        onNavigateUp = { navViewModel.pop() },
                        onOpenNotificationSettings = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openNotificationSettings(this@MainActivity)
                            }
                        },
                        onOpenBatteryOptimizationSettings = { requestIgnoreBatteryOptimizations(this@MainActivity) },
                        onOpenAutoRevokeSettings = { openAutoRevokeSettings(this@MainActivity) }
                    )
                }

                entry<PreferencesMainRoute> { _ ->
                    val vm: PreferencesViewModel = viewModel(
                        factory = PreferencesViewModel.Companion.Factory(
                            application
                        )
                    )

                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        vm.reload()
                    }

                    PreferencesScreen(
                        viewModel = vm,
                        onNavigateUp = { navViewModel.pop() }
                    )
                }
            }
        )
    }

    companion object {
        fun createStartDashboardIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("app://raaps.dh.de/dashboard")
            }
        }

        /**
         * Use this to check if it is necessary to open permissions.
         */
        fun isPermissionsMissing(context: Context): Boolean {
            return !canPostNotifications(context) ||
                    isAutoRevokePermissions(context)
        }
    }
}