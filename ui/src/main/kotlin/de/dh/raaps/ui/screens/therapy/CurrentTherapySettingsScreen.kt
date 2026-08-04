package de.dh.raaps.ui.screens.therapy

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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.composables.ProfileSelectionDialog
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentTherapySettingsScreen(
    viewModel: CurrentTherapyViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToProfileEditor: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val locale = LocalLocale.current.platformLocale
    var showProfileDialog by remember { mutableStateOf(false) }

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
                        text = stringResource(id = R.string.current_therapy_active_profile_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                val activeProfile = uiState.activeProfile
                if (activeProfile != null) {
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
                                                id = R.string.aps_control_ic_label,
                                                String.format(locale, "%.1f", activeProfile.ic)
                                            ) + " | " +
                                            stringResource(
                                                id = R.string.aps_control_isf_label,
                                                activeProfile.isf.mgdl
                                            )
                                )
                            },
                            leadingContent = {
                                RadioButton(selected = true, onClick = null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showProfileDialog = true }
                        )
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
                viewModel.selectProfile(it)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }
}