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
import de.dh.raaps.common.navigation.AlarmsRoute
import de.dh.raaps.common.navigation.AlgorithmDecisionsRoute
import de.dh.raaps.common.navigation.BgEditorRoute
import de.dh.raaps.common.navigation.BolusHistoryRoute
import de.dh.raaps.common.navigation.CurrentTherapySettingsRoute
import de.dh.raaps.common.navigation.DashboardRoute
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.FoodDatabaseRoute
import de.dh.raaps.common.navigation.HistoryRoute
import de.dh.raaps.common.navigation.InsulinProfileEditorRoute
import de.dh.raaps.common.navigation.MealBolusRoute
import de.dh.raaps.common.navigation.MealTypeEditorRoute
import de.dh.raaps.common.navigation.MealTypesRoute
import de.dh.raaps.common.navigation.MealsRoute
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.common.navigation.PermissionsRoute
import de.dh.raaps.common.navigation.PreferencesMainRoute
import de.dh.raaps.common.navigation.PumpManagementRoute
import de.dh.raaps.common.navigation.SystemControlRoute
import de.dh.raaps.common.navigation.TherapyAdjustmentRoute
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.setUserDeclinedPermissions
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.ui.controls.profile.InsulinProfileSettingsViewModel
import de.dh.raaps.ui.screens.alarms.AlarmsScreen
import de.dh.raaps.ui.screens.bolushistory.BolusHistoryScreen
import de.dh.raaps.ui.screens.bolushistory.BolusHistoryViewModel
import de.dh.raaps.ui.screens.dashboard.DashboardScreen
import de.dh.raaps.ui.screens.dashboard.DashboardViewModel
import de.dh.raaps.ui.screens.fooddatabase.FoodDatabaseScreen
import de.dh.raaps.ui.screens.history.HistoryScreen
import de.dh.raaps.ui.screens.insulinprofile.InsulinProfileEditorScreen
import de.dh.raaps.ui.screens.mealbolus.MealBolusScreen
import de.dh.raaps.ui.screens.mealbolus.MealBolusViewModel
import de.dh.raaps.ui.screens.meals.MealTypeEditorScreen
import de.dh.raaps.ui.screens.meals.MealTypeEditorViewModel
import de.dh.raaps.ui.screens.meals.MealTypesScreen
import de.dh.raaps.ui.screens.meals.MealTypesViewModel
import de.dh.raaps.ui.screens.meals.MealsScreen
import de.dh.raaps.ui.screens.meals.MealsViewModel
import de.dh.raaps.ui.screens.permissions.PermissionsScreen
import de.dh.raaps.ui.screens.permissions.PermissionsViewModel
import de.dh.raaps.ui.screens.permissions.isPermissionsMissing
import de.dh.raaps.ui.screens.permissions.openAutoRevokeSettings
import de.dh.raaps.ui.screens.permissions.openNotificationSettings
import de.dh.raaps.ui.screens.permissions.requestIgnoreBatteryOptimizations
import de.dh.raaps.ui.screens.preferences.PreferencesScreen
import de.dh.raaps.ui.screens.preferences.PreferencesViewModel
import de.dh.raaps.ui.screens.systemcontrol.AlgorithmDecisionsScreen
import de.dh.raaps.ui.screens.systemcontrol.PumpManagementScreen
import de.dh.raaps.ui.screens.systemcontrol.SystemControlScreen
import de.dh.raaps.ui.screens.systemcontrol.SystemControlViewModel
import de.dh.raaps.ui.screens.therapy.BgEditorScreen
import de.dh.raaps.ui.screens.therapy.CurrentTherapySettingsScreen
import de.dh.raaps.ui.screens.therapy.TherapyAdjustmentScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFeatureNavGraph(
    private val activity: ComponentActivity,
    private val navViewModel: NavigationViewModel,
    private val registry: SystemRegistry,
    private val extraDashboardContent: @Composable () -> Unit = {}
) : FeatureNavGraph {
    override fun getEntry(key: NavKey): NavEntry<NavKey>? {
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
                    onNavigateToAlarms = { navViewModel.push(AlarmsRoute) },
                    onNavigateToTherapySettings = { navViewModel.push(CurrentTherapySettingsRoute) },
                    onNavigateToMealBolus = { navViewModel.push(MealBolusRoute()) },
                    onAdjustmentClick = { navViewModel.push(TherapyAdjustmentRoute) },
                    onHistoryChartClick = { navViewModel.push(HistoryRoute) },
                    extraContent = extraDashboardContent
                )
            }

            is InsulinProfileEditorRoute -> NavEntry(key) {
                val vm: InsulinProfileSettingsViewModel = viewModel(
                    factory = InsulinProfileSettingsViewModel.Companion.Factory(registry)
                )

                InsulinProfileEditorScreen(
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
                    onNavigateToInsulinProfileEditor = { navViewModel.push(InsulinProfileEditorRoute) },
                    onNavigateToBgEditor = { navViewModel.push(BgEditorRoute) },
                    onNavigateToTherapyAdjustment = { navViewModel.push(TherapyAdjustmentRoute) }
                )
            }

            is BgEditorRoute -> NavEntry(key) {
                val currentTherapyVM: CurrentTherapyViewModel =
                    viewModel(factory = CurrentTherapyViewModel.Companion.Factory(registry))

                BgEditorScreen(
                    viewModel = currentTherapyVM,
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is TherapyAdjustmentRoute -> NavEntry(key) {
                val currentTherapyVM: CurrentTherapyViewModel =
                    viewModel(factory = CurrentTherapyViewModel.Companion.Factory(registry))

                TherapyAdjustmentScreen(
                    viewModel = currentTherapyVM,
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is HistoryRoute -> NavEntry(key) {
                val historyVM: HistoryViewModel =
                    viewModel(factory = HistoryViewModel.Companion.Factory(registry))

                HistoryScreen(
                    historyViewModel = historyVM
                )
            }

            is MealBolusRoute -> NavEntry(key) {
                val vm: MealBolusViewModel = viewModel(
                    factory = MealBolusViewModel.Companion.Factory(registry, key.mealId)
                )
                val historyVM: HistoryViewModel =
                    viewModel(factory = HistoryViewModel.Companion.Factory(registry))

                MealBolusScreen(
                    viewModel = vm,
                    historyViewModel = historyVM,
                    onNavigateUp = { navViewModel.pop() }
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

            is MealsRoute -> NavEntry(key) {
                val vm: MealsViewModel = viewModel(
                    factory = MealsViewModel.Companion.Factory(registry)
                )
                MealsScreen(
                    viewModel = vm,
                    onNavigateToMealTypes = { navViewModel.push(MealTypesRoute) },
                    onNavigateToMealBolus = { navViewModel.push(MealBolusRoute()) },
                    onEditMeal = { meal -> navViewModel.push(MealBolusRoute(mealId = meal.id)) },
                    onDeleteMeal = { vm.deleteMeal(it) },
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is MealTypesRoute -> NavEntry(key) {
                val vm: MealTypesViewModel = viewModel(
                    factory = MealTypesViewModel.Companion.Factory(registry)
                )
                MealTypesScreen(
                    viewModel = vm,
                    onNavigateToEditor = { id -> navViewModel.push(MealTypeEditorRoute(mealTypeId = id)) },
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is MealTypeEditorRoute -> NavEntry(key) {
                val vm: MealTypeEditorViewModel = viewModel(
                    factory = MealTypeEditorViewModel.Companion.Factory(registry, key.mealTypeId)
                )
                MealTypeEditorScreen(
                    viewModel = vm,
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is BolusHistoryRoute -> NavEntry(key) {
                val vm: BolusHistoryViewModel = viewModel(
                    factory = BolusHistoryViewModel.Companion.Factory(registry)
                )
                BolusHistoryScreen(
                    viewModel = vm,
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is FoodDatabaseRoute -> NavEntry(key) {
                FoodDatabaseScreen(onNavigateUp = { navViewModel.pop() })
            }

            is SystemControlRoute -> NavEntry(key) {
                val vm: SystemControlViewModel = viewModel(
                    factory = SystemControlViewModel.Companion.Factory(registry)
                )
                SystemControlScreen(
                    viewModel = vm,
                    onNavigateUp = { navViewModel.pop() },
                    onNavigateToAlgorithmDecisions = { navViewModel.push(AlgorithmDecisionsRoute) },
                    onNavigateToPumpManagement = { navViewModel.push(PumpManagementRoute) }
                )
            }

            is PumpManagementRoute -> NavEntry(key) {
                PumpManagementScreen(
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is AlgorithmDecisionsRoute -> NavEntry(key) {
                val vm: SystemControlViewModel = viewModel(
                    factory = SystemControlViewModel.Companion.Factory(registry)
                )
                AlgorithmDecisionsScreen(
                    viewModel = vm,
                    onNavigateUp = { navViewModel.pop() }
                )
            }

            is AlarmsRoute -> NavEntry(key) {
                AlarmsScreen(onNavigateUp = { navViewModel.pop() })
            }

            else -> null
        }
    }
}
