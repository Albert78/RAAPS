package de.dh.raaps.ui.controls.apscontrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.composables.NormalTextButton
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.common.theme.SoftBlue
import de.dh.raaps.ui.common.theme.SoftGreen
import de.dh.raaps.ui.common.theme.SoftRed

@Composable
fun ApsModeSelectionDialog(
    selectedMode: ApsMode,
    availableModes: List<ApsMode>,
    onModeChange: (ApsMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.aps_mode_dialog_title), // reusing theme dialog title or generic select
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            ApsModeSelectionContent(
                selectedMode = selectedMode,
                availableModes = availableModes,
                onModeChange = {
                    onModeChange(it)
                    onDismissRequest()
                }
            )
        },
        confirmButton = {
            NormalTextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ApsModeSelectionContent(
    selectedMode: ApsMode,
    availableModes: List<ApsMode>,
    onModeChange: (ApsMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        availableModes.forEachIndexed { index, mode ->
            ModeOption(
                mode = mode,
                isSelected = mode == selectedMode,
                onClick = { onModeChange(mode) }
            )
            if (index < availableModes.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ModeOption(
    mode: ApsMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = mode.toDescriptionString(),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        PrimaryButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when (mode) {
                    ApsMode.AutoCorrection -> SoftGreen
                    ApsMode.BasalOnly -> SoftBlue
                    ApsMode.Suspend -> SoftRed
                }
            ),
            border = if (isSelected) {
                BorderStroke(6.dp, MaterialTheme.colorScheme.primary)
            } else null
        ) {
            Text(
                text = mode.toDisplayStringFull(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ApsMode.toDisplayStringFull(): String = stringResource(id = when (this) {
    ApsMode.Suspend -> R.string.aps_mode_suspend
    ApsMode.BasalOnly -> R.string.aps_mode_basal_only
    ApsMode.AutoCorrection -> R.string.aps_mode_auto_correction
})

@Composable
private fun ApsMode.toDescriptionString(): String = stringResource(id = when (this) {
    ApsMode.Suspend -> R.string.aps_mode_suspend_desc
    ApsMode.BasalOnly -> R.string.aps_mode_basal_only_desc
    ApsMode.AutoCorrection -> R.string.aps_mode_auto_correction_desc
})

@Preview(showBackground = true)
@Composable
private fun PreviewApsModeSelectionContent() {
    AppTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            ApsModeSelectionContent(
                selectedMode = ApsMode.AutoCorrection,
                availableModes = ApsMode.entries,
                onModeChange = {}
            )
        }
    }
}
