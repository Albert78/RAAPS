package de.dh.raaps.ui.screens.therapy

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.ui.composables.ProfileSelectionDialog
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.dialogs.BgEditorDialog
import de.dh.raaps.ui.controls.profile.CurrentTherapyUiState
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.ui.controls.profile.ProfileUiState

@Composable
fun CurrentTherapySettingsScreen(
    viewModel: CurrentTherapyViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToProfileEditor: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    CurrentTherapySettingsContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onNavigateToProfileEditor = onNavigateToProfileEditor,
        onSelectProfile = { viewModel.selectInsulinProfile(it) },
        onUpdateDefaultBgBlocks = { viewModel.updateDefaultBgBlocks(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentTherapySettingsContent(
    uiState: CurrentTherapyUiState,
    onNavigateUp: () -> Unit,
    onNavigateToProfileEditor: () -> Unit,
    onSelectProfile: (InsulinProfile) -> Unit,
    onUpdateDefaultBgBlocks: (List<BgBlock>) -> Unit
) {
    val locale = LocalLocale.current.platformLocale
    var showProfileDialog by remember { mutableStateOf(false) }
    var showBgEditorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.current_therapy_settings_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.cd_navigate_up)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToProfileEditor) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(id = R.string.menu_item_profile_editor_label)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = stringResource(id = R.string.current_therapy_active_insulin_profile_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                val activeProfile = uiState.activeProfile
                item {
                    ListItem(
                        headlineContent = { Text(activeProfile.name) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    id = R.string.aps_control_basal_label,
                                    String.format(locale, "%.1f", activeProfile.basal)
                                ) + " | " +
                                        stringResource(
                                            id = R.string.aps_control_cr_label,
                                            String.format(locale, "%.1f", activeProfile.cr)
                                        ) + " | " +
                                        stringResource(
                                            id = R.string.aps_control_isf_label,
                                            activeProfile.isf.mgdl
                                        )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProfileDialog = true }
                    )
                }

                item {
                    Text(
                        text = stringResource(id = R.string.current_therapy_bg_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    val bgBlocks = uiState.defaultBgBlocks
                    val targets = bgBlocks.map { it.target.mgdl }.distinct()
                    val lows = bgBlocks.map { it.lowThreshold.mgdl }.distinct()

                    val targetLabel = if (targets.size == 1) {
                        stringResource(R.string.current_therapy_target_label_singular)
                    } else {
                        stringResource(R.string.current_therapy_target_label_plural)
                    }
                    val targetValue = if (targets.size == 1) {
                        "${targets.first()} mg/dL"
                    } else {
                        "${targets.minOrNull()}–${targets.maxOrNull()} mg/dL"
                    }

                    val lowLabel = if (lows.size == 1) {
                        stringResource(R.string.current_therapy_low_threshold_label_singular)
                    } else {
                        stringResource(R.string.current_therapy_low_threshold_label_plural)
                    }
                    val lowValue = if (lows.size == 1) {
                        "${lows.first()} mg/dL"
                    } else {
                        "${lows.minOrNull()}–${lows.maxOrNull()} mg/dL"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBgEditorDialog = true }
                            .padding(16.dp)
                    ) {
                        Text(text = "$targetLabel: $targetValue", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "$lowLabel: $lowValue", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileSelectionDialog(
            profiles = uiState.availableProfiles,
            activeProfileId = uiState.activeProfile?.activeProfileId,
            onProfileSelected = {
                onSelectProfile(it)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    if (showBgEditorDialog) {
        BgEditorDialog(
            initialBlocks = uiState.defaultBgBlocks,
            onSave = {
                onUpdateDefaultBgBlocks(it)
                showBgEditorDialog = false
            },
            onDismiss = { showBgEditorDialog = false }
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CurrentTherapySettingsPreview() {
    val mockInsulinType = InsulinType(
        name = "Rapid",
        peak = Minutes(75),
        dia = Minutes(300)
    )

    val mockProfile = InsulinProfile(
        id = 1L,
        name = "Normal",
        basalBlocks = listOf(Block(Minutes(1440), 0.5)),
        isfBlocks = listOf(Block(Minutes(1440), 40.0)),
        crBlocks = listOf(Block(Minutes(1440), 10.0)),
        insulinType = mockInsulinType,
        dia = Minutes(300),
        peak = Minutes(75)
    )

    val mockUiState = CurrentTherapyUiState(
        isLoading = false,
        activeProfile = ProfileUiState(
            name = "Normal",
            activeProfileId = 1L,
            isf = BgDelta(40),
            cr = 10.0,
            basal = 0.5,
            target = BgValue.fromMgDl(100),
            lowThreshold = BgValue.fromMgDl(70),
            adjustmentPercentage = 0,
            dia = Minutes(300),
            peak = Minutes(75)
        ),
        availableProfiles = listOf(mockProfile),
        defaultBgBlocks = listOf(
            BgBlock(Minutes(1440), BgValue.fromMgDl(100), BgValue.fromMgDl(70))
        )
    )

    AppTheme {
        CurrentTherapySettingsContent(
            uiState = mockUiState,
            onNavigateUp = {},
            onNavigateToProfileEditor = {},
            onSelectProfile = {},
            onUpdateDefaultBgBlocks = {}
        )
    }
}