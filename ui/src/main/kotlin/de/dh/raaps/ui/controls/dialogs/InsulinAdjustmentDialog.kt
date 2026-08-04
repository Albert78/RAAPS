package de.dh.raaps.ui.controls.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.ConfigurableDisplayStrategy
import de.dh.raaps.common.ui.ModuloSteppingStrategy
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.NeutralGrey
import de.dh.raaps.common.ui.theme.SoftBlue
import de.dh.raaps.common.ui.theme.SoftRed
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.profile.AdjustmentPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsulinAdjustmentDialogContent(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    onDismissRequest: (() -> Unit)? = null,
    presets: List<AdjustmentPreset> = emptyList()
) {
    val steppingStrategy = remember { ModuloSteppingStrategy(5) }
    val displayStrategy = ConfigurableDisplayStrategy(
        positiveColor = SoftRed,
        negativeColor = SoftBlue,
        neutralColor = NeutralGrey,
        positivePrefix = "+",
        suffix = "%",
        neutralLabel = stringResource(R.string.aps_control_adjustment_neutral)
    )

    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                focusManager.clearFocus()
            },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.aps_control_adjustment_dialog_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                EditableValueStepper(
                    currentValue = currentValue,
                    onValueChange = onValueChange,
                    steppingStrategy = steppingStrategy,
                    displayStrategy = displayStrategy,
                    suffix = "%"
                )
            }
        }

        if (presets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(R.string.aps_control_adjustment_dialog_presets_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        SuggestionChip(
                            onClick = {
                                onValueChange(preset.percentage)
                                onDismissRequest?.invoke()
                            },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = displayStrategy.format(preset.percentage),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = displayStrategy.color(preset.percentage)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InsulinAdjustmentDialog(
    currentValue: Int,
    presets: List<AdjustmentPreset>,
    onValueChange: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(id = R.string.aps_control_adjustment_dialog_title)) },
        text = {
            InsulinAdjustmentDialogContent(
                currentValue = currentValue,
                onValueChange = onValueChange,
                onDismissRequest = onDismissRequest,
                presets = presets
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = android.R.string.ok))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun InsulinAdjustmentDialogPreview() {
    AppTheme {
        Surface {
            InsulinAdjustmentDialogContent(
                currentValue = 10,
                onValueChange = {},
                presets = listOf(
                    AdjustmentPreset("Normal", 0),
                    AdjustmentPreset("Wandern", -20),
                    AdjustmentPreset("Fahrrad fahren", -30),
                    AdjustmentPreset("Klettern", -40)
                )
            )
        }
    }
}