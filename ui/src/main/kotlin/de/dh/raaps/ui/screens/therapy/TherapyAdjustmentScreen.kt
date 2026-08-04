package de.dh.raaps.ui.screens.therapy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    
    // Local state for editing
    var localPercentage by remember(uiState.activeInsulinProfile.insulinAdjustmentPercentage) { 
        mutableStateOf(uiState.activeInsulinProfile.insulinAdjustmentPercentage) 
    }
    var localTarget by remember(uiState.activeInsulinProfile.targetBgOverride) { 
        mutableStateOf(uiState.activeInsulinProfile.targetBgOverride) 
    }
    var localLow by remember(uiState.activeInsulinProfile.lowThresholdOverride) { 
        mutableStateOf(uiState.activeInsulinProfile.lowThresholdOverride) 
    }
    var localHint by remember(uiState.activeInsulinProfile.adjustmentHint) { 
        mutableStateOf(uiState.activeInsulinProfile.adjustmentHint) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.aps_control_therpay_adjustment_dialog_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.cd_navigate_up)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.setTherapyAdjustment(localPercentage, localTarget, localLow, localHint)
                        onNavigateUp()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.action_save)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TherapyAdjustmentContent(
                currentPercentage = localPercentage,
                currentTarget = localTarget,
                currentLow = localLow,
                baseTarget = uiState.activeInsulinProfile.baseTarget,
                baseLow = uiState.activeInsulinProfile.baseLow,
                onValuesChange = { p, t, l, h ->
                    localPercentage = p
                    localTarget = t
                    localLow = l
                    localHint = h
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
            .fillMaxSize()
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
        AdjustmentSection(
            icon = Icons.Default.UnfoldMore,
            title = stringResource(R.string.aps_control_therapy_adjustment_dialog_insulin_adjustment_label),
            description = stringResource(R.string.aps_control_therapy_adjustment_dialog_insulin_adjustment_description)
        ) {
            EditableValueStepper(
                currentValue = currentPercentage.toDouble(),
                onValueChange = { onValuesChange(it.toInt(), currentTarget, currentLow, null) },
                minValue = ADJUSTMENT_PERCENTAGE_MIN.toDouble(),
                maxValue = ADJUSTMENT_PERCENTAGE_MAX.toDouble(),
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
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
                        Switch(
                            checked = currentTarget != null,
                            onCheckedChange = { active ->
                                if (active) {
                                    onValuesChange(currentPercentage, baseTarget, currentLow, null)
                                } else {
                                    onValuesChange(currentPercentage, null, currentLow, null)
                                }
                            }
                        )
                    }
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
                            suffix = " mg/dL"
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clickable {
                                    onValuesChange(currentPercentage, baseTarget, currentLow, null)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.bg_value_single_format, baseTarget.mgdl.toInt()),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "(${stringResource(R.string.aps_control_adjustment_standard)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Low Threshold
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
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
                        Switch(
                            checked = currentLow != null,
                            onCheckedChange = { active ->
                                if (active) {
                                    onValuesChange(currentPercentage, currentTarget, baseLow, null)
                                } else {
                                    onValuesChange(currentPercentage, currentTarget, null, null)
                                }
                            }
                        )
                    }
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
                            suffix = " mg/dL"
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clickable {
                                    onValuesChange(currentPercentage, currentTarget, baseLow, null)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.bg_value_single_format, baseLow.mgdl.toInt()),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "(${stringResource(R.string.aps_control_adjustment_standard)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
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
                fontWeight = FontWeight.Bold
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