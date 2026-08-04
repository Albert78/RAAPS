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
import androidx.compose.material.icons.filled.MoreVert
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
import de.dh.raaps.common.model.calculation.CarbsInsulinCalculationModel
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.navigation.CurrentTherapySettingsRoute
import de.dh.raaps.common.navigation.DashboardRoute
import de.dh.raaps.common.navigation.HistoryRoute
import de.dh.raaps.common.navigation.PermissionsRoute
import de.dh.raaps.common.navigation.PreferencesMainRoute
import de.dh.raaps.common.navigation.ProfileEditorRoute
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
import de.dh.raaps.ui.controls.profile.ProfileUiState
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
    onFixPermissions: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    onNavigateToProfileEditor: () -> Unit,
    onNavigateToTherapySettings: () -> Unit,
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
            currentValue = currentTherapyUiState.activeProfile.adjustmentPercentage,
            onValueChange = { currentTherapyViewModel.setAdjustmentPercentage(it) },
            onDismissRequest = { showAdjustmentDialog = false }
        )
    }

    DashboardContent(
        dashboardUiState = uiState,
        currentBgUiState = currentBgUiState,
        historyUiState = historyUiState,
        iob = iob,
        cob = cob,
        calculationModel = historyViewModel.calculationModel,
        currentTherapyUiState = currentTherapyUiState,
        permissionsUiState = permissionsUiState,
        onFixPermissionsClick = onFixPermissions,
        onNavigateToPermissions = onNavigateToPermissions,
        onNavigateToPreferences = onNavigateToPreferences,
        onNavigateToProfileEditor = onNavigateToProfileEditor,
        onNavigateToTherapySettings = onNavigateToTherapySettings,
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
    calculationModel: CarbsInsulinCalculationModel,
    currentTherapyUiState: CurrentTherapyUiState,
    permissionsUiState: PermissionsUiModel,
    onFixPermissionsClick: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    onNavigateToProfileEditor: () -> Unit,
    onNavigateToTherapySettings: () -> Unit,
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.dashboard_history_title),
                style = MaterialTheme.typography.bodyLarge
            )

            Box {
                if (historyUiState.isLoading) {
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
                            calculationModel = calculationModel,
                            dia = currentTherapyUiState.activeProfile.dia,
                            peak = currentTherapyUiState.activeProfile.peak
                        ),
                        state = chartState,
                        onChartClick = onHistoryChartClick,
                        showCarbs = carbsVisible,
                        showInsulin = insulinVisible
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ApsControlCard(
                modifier = Modifier.fillMaxWidth(),
                profileUiState = currentTherapyUiState.activeProfile,
                selectedMode = dashboardUiState.apsMode,
                availableModes = dashboardUiState.availableApsModes,
                onModeChange = onApsModeSelect,
                adjustmentPercentage = currentTherapyUiState.activeProfile.adjustmentPercentage,
                onAdjustmentClick = onAdjustmentClick,
                onProfileClick = onNavigateToTherapySettings
            )


            Spacer(modifier = Modifier.height(24.dp))

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
            calculationModel = CarbsInsulinCalculationModel(Minutes(5)),
            currentTherapyUiState = CurrentTherapyUiState(
                activeProfile = ProfileUiState(
                    name = "Normal",
                    activeProfileId = null,
                    isf = BgDelta.fromMgDl(50),
                    ic = 10.0,
                    basal = 0.5,
                    target = BgValue.fromMgDl(110),
                    lowThreshold = BgValue.fromMgDl(70),
                    adjustmentPercentage = 0,
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
            onFixPermissionsClick = {},
            onNavigateToPermissions = {},
            onNavigateToPreferences = {},
            onNavigateToProfileEditor = {},
            onNavigateToTherapySettings = {},
            onHistoryChartClick = {},
            onApsModeSelect = {},
            onAdjustmentClick = {}
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
            calculationModel = CarbsInsulinCalculationModel(Minutes(5)),
            currentTherapyUiState = CurrentTherapyUiState(
                activeProfile = ProfileUiState(
                    name = "Normal",
                    activeProfileId = null,
                    isf = BgDelta.fromMgDl(50),
                    ic = 10.0,
                    basal = 0.5,
                    target = BgValue.fromMgDl(110),
                    lowThreshold = BgValue.fromMgDl(70),
                    adjustmentPercentage = 0,
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
            onFixPermissionsClick = {},
            onNavigateToPermissions = {},
            onNavigateToPreferences = {},
            onNavigateToProfileEditor = {},
            onNavigateToTherapySettings = {},
            onHistoryChartClick = {},
            onApsModeSelect = {},
            onAdjustmentClick = {}
        )
    }
}