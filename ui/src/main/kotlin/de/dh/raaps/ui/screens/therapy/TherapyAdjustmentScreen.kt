package de.dh.raaps.ui.screens.therapy

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import de.dh.raaps.common.ui.composables.StepperDefaults
import de.dh.raaps.common.model.ADJUSTMENT_PERCENTAGE_MAX
import de.dh.raaps.common.model.ADJUSTMENT_PERCENTAGE_MIN
import de.dh.raaps.common.model.LOW_THRESHOLD_MAX
import de.dh.raaps.common.model.LOW_THRESHOLD_MIN
import de.dh.raaps.common.model.TARGET_MAX
import de.dh.raaps.common.model.TARGET_MIN
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.ui.ConfigurableDisplayStrategy
import de.dh.raaps.common.ui.ModuloSteppingStrategy
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.composables.contentScrollIndicator
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.NeutralGrey
import de.dh.raaps.common.ui.theme.SoftBlue
import de.dh.raaps.common.ui.theme.SoftRed
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.ui.controls.profile.TherapyAdjustment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TherapyAdjustmentScreen(
    viewModel: CurrentTherapyViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = screenTitle(stringResource(id = R.string.aps_control_therpay_adjustment_dialog_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.cd_navigate_up)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val activeProfile = uiState.activeInsulinProfile
            TherapyAdjustmentContent(
                currentPercentage = activeProfile.insulinAdjustmentPercentage,
                currentTarget = activeProfile.targetBgOverride,
                currentLow = activeProfile.lowThresholdOverride,
                baseTarget = activeProfile.baseTarget,
                baseLow = activeProfile.baseLow,
                onValuesChange = { p, t, l, h ->
                    viewModel.setTherapyAdjustment(p, t, l, h)
                },
                presets = uiState.therapyAdjustmentPresets,
                onPresetApplied = { p, t, l, h ->
                    viewModel.setTherapyAdjustment(p, t, l, h)
                    onNavigateUp()
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TherapyAdjustmentContent(
    currentPercentage: Int,
    currentTarget: BgValue?,
    currentLow: BgValue?,
    baseTarget: BgValue,
    baseLow: BgValue,
    onValuesChange: (Int, BgValue?, BgValue?, String?) -> Unit,
    onPresetApplied: (Int, BgValue?, BgValue?, String?) -> Unit,
    presets: List<TherapyAdjustment> = emptyList()
) {
    val steppingStrategyInsulin = remember { ModuloSteppingStrategy(5.0) }
    val steppingStrategyBg = remember { ModuloSteppingStrategy(5.0) }

    val displayStrategyInsulin = ConfigurableDisplayStrategy(
        positiveColor = SoftRed,
        negativeColor = SoftBlue,
        neutralColor = NeutralGrey,
        positivePrefix = "+",
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
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Insulin Adjustment Section
        val insulinAdjustmentActive = currentPercentage != 0
        AdjustmentSection(
            icon = Icons.Default.UnfoldMore,
            title = stringResource(R.string.aps_control_therapy_adjustment_dialog_insulin_adjustment_label),
            description = stringResource(R.string.aps_control_therapy_adjustment_dialog_insulin_adjustment_description),
            isActive = insulinAdjustmentActive,
            accentColor = if (currentPercentage > 0) SoftRed else SoftBlue
        ) {
            EditableValueStepper(
                currentValue = currentPercentage.toDouble(),
                onValueChange = { onValuesChange(it.toInt(), currentTarget, currentLow, null) },
                minValue = ADJUSTMENT_PERCENTAGE_MIN.toDouble(),
                maxValue = ADJUSTMENT_PERCENTAGE_MAX.toDouble(),
                steppingStrategy = steppingStrategyInsulin,
                displayStrategy = displayStrategyInsulin,
                suffix = if (currentPercentage != 0) "%" else ""
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // BG Override Section
        AdjustmentSection(
            icon = Icons.Default.Adjust,
            title = stringResource(R.string.aps_control_therapy_adjustment_dialog_bg_adjustment_label),
            description = stringResource(R.string.aps_control_therapy_adjustment_dialog_bg_adjustment_description),
            useCardWrapper = false
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Target BG Tile
                AdjustmentTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    icon = Icons.Default.Adjust,
                    label = stringResource(R.string.current_therapy_target_label),
                    active = currentTarget != null,
                    onActiveChange = { active ->
                        if (active) {
                            onValuesChange(currentPercentage, baseTarget, currentLow, null)
                        } else {
                            onValuesChange(currentPercentage, null, currentLow, null)
                        }
                    },
                    accentColor = MaterialTheme.colorScheme.primary
                ) {
                    if (currentTarget != null) {
                        EditableValueStepper(
                            currentValue = currentTarget.mgdl.toDouble(),
                            onValueChange = {
                                val newValue = if (it == 0.0) null else BgValue.fromMgDl(it.toInt())
                                onValuesChange(currentPercentage, newValue, currentLow, null)
                            },
                            minValue = TARGET_MIN.toDouble(),
                            maxValue = TARGET_MAX.toDouble(),
                            steppingStrategy = steppingStrategyBg,
                            suffix = "mg/dL",
                            style = StepperDefaults.compactStyle()
                        )
                    } else {
                        StandardValueDisplay(baseTarget)
                    }
                }

                // Low Threshold Tile
                AdjustmentTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    icon = Icons.Default.VerticalAlignBottom,
                    label = stringResource(R.string.current_therapy_low_threshold_label),
                    active = currentLow != null,
                    onActiveChange = { active ->
                        if (active) {
                            onValuesChange(currentPercentage, currentTarget, baseLow, null)
                        } else {
                            onValuesChange(currentPercentage, currentTarget, null, null)
                        }
                    },
                    accentColor = MaterialTheme.colorScheme.error
                ) {
                    if (currentLow != null) {
                        EditableValueStepper(
                            currentValue = currentLow.mgdl.toDouble(),
                            onValueChange = {
                                val newValue = if (it == 0.0) null else BgValue.fromMgDl(it.toInt())
                                onValuesChange(currentPercentage, currentTarget, newValue, null)
                            },
                            minValue = LOW_THRESHOLD_MIN.toDouble(),
                            maxValue = LOW_THRESHOLD_MAX.toDouble(),
                            steppingStrategy = steppingStrategyBg,
                            suffix = "mg/dL",
                            style = StepperDefaults.compactStyle()
                        )
                    } else {
                        StandardValueDisplay(baseLow)
                    }
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
                                onPresetApplied(
                                    preset.percentage,
                                    preset.targetBgMgDl?.let { BgValue.fromMgDl(it.toInt()) },
                                    preset.lowThresholdMgDl?.let { BgValue.fromMgDl(it.toInt()) },
                                    preset.name
                                )
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
    useCardWrapper: Boolean = true,
    isActive: Boolean = false,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (useCardWrapper) {
            val containerColor = if (isActive) accentColor.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
            val borderColor = if (isActive) Color.White else MaterialTheme.colorScheme.outlineVariant

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
                border = BorderStroke(1.dp, borderColor)
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
        } else {
            content()
        }
    }
}

@Composable
private fun AdjustmentTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    val containerColor = if (active) accentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    val borderColor = if (active) Color.White else MaterialTheme.colorScheme.outlineVariant
    val contentColor = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = if (active) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    OutlinedCard(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null // Subtler or no ripple to avoid visual clutter in small tiles
        ) {
            onActiveChange(!active)
        },
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = iconTint
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { /* stop propagation if needed */ },
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

@Composable
private fun StandardValueDisplay(
    value: BgValue
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.bg_value_single_format, value.mgdl.toInt()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.aps_control_adjustment_standard),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun TherapyAdjustmentPreviewValues() {
    AppTheme {
        Surface {
            TherapyAdjustmentContent(
                currentPercentage = -10,
                currentTarget = BgValue.fromMgDl(120),
                currentLow = BgValue.fromMgDl(80),
                baseTarget = BgValue.fromMgDl(100),
                baseLow = BgValue.fromMgDl(70),
                onValuesChange = { _, _, _, _ -> },
                onPresetApplied = { _, _, _, _ -> },
                presets = listOf(
                    TherapyAdjustment("Fahrrad fahren", percentage = -30, targetBgMgDl = 150, lowThresholdMgDl = 100),
                    TherapyAdjustment("Stress", percentage = 20, targetBgMgDl = 115, lowThresholdMgDl = 75)
                )
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun TherapyAdjustmentPreviewEmpty() {
    AppTheme {
        Surface {
            TherapyAdjustmentContent(
                currentPercentage = 0,
                currentTarget = null,
                currentLow = null,
                baseTarget = BgValue.fromMgDl(100),
                baseLow = BgValue.fromMgDl(70),
                onValuesChange = { _, _, _, _ -> },
                onPresetApplied = { _, _, _, _ -> },
                presets = listOf(
                    TherapyAdjustment("Fahrrad fahren", percentage = -30, targetBgMgDl = 150, lowThresholdMgDl = 100),
                    TherapyAdjustment("Stress", percentage = 20, targetBgMgDl = 115, lowThresholdMgDl = 75)
                )
            )
        }
    }
}