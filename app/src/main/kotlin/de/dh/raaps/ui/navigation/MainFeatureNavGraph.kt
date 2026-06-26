package de.dh.raaps.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.dh.raaps.MainApplication
import de.dh.raaps.common.navigation.DashboardRoute
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.HistoryRoute
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.common.navigation.PermissionsRoute
import de.dh.raaps.common.navigation.PreferencesMainRoute
import de.dh.raaps.common.navigation.ProfileEditorRoute
import de.dh.raaps.core.RAAPSApplication
import de.dh.raaps.setUserDeclinedPermissions
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.ui.controls.profile.ProfileSettingsViewModel
import de.dh.raaps.ui.screens.common.MainActivity
import de.dh.raaps.ui.screens.dashboard.DashboardScreen
import de.dh.raaps.ui.screens.dashboard.DashboardViewModel
import de.dh.raaps.ui.screens.history.HistoryScreen
import de.dh.raaps.ui.screens.permissions.*
import de.dh.raaps.ui.screens.preferences.PreferencesScreen
import de.dh.raaps.ui.screens.preferences.PreferencesViewModel
import de.dh.raaps.ui.screens.profile.ProfileEditorScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFeatureNavGraph(
    private val activity: ComponentActivity,
    private val navViewModel: NavigationViewModel,
    private val extraDashboardContent: @Composable () -> Unit = {}
) : FeatureNavGraph {
    override fun getEntry(key: NavKey): NavEntry<NavKey>? {
        val raapsApp = activity.application as RAAPSApplication

        return when (key) {
            is DashboardRoute -> NavEntry(key) {
                val vm: DashboardViewModel =
                    viewModel(factory = DashboardViewModel.Companion.Factory(raapsApp))
                val historyVM: HistoryViewModel =
                    viewModel(factory = HistoryViewModel.Companion.Factory(raapsApp))
                val currentTherapyVM: CurrentTherapyViewModel =
                    viewModel(factory = CurrentTherapyViewModel.Companion.Factory(raapsApp))
                val permissionsViewModel: PermissionsViewModel =
                    viewModel(factory = PermissionsViewModel.Companion.Factory(raapsApp))

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    vm.reload()
                    currentTherapyVM.loadData()
                    permissionsViewModel.updateAppPermissions()
                }

                DashboardScreen(
                    viewModel = vm,
                    historyViewModel = historyVM,
                    currentTherapyViewModel = currentTherapyVM,
                    permissionsViewModel = permissionsViewModel,
                    onFixPermissions = { navViewModel.push(PermissionsRoute) },
                    onNavigateToPermissions = { navViewModel.push(PermissionsRoute) },
                    onNavigateToPreferences = { navViewModel.push(PreferencesMainRoute) },
                    onNavigateToProfileEditor = { navViewModel.push(ProfileEditorRoute) },
                    onHistoryChartClick = { navViewModel.push(HistoryRoute) },
                    extraContent = extraDashboardContent
                )
            }

            is ProfileEditorRoute -> NavEntry(key) {
                val vm: ProfileSettingsViewModel = viewModel(
                    factory = ProfileSettingsViewModel.Companion.Factory(
                        raapsApp
                    )
                )

                ProfileEditorScreen(
                    viewModel = vm,
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is HistoryRoute -> NavEntry(key) {
                val historyVM: HistoryViewModel =
                    viewModel(factory = HistoryViewModel.Companion.Factory(raapsApp))

                HistoryScreen(
                    historyViewModel = historyVM
                )
            }

            is PermissionsRoute -> NavEntry(key) {
                val permissionsViewModel: PermissionsViewModel =
                    viewModel(factory = PermissionsViewModel.Companion.Factory(raapsApp))

                permissionsViewModel.updateAppPermissions()

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    permissionsViewModel.updateAppPermissions()

                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        raapsApp.triggerUpdatesAfterPermissionsChange()
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            val userDeclinedPermissions = MainActivity.isPermissionsMissing(activity)
                            raapsApp.appPreferencesRepository.setUserDeclinedPermissions(userDeclinedPermissions)
                            raapsApp.triggerUpdatesAfterPermissionsChange()
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
                            openNotificationSettings(activity)
                        }
                    },
                    onOpenBatteryOptimizationSettings = { requestIgnoreBatteryOptimizations(activity) },
                    onOpenAutoRevokeSettings = { openAutoRevokeSettings(activity) }
                )
            }

            is PreferencesMainRoute -> NavEntry(key) {
                val vm: PreferencesViewModel = viewModel(
                    factory = PreferencesViewModel.Companion.Factory(
                        raapsApp
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

            else -> null
        }
    }
}