package de.dh.raaps.ui.screens.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.ui.composables.WarningBanner
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.apscontrol.ApsControlCard
import de.dh.raaps.ui.controls.dialogs.TherapyAdjustmentDialog
import de.dh.raaps.ui.controls.history.CurrentBgUiState
import de.dh.raaps.ui.controls.history.HistoryAndImpactChartOrDefault
import de.dh.raaps.ui.controls.history.HistoryAndImpactDiagramData
import de.dh.raaps.ui.controls.history.HistoryUiState
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.history.rememberBgHistoryChartState
import de.dh.raaps.ui.controls.profile.CurrentTherapyUiState
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.ui.controls.profile.InsulinProfileUiState
import de.dh.raaps.ui.controls.state.CurrentStateView
import de.dh.raaps.ui.controls.state.createSampleGoodBgUiState
import de.dh.raaps.ui.screens.history.createSampleHistoryUiState
import de.dh.raaps.ui.screens.permissions.PermissionStatus
import de.dh.raaps.ui.screens.permissions.PermissionsUiModel
import de.dh.raaps.ui.screens.permissions.PermissionsViewModel

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    historyViewModel: HistoryViewModel,
    currentTherapyViewModel: CurrentTherapyViewModel,
    permissionsViewModel: PermissionsViewModel,
    onOpenDrawer: () -> Unit,
    onFixPermissions: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    onNavigateToProfileEditor: () -> Unit,
    onNavigateToTherapySettings: () -> Unit,
    onNavigateToMealBolus: () -> Unit,
    onHistoryChartClick: () -> Unit,
    extraContent: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentBgUiState by historyViewModel.currentBgUiState.collectAsState()
    val historyUiState by historyViewModel.historyUiState.collectAsState()
    val iob by historyViewModel.iob.collectAsState()
    val cob by historyViewModel.cob.collectAsState()
    val currentTherapyUiState by currentTherapyViewModel.uiState.collectAsState()
    val permissionsUiState by permissionsViewModel.uiState.collectAsState()

    var showAdjustmentDialog by remember { mutableStateOf(false) }

    if (showAdjustmentDialog) {
        TherapyAdjustmentDialog(
            currentPercentage = currentTherapyUiState.activeInsulinProfile.insulinAdjustmentPercentage,
            currentTarget = currentTherapyUiState.activeInsulinProfile.targetBgOverride,
            currentLow = currentTherapyUiState.activeInsulinProfile.lowThresholdOverride,
            currentHint = currentTherapyUiState.activeInsulinProfile.adjustmentHint,
            presets = currentTherapyUiState.therapyAdjustmentPresets,
            onValuesChange = { p, t, l, h -> currentTherapyViewModel.setTherapyAdjustment(p, t, l, h) },
            onDismissRequest = { showAdjustmentDialog = false }
        )
    }

    DashboardContent(
        dashboardUiState = uiState,
        currentBgUiState = currentBgUiState,
        historyUiState = historyUiState,
        iob = iob,
        cob = cob,
        currentTherapyUiState = currentTherapyUiState,
        permissionsUiState = permissionsUiState,
        onOpenDrawer = onOpenDrawer,
        onFixPermissionsClick = onFixPermissions,
        onNavigateToPermissions = onNavigateToPermissions,
        onNavigateToPreferences = onNavigateToPreferences,
        onNavigateToProfileEditor = onNavigateToProfileEditor,
        onNavigateToTherapySettings = onNavigateToTherapySettings,
        onNavigateToMealBolus = onNavigateToMealBolus,
        onHistoryChartClick = onHistoryChartClick,
        onApsModeSelect = { viewModel.setApsMode(it) },
        onAdjustmentClick = { showAdjustmentDialog = true },
        extraContent = extraContent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    dashboardUiState: DashboardUiState,
    currentBgUiState: CurrentBgUiState,
    historyUiState: HistoryUiState,
    iob: Double,
    cob: Double,
    currentTherapyUiState: CurrentTherapyUiState,
    permissionsUiState: PermissionsUiModel,
    onOpenDrawer: () -> Unit,
    onFixPermissionsClick: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    onNavigateToProfileEditor: () -> Unit,
    onNavigateToTherapySettings: () -> Unit,
    onNavigateToMealBolus: () -> Unit,
    onHistoryChartClick: (() -> Unit)?,
    onApsModeSelect: (ApsMode) -> Unit,
    onAdjustmentClick: () -> Unit,
    extraContent: @Composable () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menuExpanded by remember { mutableStateOf(false) }

    var carbsVisible by remember { mutableStateOf(true) }
    var insulinVisible by remember { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.dashboard_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(id = R.string.cd_open_navigation_drawer)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(id = de.dh.raaps.common.R.string.cd_more_options))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.menu_item_permissions_label)) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToPermissions()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.menu_item_preferences_label)) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToPreferences()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.menu_item_profile_editor_label)) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToProfileEditor()
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Permissions warning header
            if (!permissionsUiState.isPermissionsConfigComplete) {
                WarningBanner(
                    warningText = stringResource(id = R.string.dashboard_permissions_missing),
                    actionText = stringResource(id = R.string.dashboard_fix_permissions_link),
                    onActionClick = onFixPermissionsClick
                )
            }

            CurrentStateView(
                currentBgUiState = currentBgUiState,
                iob = iob,
                cob = cob,
                modifier = Modifier.fillMaxWidth(),
                carbsVisible = carbsVisible,
                onCarbsToggle = { carbsVisible = it },
                insulinVisible = insulinVisible,
                onInsulinToggle = { insulinVisible = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.dashboard_history_title),
                style = MaterialTheme.typography.bodyLarge
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (historyUiState.isLoading || currentTherapyUiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val chartState = rememberBgHistoryChartState()

                    HistoryAndImpactChartOrDefault(
                        diagramData = HistoryAndImpactDiagramData.create(
                            readings = historyUiState.readings,
                            insulinApplications = historyUiState.insulinApplications,
                            meals = historyUiState.meals,
                            dia = currentTherapyUiState.activeInsulinProfile.dia,
                            peak = currentTherapyUiState.activeInsulinProfile.peak
                        ),
                        state = chartState,
                        onChartClick = onHistoryChartClick,
                        showCarbs = carbsVisible,
                        showInsulin = insulinVisible
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ApsControlCard(
                modifier = Modifier.fillMaxWidth(),
                insulinProfileUiState = currentTherapyUiState.activeInsulinProfile,
                selectedMode = dashboardUiState.apsMode,
                availableModes = dashboardUiState.availableApsModes,
                onModeChange = onApsModeSelect,
                insulinAdjustmentPercentage = currentTherapyUiState.activeInsulinProfile.insulinAdjustmentPercentage,
                adjustmentHint = currentTherapyUiState.activeInsulinProfile.adjustmentHint,
                onAdjustmentClick = onAdjustmentClick,
                onProfileClick = onNavigateToTherapySettings
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNavigateToMealBolus,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.dashboard_meal_bolus_button),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            extraContent()
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun DashboardPreview() {
    AppTheme {
        DashboardContent(
            dashboardUiState = DashboardUiState(isLoading = false, isError = false),
            currentBgUiState = createSampleGoodBgUiState(),
            historyUiState = createSampleHistoryUiState(),
            iob = 1.57,
            cob = 12.0,
            currentTherapyUiState = CurrentTherapyUiState(
                activeInsulinProfile = InsulinProfileUiState(
                    name = "Normal",
                    activeProfileId = null,
                    currentIsf = BgDelta.fromMgDl(50),
                    currentCr = 10.0,
                    currentBasal = 0.5,
                    isfRange = "50",
                    crRange = "10.0",
                    basalRange = "0.50",
                    target = BgValue.fromMgDl(110),
                    lowThreshold = BgValue.fromMgDl(70),
                    insulinAdjustmentPercentage = 0,
                    targetBgOverride = null,
                    lowThresholdOverride = null,
                    adjustmentHint = null,
                    dia = Minutes(300),
                    peak = Minutes(75)
                ),
            ),
            permissionsUiState = PermissionsUiModel(
                isLoading = false,
                notificationPermissionStatus = PermissionStatus.Granted,
                ignoreBatteryOptimizationPermissionStatus = PermissionStatus.Granted,
                autoRevokePermissionsPermissionStatus = PermissionStatus.Granted,
                numPermissionsMissing = 0,
                permissionsMissingText = ""
            ),
            onOpenDrawer = {},
            onFixPermissionsClick = {},
            onNavigateToPermissions = {},
            onNavigateToPreferences = {},
            onNavigateToProfileEditor = {},
            onNavigateToTherapySettings = {},
            onHistoryChartClick = {},
            onApsModeSelect = {},
            onAdjustmentClick = {},
            onNavigateToMealBolus = {}
        )
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun DashboardPermissionsWarningPreview() {
    AppTheme {
        DashboardContent(
            dashboardUiState = DashboardUiState(isLoading = false, isError = false),
            currentBgUiState = createSampleGoodBgUiState(),
            historyUiState = createSampleHistoryUiState(),
            iob = 1.57,
            cob = 12.0,
            currentTherapyUiState = CurrentTherapyUiState(
                activeInsulinProfile = InsulinProfileUiState(
                    name = "Normal",
                    activeProfileId = null,
                    currentIsf = BgDelta.fromMgDl(50),
                    currentCr = 10.0,
                    currentBasal = 0.5,
                    isfRange = "50",
                    crRange = "10.0",
                    basalRange = "0.50",
                    target = BgValue.fromMgDl(110),
                    lowThreshold = BgValue.fromMgDl(70),
                    insulinAdjustmentPercentage = 0,
                    targetBgOverride = null,
                    lowThresholdOverride = null,
                    adjustmentHint = null,
                    dia = Minutes(300),
                    peak = Minutes(75)
                ),
            ),
            permissionsUiState = PermissionsUiModel(
                isLoading = false,
                notificationPermissionStatus = PermissionStatus.Denied,
                ignoreBatteryOptimizationPermissionStatus = PermissionStatus.Granted,
                autoRevokePermissionsPermissionStatus = PermissionStatus.Granted,
                numPermissionsMissing = 1,
                permissionsMissingText = "1 permission missing"
            ),
            onOpenDrawer = {},
            onFixPermissionsClick = {},
            onNavigateToPermissions = {},
            onNavigateToPreferences = {},
            onNavigateToProfileEditor = {},
            onNavigateToTherapySettings = {},
            onHistoryChartClick = {},
            onApsModeSelect = {},
            onAdjustmentClick = {},
            onNavigateToMealBolus = {}
        )
    }
}