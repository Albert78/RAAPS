package de.dh.raaps.ui.controls.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.ui.ConfigurableDisplayStrategy
import de.dh.raaps.common.ui.ModuloSteppingStrategy
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.composables.contentScrollIndicator
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.NeutralGrey
import de.dh.raaps.common.ui.theme.SoftBlue
import de.dh.raaps.common.ui.theme.SoftRed
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.profile.TherapyAdjustment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TherapyAdjustmentDialogContent(
    currentPercentage: Int,
    currentTarget: BgValue?,
    currentLow: BgValue?,
    onValuesChange: (Int, BgValue?, BgValue?, String?) -> Unit,
    onDismissRequest: (() -> Unit)? = null,
    presets: List<TherapyAdjustment> = emptyList()
) {
    val steppingStrategyInsulin = remember { ModuloSteppingStrategy(5.0) }
    val steppingStrategyBg = remember { ModuloSteppingStrategy(5.0) }

    val displayStrategyInsulin = ConfigurableDisplayStrategy(
        positiveColor = SoftRed,
        negativeColor = SoftBlue,
        neutralColor = NeutralGrey,
        positivePrefix = "+",
        suffix = "%",
        neutralLabel = stringResource(R.string.aps_control_adjustment_neutral)
    )

    val focusManager = LocalFocusManager.current

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .contentScrollIndicator(scrollState)
            .verticalScroll(scrollState)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                focusManager.clearFocus()
            },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Insulin Adjustment Section
        AdjustmentSection(
            icon = Icons.Default.UnfoldMore,
            title = stringResource(R.string.aps_control_therapy_adjustment_dialog_insulin_adjustment_label),
            description = stringResource(R.string.aps_control_therapy_adjustment_dialog_insulin_adjustment_description)
        ) {
            EditableValueStepper(
                currentValue = currentPercentage.toDouble(),
                onValueChange = { onValuesChange(it.toInt(), currentTarget, currentLow, null) },
                steppingStrategy = steppingStrategyInsulin,
                displayStrategy = displayStrategyInsulin,
                suffix = "%"
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // BG Override Section
        AdjustmentSection(
            icon = Icons.Default.Adjust,
            title = stringResource(R.string.aps_control_therapy_adjustment_dialog_bg_adjustment_label),
            description = stringResource(R.string.aps_control_therapy_adjustment_dialog_bg_adjustment_description)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Target BG
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Adjust,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.current_therapy_target_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    EditableValueStepper(
                        currentValue = currentTarget?.mgdl?.toDouble() ?: 0.0,
                        onValueChange = {
                            val newValue = if (it == 0.0) null else BgValue.fromMgDl(it.toInt())
                            onValuesChange(currentPercentage, newValue, currentLow, null)
                        },
                        steppingStrategy = steppingStrategyBg,
                        suffix = " mg/dL"
                    )
                }

                // Low Threshold
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignBottom,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.current_therapy_low_threshold_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    EditableValueStepper(
                        currentValue = currentLow?.mgdl?.toDouble() ?: 0.0,
                        onValueChange = {
                            val newValue = if (it == 0.0) null else BgValue.fromMgDl(it.toInt())
                            onValuesChange(currentPercentage, currentTarget, newValue, null)
                        },
                        steppingStrategy = steppingStrategyBg,
                        suffix = " mg/dL"
                    )
                }
            }
        }

        if (presets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(R.string.aps_control_therapy_adjustment_dialog_presets_title),
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
                                onValuesChange(
                                    preset.percentage,
                                    preset.targetBgMgDl?.let { BgValue.fromMgDl(it.toInt()) },
                                    preset.lowThresholdMgDl?.let { BgValue.fromMgDl(it.toInt()) },
                                    preset.name
                                )
                                onDismissRequest?.invoke()
                            },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = displayStrategyInsulin.format(preset.percentage.toDouble()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = displayStrategyInsulin.color(preset.percentage.toDouble())
                                        )
                                        if (preset.targetBgMgDl != null) {
                                            Text(
                                                text = "• ${preset.targetBgMgDl} mg/dL",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
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
private fun AdjustmentSection(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

@Composable
fun TherapyAdjustmentDialog(
    currentPercentage: Int,
    currentTarget: BgValue?,
    currentLow: BgValue?,
    currentHint: String?,
    presets: List<TherapyAdjustment>,
    onValuesChange: (Int, BgValue?, BgValue?, String?) -> Unit,
    onDismissRequest: () -> Unit
) {
    // Keep local state for the dialog to avoid jumping while typing
    var localPercentage by remember(currentPercentage) { mutableStateOf(currentPercentage) }
    var localTarget by remember(currentTarget) { mutableStateOf(currentTarget) }
    var localLow by remember(currentLow) { mutableStateOf(currentLow) }
    var localHint by remember(currentHint) { mutableStateOf(currentHint) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(id = R.string.aps_control_therpay_adjustment_dialog_title)) },
        text = {
            TherapyAdjustmentDialogContent(
                currentPercentage = localPercentage,
                currentTarget = localTarget,
                currentLow = localLow,
                onValuesChange = { p, t, l, h ->
                    localPercentage = p
                    localTarget = t
                    localLow = l
                    localHint = h
                },
                onDismissRequest = {
                    onValuesChange(localPercentage, localTarget, localLow, localHint)
                    onDismissRequest()
                },
                presets = presets
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onValuesChange(localPercentage, localTarget, localLow, localHint)
                onDismissRequest()
            }) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun TherapyAdjustmentDialogPreview() {
    AppTheme {
        Surface {
            TherapyAdjustmentDialogContent(
                currentPercentage = 10,
                currentTarget = BgValue.fromMgDl(120),
                currentLow = BgValue.fromMgDl(80),
                onValuesChange = { _, _, _, _ -> },
                presets = listOf(
                    TherapyAdjustment("Normal", 0),
                    TherapyAdjustment("Wandern", -20, 140, 90),
                    TherapyAdjustment("Fahrrad fahren", -30, 150, 100),
                    TherapyAdjustment("Klettern", -40, 160, 110),
                    TherapyAdjustment("Laufen", -25, 130, 95),
                    TherapyAdjustment("Krank", 30, 100, 70),
                    TherapyAdjustment("Stress", 20, 115, 75)
                )
            )
        }
    }
}