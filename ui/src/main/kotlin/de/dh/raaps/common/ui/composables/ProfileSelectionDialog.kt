package de.dh.raaps.common.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.dh.raaps.common.R
import de.dh.raaps.common.model.data.InsulinProfile

@Composable
fun ProfileSelectionDialog(
    profiles: List<InsulinProfile>,
    activeProfileId: Long?,
    onProfileSelected: (InsulinProfile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(id = R.string.therapy_profile_selection_title))
            }
        },
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
                Text(stringResource(id = R.string.action_close))
            }
        }
    )
}