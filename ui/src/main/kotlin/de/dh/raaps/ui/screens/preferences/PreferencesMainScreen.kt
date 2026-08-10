package de.dh.raaps.ui.screens.preferences

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.ThemeMode
import de.dh.raaps.ui.common.composables.DialogDismissButton
import de.dh.raaps.ui.common.composables.DialogSurface
import de.dh.raaps.ui.common.composables.DialogTitle
import de.dh.raaps.ui.common.composables.screenTitle
import de.dh.raaps.ui.common.icons.Icon_Screen_Back
import de.dh.raaps.ui.common.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: PreferencesViewModel,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    PreferencesContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onThemeSelected = { newValue ->
            viewModel.setThemeMode(newValue)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesContent(
    uiState: PreferencesUiState,
    onNavigateUp: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = screenTitle(stringResource(R.string.preferences_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icon_Screen_Back,
                            contentDescription = stringResource(de.dh.raaps.common.R.string.cd_navigate_up)
                        )
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
        ) {
            PreferenceCategory(title = stringResource(R.string.pref_category_appearance_title))

            // Map the internal value to a display label
            val currentThemeLabel = stringResource(uiState.themeMode.labelResId)

            PreferenceItem(
                title = stringResource(R.string.pref_theme_title),
                summary = currentThemeLabel,
                icon = Icons.Default.Palette,
                onClick = { showThemeDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentValue = uiState.themeMode,
            onDismiss = { showThemeDialog = false },
            onSelected = { newValue ->
                onThemeSelected(newValue)
                showThemeDialog = false
            }
        )
    }
}

@Composable
fun ThemeSelectionDialog(
    currentValue: ThemeMode,
    onDismiss: () -> Unit,
    onSelected: (ThemeMode) -> Unit
) {
    val themeModes = ThemeMode.entries

    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Column(modifier = Modifier
                .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally) {

                DialogTitle(
                    stringResource(id = R.string.pref_theme_dialog_title),
                    modifier = Modifier
                        .padding(24.dp)
                )

                Column(
                    Modifier.selectableGroup(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    themeModes.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (mode == currentValue),
                                    onClick = { onSelected(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (mode == currentValue),
                                onClick = null // null recommended for accessibility with screen readers
                            )
                            Text(
                                text = stringResource(mode.labelResId),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                DialogDismissButton(
                    modifier = Modifier
                        .padding(24.dp),
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
fun PreferenceCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun PreferenceItem(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreferencesContentPreview() {
    AppTheme {
        PreferencesContent(
            uiState = PreferencesUiState(isLoading = false, isError = false),
            onNavigateUp = {},
            onThemeSelected = {},
        )
    }
}
