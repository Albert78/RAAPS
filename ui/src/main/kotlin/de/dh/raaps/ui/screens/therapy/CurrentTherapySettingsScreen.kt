package de.dh.raaps.ui.screens.therapy

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.ui.ConfigurableDisplayStrategy
import de.dh.raaps.common.ui.composables.InsulinProfileSelectionDialog
import de.dh.raaps.common.ui.composables.contentScrollIndicator
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.NeutralGrey
import de.dh.raaps.common.ui.theme.SoftBlue
import de.dh.raaps.common.ui.theme.SoftGreen
import de.dh.raaps.common.ui.theme.SoftRed
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.dialogs.BgEditorDialog
import de.dh.raaps.ui.controls.dialogs.TherapyAdjustmentDialog
import de.dh.raaps.ui.controls.profile.CurrentTherapyUiState
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.ui.controls.profile.InsulinProfileUiState
import de.dh.raaps.ui.controls.profile.TherapyAdjustment

@Composable
fun CurrentTherapySettingsScreen(
    viewModel: CurrentTherapyViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToInsulinProfileEditor: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    CurrentTherapySettingsContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onNavigateToInsulinProfileEditor = onNavigateToInsulinProfileEditor,
        onSelectProfile = { viewModel.selectInsulinProfile(it) },
        onUpdateDefaultBgBlocks = { viewModel.updateDefaultBgBlocks(it) },
        onUpdateTherapyAdjustment = { p, t, l, h -> viewModel.setTherapyAdjustment(p, t, l, h) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentTherapySettingsContent(
    uiState: CurrentTherapyUiState,
    onNavigateUp: () -> Unit,
    onNavigateToInsulinProfileEditor: () -> Unit,
    onSelectProfile: (InsulinProfile) -> Unit,
    onUpdateDefaultBgBlocks: (List<BgBlock>) -> Unit,
    onUpdateTherapyAdjustment: (Int, BgValue?, BgValue?, String?) -> Unit
) {
    var showInsulinProfileDialog by remember { mutableStateOf(false) }
    var showBgEditorDialog by remember { mutableStateOf(false) }
    var showAdjustmentDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onNavigateToInsulinProfileEditor) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(id = R.string.menu_item_profile_editor_label)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .contentScrollIndicator(scrollState)
                    .verticalScroll(scrollState)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active Insulin Profile Card
                SectionHeader(
                    icon = Icons.Default.Tune,
                    title = stringResource(id = R.string.current_therapy_active_insulin_profile_label)
                )

                ActiveInsulinProfileCard(
                    profile = uiState.activeProfile,
                    onSwitchInsulinProfileClick = { showInsulinProfileDialog = true },
                    onManageInsulinProfilesClick = onNavigateToInsulinProfileEditor
                )

                Spacer(modifier = Modifier.height(8.dp))

                // BG Target & Low Threshold Card
                SectionHeader(
                    icon = Icons.Default.Adjust,
                    title = stringResource(id = R.string.current_therapy_bg_title)
                )

                BgTargetCard(
                    bgBlocks = uiState.defaultBgBlocks,
                    onClick = { showBgEditorDialog = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Temporary Adjustment Card
                SectionHeader(
                    icon = Icons.Default.UnfoldMore,
                    title = stringResource(id = R.string.aps_control_therapy_adjustment_label)
                )

                TemporaryAdjustmentCard(
                    insulinProfile = uiState.activeProfile,
                    onClick = { showAdjustmentDialog = true }
                )
            }
        }
    }

    if (showInsulinProfileDialog) {
        InsulinProfileSelectionDialog(
            profiles = uiState.availableProfiles,
            activeProfileId = uiState.activeProfile.activeProfileId,
            onProfileSelected = {
                onSelectProfile(it)
                showInsulinProfileDialog = false
            },
            onDismiss = { showInsulinProfileDialog = false }
        )
    }

    if (showBgEditorDialog) {
        BgEditorDialog(
            initialBlocks = uiState.defaultBgBlocks,
            onSave = {
                onUpdateDefaultBgBlocks(it)
                showBgEditorDialog = false
            },
            onDismiss = { showBgEditorDialog = false }
        )
    }

    if (showAdjustmentDialog) {
        TherapyAdjustmentDialog(
            currentPercentage = uiState.activeProfile.insulinAdjustmentPercentage,
            currentTarget = uiState.activeProfile.targetBgOverride,
            currentLow = uiState.activeProfile.lowThresholdOverride,
            currentHint = uiState.activeProfile.adjustmentHint,
            presets = uiState.therapyAdjustmentPresets,
            onValuesChange = { p, t, l, h ->
                onUpdateTherapyAdjustment(p, t, l, h)
            },
            onDismissRequest = { showAdjustmentDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActiveInsulinProfileCard(
    profile: InsulinProfileUiState,
    onSwitchInsulinProfileClick: () -> Unit,
    onManageInsulinProfilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalLocale.current.platformLocale

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Name & Active Badge Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = profile.name.ifBlank { stringResource(id = R.string.aps_control_no_insulin_profile) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = SoftGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SoftGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SoftGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.current_therapy_active_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Parameter Grid (Basal, CR, ISF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    label = stringResource(id = de.dh.raaps.common.R.string.therapy_basal_label),
                    value = String.format(locale, "%.2f", profile.basal),
                    unit = stringResource(id = R.string.unit_u_per_h),
                    modifier = Modifier.weight(1f)
                )

                MetricChip(
                    label = stringResource(id = de.dh.raaps.common.R.string.therapy_cr_label),
                    value = String.format(locale, "%.1f", profile.cr),
                    unit = stringResource(id = R.string.unit_g_per_u),
                    modifier = Modifier.weight(1f)
                )

                MetricChip(
                    label = stringResource(id = de.dh.raaps.common.R.string.therapy_isf_label),
                    value = profile.isf.mgdl.toString(),
                    unit = stringResource(id = R.string.unit_mgdl_per_u),
                    modifier = Modifier.weight(1f)
                )
            }

            // Secondary Info: DIA and Peak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.current_therapy_dia_label_format, formatMinutes(profile.dia)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(id = R.string.current_therapy_peak_label_format, formatMinutes(profile.peak)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSwitchInsulinProfileClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(id = R.string.current_therapy_switch_profile_button))
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = onManageInsulinProfilesClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(id = R.string.current_therapy_manage_profile_button))
                }
            }
        }
    }
}

