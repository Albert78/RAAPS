package de.dh.raaps.ui.controls.profile

import android.content.res.Configuration
import android.util.Range
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.R
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.ui.icValue
import de.dh.raaps.common.ui.isfValue
import de.dh.raaps.common.ui.targetRange
import de.dh.raaps.common.ui.theme.AppTheme

@Composable
fun CurrentTherapyView(
    uiState: CurrentTherapyUiState,
    onProfileSelect: (Profile) -> Unit,
    modifier: Modifier = Modifier
) {
    var showProfileDialog by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { showProfileDialog = true },
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = uiState.profileName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                InfoColumn(
                    label = stringResource(id = R.string.therapy_target_label),
                    value = uiState.currentTarget?.let { targetRange(it, uiState.glucoseUnit) } ?: "-",
                    modifier = Modifier.weight(1f)
                )
                InfoColumn(
                    label = stringResource(id = R.string.therapy_isf_label),
                    value = uiState.currentIsf?.let { isfValue(it, uiState.glucoseUnit) } ?: "-",
                    modifier = Modifier.weight(1f)
                )
                InfoColumn(
                    label = stringResource(id = R.string.therapy_ic_label),
                    value = uiState.currentIc?.let { icValue(it) } ?: "-",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showProfileDialog) {
        ProfileSelectionDialog(
            profiles = uiState.availableProfiles,
            activeProfileId = uiState.activeProfileId,
            onProfileSelected = {
                onProfileSelect(it)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
private fun ProfileSelectionDialog(
    profiles: List<Profile>,
    activeProfileId: Long?,
    onProfileSelected: (Profile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.therapy_profile_selection_title)) },
        text = {
            LazyColumn {
                items(profiles) { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        leadingContent = {
                            RadioButton(
                                selected = profile.id == activeProfileId,
                                onClick = null // Handled by ListItem click
                            )
                        },
                        modifier = Modifier.clickable { onProfileSelected(profile) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = de.dh.raaps.common.R.string.cd_close))
            }
        }
    )
}

@Composable
private fun InfoColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CurrentTherapyViewPreview() {
    AppTheme {
        CurrentTherapyView(
            uiState = CurrentTherapyUiState(
                isLoading = false,
                profileName = "Normal",
                activeProfileId = 1L,
                currentIsf = BgDelta.fromMgDl(50),
                currentIc = 10.0,
                currentTarget = Range(BgValue.fromMgDl(80), BgValue.fromMgDl(120)),
                glucoseUnit = GlucoseUnit.MG_DL,
                availableProfiles = listOf(
                    Profile(id = 1L, name = "Normal", therapyData = TherapyData(basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList())),
                    Profile(id = 2L, name = "Sport", therapyData = TherapyData(basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList()))
                )
            ),
            onProfileSelect = {}
        )
    }
}