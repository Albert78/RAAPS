package de.dh.raaps.ui.screens.dashboard

import android.content.res.Configuration
import android.util.Range
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.R
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.ui.composables.WarningBanner
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.targetRange
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.controls.history.BgHistoryChartOrDefault
import de.dh.raaps.ui.controls.history.CurrentBgUiState
import de.dh.raaps.ui.controls.history.CurrentBgView
import de.dh.raaps.ui.controls.history.DiagramData
import de.dh.raaps.ui.controls.history.HistoryUiState
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.history.createSampleGoodBgUiState
import de.dh.raaps.ui.controls.history.rememberBgHistoryChartState
import de.dh.raaps.ui.controls.profile.CurrentTherapyUiState
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.common.ui.composables.ProfileSelectionDialog
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
    onHistoryChartClick: () -> Unit
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
        onProfileSelect = { currentTherapyViewModel.selectProfile(it) }
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
    onProfileSelect: (Profile) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menuExpanded by remember { mutableStateOf(false) }

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
        }, floatingActionButton = {
//            if (!uiState.isLoading && !uiState.isError) {
//                ExtendedFloatingActionButton(
//                    onClick = onAddEventSeries,
//                    containerColor = MaterialTheme.colorScheme.primaryContainer,
//                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
//                ) {
//                    Icon(Icons.Default.Add, contentDescription = null)
//                    Spacer(Modifier.width(8.dp))
//                    Text(text = stringResource(R.string.add_button))
//                }
//            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Permissions warning header
                if (!permissionsUiState.isPermissionsConfigComplete) {
                    WarningBanner(
                        warningText = stringResource(id = R.string.dashboard_permissions_missing),
                        actionText = stringResource(id = R.string.dashboard_fix_permissions_link),
                        onActionClick = onFixPermissionsClick
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CurrentBgView(
                        currentBgUiState
                    )

                    var showProfileDialog by remember { mutableStateOf(false) }

                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .clickable { showProfileDialog = true }
                            .padding(8.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = currentTherapyUiState.profileName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            val targetStr = currentTherapyUiState.currentTarget?.let { targetRange(it, currentTherapyUiState.glucoseUnit) } ?: "-"
                            val unitStr = when(currentTherapyUiState.glucoseUnit) {
                                GlucoseUnit.MG_DL -> "mg/dL"
                                GlucoseUnit.MMOL -> "mmol/l"
                            }

                            Text(
                                text = "$targetStr $unitStr",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (showProfileDialog) {
                        ProfileSelectionDialog(
                            profiles = currentTherapyUiState.availableProfiles,
                            activeProfileId = currentTherapyUiState.activeProfileId,
                            onProfileSelected = {
                                onProfileSelect(it)
                                showProfileDialog = false
                            },
                            onDismiss = { showProfileDialog = false },
                            onEditProfilesClick = {
                                showProfileDialog = false
                                onNavigateToProfileEditor()
                            }
                        )
                    }
                }

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
                            diagramData = DiagramData.fromReadings(historyUiState.readings),
                            state = chartState,
                            onChartClick = onHistoryChartClick
                        )
                    }
                }
            }
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
            onProfileSelect = {}
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
            onProfileSelect = {}
        )
    }
}