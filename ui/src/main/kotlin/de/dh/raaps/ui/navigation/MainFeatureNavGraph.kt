package de.dh.raaps.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.dh.raaps.common.navigation.CurrentTherapySettingsRoute
import de.dh.raaps.common.navigation.DashboardRoute
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.HistoryRoute
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.common.navigation.PermissionsRoute
import de.dh.raaps.common.navigation.PreferencesMainRoute
import de.dh.raaps.common.navigation.ProfileEditorRoute
import de.dh.raaps.core.RAAPSRegistry
import de.dh.raaps.setUserDeclinedPermissions
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.ui.controls.profile.ProfileSettingsViewModel
import de.dh.raaps.ui.screens.dashboard.DashboardScreen
import de.dh.raaps.ui.screens.dashboard.DashboardViewModel
import de.dh.raaps.ui.screens.history.HistoryScreen
import de.dh.raaps.ui.screens.permissions.PermissionsScreen
import de.dh.raaps.ui.screens.permissions.PermissionsViewModel
import de.dh.raaps.ui.screens.permissions.isPermissionsMissing
import de.dh.raaps.ui.screens.permissions.openAutoRevokeSettings
import de.dh.raaps.ui.screens.permissions.openNotificationSettings
import de.dh.raaps.ui.screens.permissions.requestIgnoreBatteryOptimizations
import de.dh.raaps.ui.screens.preferences.PreferencesScreen
import de.dh.raaps.ui.screens.preferences.PreferencesViewModel
import de.dh.raaps.ui.screens.profile.ProfileEditorScreen
import de.dh.raaps.ui.screens.therapy.CurrentTherapySettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFeatureNavGraph(
    private val activity: ComponentActivity,
    private val navViewModel: NavigationViewModel,
    private val registry: RAAPSRegistry,
    private val extraDashboardContent: @Composable () -> Unit = {}
) : FeatureNavGraph {
    override fun getEntry(key: NavKey): NavEntry<NavKey>? {
        val application = activity.application

        return when (key) {
            is DashboardRoute -> NavEntry(key) {
                val vm: DashboardViewModel =
                    viewModel(factory = DashboardViewModel.Companion.Factory(registry))
                val historyVM: HistoryViewModel =
                    viewModel(factory = HistoryViewModel.Companion.Factory(registry))
                val currentTherapyVM: CurrentTherapyViewModel =
                    viewModel(factory = CurrentTherapyViewModel.Companion.Factory(registry))
                val permissionsViewModel: PermissionsViewModel =
                    viewModel(factory = PermissionsViewModel.Companion.Factory(registry))

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    vm.reload()
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
                    onNavigateToTherapySettings = { navViewModel.push(CurrentTherapySettingsRoute) },
                    onHistoryChartClick = { navViewModel.push(HistoryRoute) },
                    extraContent = extraDashboardContent
                )
            }

            is ProfileEditorRoute -> NavEntry(key) {
                val vm: ProfileSettingsViewModel = viewModel(
                    factory = ProfileSettingsViewModel.Companion.Factory(registry)
                )

                ProfileEditorScreen(
                    viewModel = vm,
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is CurrentTherapySettingsRoute -> NavEntry(key) {
                val currentTherapyVM: CurrentTherapyViewModel =
                    viewModel(factory = CurrentTherapyViewModel.Companion.Factory(registry))

                CurrentTherapySettingsScreen(
                    viewModel = currentTherapyVM,
                    onNavigateUp = { navViewModel.pop() },
                    onNavigateToProfileEditor = { navViewModel.push(ProfileEditorRoute) }
                )
            }

            is HistoryRoute -> NavEntry(key) {
                val historyVM: HistoryViewModel =
                    viewModel(factory = HistoryViewModel.Companion.Factory(registry))

                HistoryScreen(
                    historyViewModel = historyVM
                )
            }

            is PermissionsRoute -> NavEntry(key) {
                val permissionsViewModel: PermissionsViewModel =
                    viewModel(factory = PermissionsViewModel.Companion.Factory(registry))

                permissionsViewModel.updateAppPermissions()

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    permissionsViewModel.updateAppPermissions()

                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        registry.permissionsChangedHandler.onPermissionsChanged()
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            val userDeclinedPermissions = isPermissionsMissing(activity)
                            registry.appPreferencesRepository.setUserDeclinedPermissions(userDeclinedPermissions)
                            registry.permissionsChangedHandler.onPermissionsChanged()
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
                    factory = PreferencesViewModel.Companion.Factory(registry)
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