@Composable
private fun BgTargetCard(
    bgBlocks: List<BgBlock>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targets = bgBlocks.map { it.target.mgdl }.distinct()
    val lows = bgBlocks.map { it.lowThreshold.mgdl }.distinct()

    val targetValue = if (targets.size == 1) {
        targets.first().toString()
    } else if (targets.isNotEmpty()) {
        "${targets.minOrNull() ?: 0}–${targets.maxOrNull() ?: 0}"
    } else {
        "-"
    }

    val lowValue = if (lows.size == 1) {
        lows.first().toString()
    } else if (lows.isNotEmpty()) {
        "${lows.minOrNull() ?: 0}–${lows.maxOrNull() ?: 0}"
    } else {
        "-"
    }

    val unit = stringResource(id = R.string.glucose_unit_mgdl)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Target Column
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Adjust,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = stringResource(id = R.string.current_therapy_target_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = targetValue,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            VerticalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Low Threshold Column
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalAlignBottom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = stringResource(id = R.string.current_therapy_low_threshold_short_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = lowValue,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TemporaryAdjustmentCard(
    insulinProfile: InsulinProfileUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayStrategy = ConfigurableDisplayStrategy(
        positiveColor = SoftRed,
        negativeColor = SoftBlue,
        neutralColor = NeutralGrey,
        positivePrefix = "+",
        suffix = "%",
        neutralLabel = stringResource(R.string.aps_control_adjustment_neutral)
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Hint and Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = insulinProfile.adjustmentHint ?: stringResource(R.string.aps_control_therapy_adjustment_custom),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (insulinProfile.adjustmentHint != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Adjustment Grid
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Insulin Adjustment
                AdjustmentItem(
                    icon = Icons.Default.UnfoldMore,
                    label = stringResource(R.string.aps_control_therapy_adjustment_dialog_insulin_adjustment_label),
                    value = displayStrategy.format(insulinProfile.insulinAdjustmentPercentage),
                    valueColor = if (insulinProfile.insulinAdjustmentPercentage == 0)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        displayStrategy.color(insulinProfile.insulinAdjustmentPercentage),
                    status = if (insulinProfile.insulinAdjustmentPercentage == 0)
                        stringResource(R.string.aps_control_adjustment_neutral)
                    else
                        stringResource(
                            if (insulinProfile.insulinAdjustmentPercentage > 0)
                                R.string.label_increased
                            else
                                R.string.label_decreased
                        )
                )

                // Target BG Override
                AdjustmentItem(
                    icon = Icons.Default.Adjust,
                    label = stringResource(R.string.current_therapy_target_label),
                    value = if (insulinProfile.targetBgOverride != null)
                        insulinProfile.targetBgOverride.mgdl.toString()
                    else
                        stringResource(R.string.aps_control_adjustment_standard),
                    unit = if (insulinProfile.targetBgOverride != null) stringResource(R.string.glucose_unit_mgdl) else null,
                    valueColor = if (insulinProfile.targetBgOverride != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    status = if (insulinProfile.targetBgOverride != null)
                        stringResource(R.string.current_therapy_active_badge)
                    else
                        null
                )

                // Low Threshold Override
                AdjustmentItem(
                    icon = Icons.Default.VerticalAlignBottom,
                    label = stringResource(R.string.current_therapy_low_threshold_label),
                    value = if (insulinProfile.lowThresholdOverride != null)
                        insulinProfile.lowThresholdOverride.mgdl.toString()
                    else
                        stringResource(R.string.aps_control_adjustment_standard),
                    unit = if (insulinProfile.lowThresholdOverride != null) stringResource(R.string.glucose_unit_mgdl) else null,
                    valueColor = if (insulinProfile.lowThresholdOverride != null)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    status = if (insulinProfile.lowThresholdOverride != null) stringResource(R.string.label_active) else null
                )
            }
        }
    }
}

@Composable
private fun AdjustmentItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    status: String? = null,
    unit: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(6.dp)
                    .size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = valueColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = valueColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun formatMinutes(minutes: Minutes): String {
    val total = minutes.value.toInt()
    if (total <= 0) return stringResource(id = R.string.duration_minutes_format, 0)
    val hours = total / 60
    val mins = total % 60
    return when {
        hours > 0 && mins > 0 -> stringResource(id = R.string.duration_hours_and_minutes_format, hours, mins)
        hours > 0 -> stringResource(id = R.string.duration_hours_format, hours)
        else -> stringResource(id = R.string.duration_minutes_format, mins)
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CurrentTherapySettingsPreview() {
    val mockInsulinType = InsulinType(
        name = "Rapid",
        peak = Minutes(75),
        dia = Minutes(300)
    )

    val mockProfile1 = InsulinProfile(
        id = 1L,
        name = "Normal",
        basalBlocks = listOf(Block(Minutes(1440), 0.5)),
        isfBlocks = listOf(Block(Minutes(1440), 40.0)),
        crBlocks = listOf(Block(Minutes(1440), 10.0)),
        insulinType = mockInsulinType,
        dia = Minutes(300),
        peak = Minutes(75)
    )

    val mockProfile2 = InsulinProfile(
        id = 2L,
        name = "Sport",
        basalBlocks = listOf(Block(Minutes(1440), 0.3)),
        isfBlocks = listOf(Block(Minutes(1440), 50.0)),
        crBlocks = listOf(Block(Minutes(1440), 12.0)),
        insulinType = mockInsulinType,
        dia = Minutes(300),
        peak = Minutes(75)
    )

    val mockUiState = CurrentTherapyUiState(
        isLoading = false,
        activeProfile = InsulinProfileUiState(
            name = "Normal",
            activeProfileId = 1L,
            isf = BgDelta(40),
            cr = 10.0,
            basal = 0.5,
            target = BgValue.fromMgDl(100),
            lowThreshold = BgValue.fromMgDl(70),
            insulinAdjustmentPercentage = -30,
            targetBgOverride = BgValue.fromMgDl(160),
            lowThresholdOverride = BgValue.fromMgDl(90),
            adjustmentHint = "Fahrrad fahren",
            dia = Minutes(300),
            peak = Minutes(75)
        ),
        availableProfiles = listOf(mockProfile1, mockProfile2),
        defaultBgBlocks = listOf(
            BgBlock(Minutes(1440), BgValue.fromMgDl(100), BgValue.fromMgDl(70))
        ),
        therapyAdjustmentPresets = listOf(
            TherapyAdjustment("Neutral"),
            TherapyAdjustment("Fahrrad fahren", percentage = -30, targetBgMgDl = 150, lowThresholdMgDl = 100),
            TherapyAdjustment("Klettern", percentage = -40, targetBgMgDl = 160, lowThresholdMgDl = 110),
        )
    )

    AppTheme {
        CurrentTherapySettingsContent(
            uiState = mockUiState,
            onNavigateUp = {},
            onNavigateToInsulinProfileEditor = {},
            onSelectProfile = {},
            onUpdateDefaultBgBlocks = {},
            onUpdateTherapyAdjustment = { _, _, _, _ -> }
        )
    }
}