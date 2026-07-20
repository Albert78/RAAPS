package de.dh.raaps.ui.screens.dashboard

import android.content.res.Configuration
import android.util.Range
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
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.ui.composables.WarningBanner
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.history.BgHistoryChartOrDefault
import de.dh.raaps.ui.controls.history.CurrentBgUiState
import de.dh.raaps.ui.controls.history.HistoryDiagramData
import de.dh.raaps.ui.controls.history.HistoryUiState
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.history.rememberBgHistoryChartState
import de.dh.raaps.ui.controls.profile.CurrentTherapyUiState
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
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
    onHistoryChartClick: () -> Unit,
    extraContent: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentBgUiState by historyViewModel.currentBgUiState.collectAsState()
    val historyUiState by historyViewModel.historyUiState.collectAsState()
    val currentTherapyUiState by currentTherapyViewModel.uiState.collectAsState()
    val permissionsUiState by permissionsViewModel.uiState.collectAsState()

    DashboardContent(
        dashboardUiState = uiState,
        currentBgUiState = currentBgUiState,
        historyUiState = historyUiState,
        currentTherapyUiState = currentTherapyUiState,
        permissionsUiState = permissionsUiState,
        onFixPermissionsClick = onFixPermissions,
        onNavigateToPermissions = onNavigateToPermissions,
        onNavigateToPreferences = onNavigateToPreferences,
        onNavigateToProfileEditor = onNavigateToProfileEditor,
        onHistoryChartClick = onHistoryChartClick,
        onProfileSelect = { currentTherapyViewModel.selectProfile(it) },
        onApsModeSelect = { viewModel.setApsMode(it) },
        extraContent = extraContent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    dashboardUiState: DashboardUiState,
    currentBgUiState: CurrentBgUiState,
    historyUiState: HistoryUiState,
    currentTherapyUiState: CurrentTherapyUiState,
    permissionsUiState: PermissionsUiModel,
    onFixPermissionsClick: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    onNavigateToProfileEditor: () -> Unit,
    onHistoryChartClick: (() -> Unit)?,
    onProfileSelect: (Profile) -> Unit,
    onApsModeSelect: (ApsMode) -> Unit,
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
                modifier = Modifier.fillMaxWidth(),
                carbsVisible = carbsVisible,
                onCarbsToggle = { carbsVisible = it },
                insulinVisible = insulinVisible,
                onInsulinToggle = { insulinVisible = it }
            )

            Text(
                text = stringResource(R.string.dashboard_glucose_title),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.padding(top = 15.dp))

            Box(
                modifier = Modifier.height(300.dp)
            ) {
                if (historyUiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val chartState = rememberBgHistoryChartState()

                    BgHistoryChartOrDefault(
                        diagramData = HistoryDiagramData.fromReadings(historyUiState.readings),
                        state = chartState,
                        onChartClick = onHistoryChartClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
            currentTherapyUiState = CurrentTherapyUiState(
                profileName = "Normal",
                currentIsf = BgDelta.fromMgDl(50),
                currentIc = 10.0,
                currentBasal = 0.5,
                currentTarget = Range(BgValue.fromMgDl(80), BgValue.fromMgDl(120))
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
            onHistoryChartClick = {},
            onProfileSelect = {},
            onApsModeSelect = {}
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
            currentTherapyUiState = CurrentTherapyUiState(
                profileName = "Normal",
                currentIsf = BgDelta.fromMgDl(50),
                currentIc = 10.0,
                currentBasal = 0.5,
                currentTarget = Range(BgValue.fromMgDl(80), BgValue.fromMgDl(120))
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
            onHistoryChartClick = {},
            onProfileSelect = {},
            onApsModeSelect = {}
        )
    }
}