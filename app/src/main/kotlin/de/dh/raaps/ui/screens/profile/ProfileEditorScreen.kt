package de.dh.raaps.ui.screens.profile

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.R
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.controls.profile.ProfileSettingsUiState
import de.dh.raaps.ui.controls.profile.ProfileSettingsViewModel

@Composable
fun ProfileEditorScreen(
    viewModel: ProfileSettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    ProfileEditorContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onAddProfile = { viewModel.startEditing(null) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorContent(
    uiState: ProfileSettingsUiState,
    onNavigateUp: () -> Unit,
    onAddProfile: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.profile_editor_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.cd_navigate_up)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProfile) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.cd_add_profile)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.profiles) { profile ->
                        ListItem(
                            headlineContent = { Text(profile.name) },
                            modifier = androidx.compose.ui.Modifier.padding(8.dp)
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
fun ProfileEditorPreview() {
    AppTheme {
        ProfileEditorContent(
            uiState = ProfileSettingsUiState(
                profiles = listOf(
                    Profile(name = "Normal", therapyData = TherapyData(basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList())),
                    Profile(name = "Sport", therapyData = TherapyData(basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList())),
                    Profile(name = "Illness", therapyData = TherapyData(basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList()))
                ),
                isLoading = false
            ),
            onNavigateUp = {},
            onAddProfile = {}
        )
    }
}