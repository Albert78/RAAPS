package de.dh.raaps.ui.screens.therapy

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import de.dh.raaps.common.ui.composables.ProfileSelectionDialog
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.NeutralGrey
import de.dh.raaps.common.ui.theme.SoftBlue
import de.dh.raaps.common.ui.theme.SoftGreen
import de.dh.raaps.common.ui.theme.SoftRed
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.dialogs.BgEditorDialog
import de.dh.raaps.ui.controls.dialogs.InsulinAdjustmentDialog
import de.dh.raaps.ui.controls.profile.CurrentTherapyUiState
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.ui.controls.profile.ProfileUiState
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
        onUpdateAdjustmentPercentage = { viewModel.setAdjustmentPercentage(it) }
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
    onUpdateAdjustmentPercentage: (Int) -> Unit
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Active Profile Card
            item {
                SectionHeader(
                    icon = Icons.Default.Tune,
                    title = stringResource(id = R.string.current_therapy_active_insulin_profile_label)
                )

                Spacer(modifier = Modifier.height(8.dp))

                ActiveInsulinProfileCard(
                    profile = uiState.activeProfile,
                    onSwitchInsulinProfileClick = { showInsulinProfileDialog = true },
                    onManageInsulinProfilesClick = onNavigateToInsulinProfileEditor,
                    onAdjustmentClick = { showAdjustmentDialog = true }
                )
            }

            // Section 2: BG Target & Low Threshold Card
            item {
                SectionHeader(
                    icon = Icons.Default.Adjust,
                    title = stringResource(id = R.string.current_therapy_bg_title)
                )

                Spacer(modifier = Modifier.height(8.dp))

                BgTargetCard(
                    bgBlocks = uiState.defaultBgBlocks,
                    onClick = { showBgEditorDialog = true }
                )
            }
        }
    }

    if (showInsulinProfileDialog) {
        ProfileSelectionDialog(
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
        InsulinAdjustmentDialog(
            currentValue = uiState.activeProfile.adjustmentPercentage,
            presets = uiState.therapyAdjustmentPresets,
            onValueChange = {
                onUpdateAdjustmentPercentage(it)
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
    profile: ProfileUiState,
    onSwitchInsulinProfileClick: () -> Unit,
    onManageInsulinProfilesClick: () -> Unit,
    onAdjustmentClick: () -> Unit,
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

            // Percentage Adjustment Clickable Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAdjustmentClick() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(id = R.string.aps_control_insulin_adjustment_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(id = R.string.aps_control_insulin_adjustment_dialog_description),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                val displayStrategy = ConfigurableDisplayStrategy(
                    positiveColor = SoftRed,
                    negativeColor = SoftBlue,
                    neutralColor = NeutralGrey,
                    positivePrefix = "+",
                    suffix = "%",
                    neutralLabel = stringResource(R.string.aps_control_adjustment_neutral)
                )

                Surface(
                    color = if (profile.adjustmentPercentage == 0) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        displayStrategy.color(profile.adjustmentPercentage).copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (profile.adjustmentPercentage == 0) {
                            MaterialTheme.colorScheme.outlineVariant
                        } else {
                            displayStrategy.color(profile.adjustmentPercentage)
                        }
                    )
                ) {
                    Text(
                        text = displayStrategy.format(profile.adjustmentPercentage),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (profile.adjustmentPercentage == 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            displayStrategy.color(profile.adjustmentPercentage)
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
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
private fun BgTargetCard(
    bgBlocks: List<BgBlock>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targets = bgBlocks.map { it.target.mgdl }.distinct()
    val lows = bgBlocks.map { it.lowThreshold.mgdl }.distinct()

    val targetValue = if (targets.size == 1) {
        stringResource(id = R.string.bg_value_single_format, targets.first())
    } else if (targets.isNotEmpty()) {
        stringResource(id = R.string.bg_value_range_format, targets.minOrNull() ?: 0, targets.maxOrNull() ?: 0)
    } else {
        "-"
    }

    val lowValue = if (lows.size == 1) {
        stringResource(id = R.string.bg_value_single_format, lows.first())
    } else if (lows.isNotEmpty()) {
        stringResource(id = R.string.bg_value_range_format, lows.minOrNull() ?: 0, lows.maxOrNull() ?: 0)
    } else {
        "-"
    }

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
                        text = stringResource(id = R.string.current_therapy_target_label_singular),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = targetValue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                        text = stringResource(id = R.string.current_therapy_low_threshold_label_singular),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = lowValue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
        activeProfile = ProfileUiState(
            name = "Normal",
            activeProfileId = 1L,
            isf = BgDelta(40),
            cr = 10.0,
            basal = 0.5,
            target = BgValue.fromMgDl(100),
            lowThreshold = BgValue.fromMgDl(70),
            adjustmentPercentage = 0,
            dia = Minutes(300),
            peak = Minutes(75)
        ),
        availableProfiles = listOf(mockProfile1, mockProfile2),
        defaultBgBlocks = listOf(
            BgBlock(Minutes(1440), BgValue.fromMgDl(100), BgValue.fromMgDl(70))
        ),
        therapyAdjustmentPresets = listOf(
            TherapyAdjustment("Normal", 0),
            TherapyAdjustment("Sport", -20)
        )
    )

    AppTheme {
        CurrentTherapySettingsContent(
            uiState = mockUiState,
            onNavigateUp = {},
            onNavigateToInsulinProfileEditor = {},
            onSelectProfile = {},
            onUpdateDefaultBgBlocks = {},
            onUpdateAdjustmentPercentage = {}
        )
    }
}