package de.dh.raaps.ui.controls.apscontrol

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.ConfigurableDisplayStrategy
import de.dh.raaps.ui.common.composables.AppColorBlue
import de.dh.raaps.ui.common.composables.NormalButton
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.common.theme.NeutralGrey
import de.dh.raaps.ui.common.theme.SoftBlue
import de.dh.raaps.ui.common.theme.SoftGreen
import de.dh.raaps.ui.common.theme.SoftRed
import de.dh.raaps.ui.controls.profile.InsulinProfileUiState

@Composable
fun ApsControlCard(
    modifier: Modifier = Modifier,
    insulinProfileUiState: InsulinProfileUiState,
    selectedMode: ApsMode,
    availableModes: List<ApsMode>,
    onModeChange: (ApsMode) -> Unit,
    insulinAdjustmentPercentage: Int,
    adjustmentHint: String?,
    onAdjustmentClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val isSuspended = selectedMode == ApsMode.Suspend
    val displayStrategy = ConfigurableDisplayStrategy(
        positiveColor = SoftRed,
        negativeColor = SoftBlue,
        neutralColor = NeutralGrey,
        positivePrefix = "+",
        suffix = "%",
        neutralLabel = stringResource(R.string.aps_control_adjustment_neutral)
    )

    Surface(
        modifier = modifier.height(IntrinsicSize.Min),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(2.dp, AppColorBlue.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Info Column (left)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onProfileClick() }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val nameText = if (insulinAdjustmentPercentage != 0) {
                    "${insulinProfileUiState.name} (${displayStrategy.format(insulinAdjustmentPercentage.toDouble())})"
                } else {
                    insulinProfileUiState.name
                }
                Text(
                    text = nameText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Chips (Basal, I:C, ISF)
                CompositionLocalProvider(
                    LocalContentColor provides if (isSuspended) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else LocalContentColor.current
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Basal Chip
                        Surface(
                            color = if (isSuspended) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            val basalValue = String.format(LocalLocale.current.platformLocale, "%.1f", if (isSuspended) 0.0 else insulinProfileUiState.currentBasal.iu)
                            Text(
                                text = " ${stringResource(R.string.aps_control_basal_label, basalValue)} ",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        // I:C & ISF Chips
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = if (isSuspended) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    text = " ${stringResource(R.string.aps_control_cr_label, String.format(LocalLocale.current.platformLocale, "%.1f", insulinProfileUiState.currentCr))} ",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = if (isSuspended) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    text = " ${stringResource(R.string.aps_control_isf_label, insulinProfileUiState.currentIsf.mgdl)} ",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // Target
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Adjust,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = insulinProfileUiState.target.mgdl.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Low
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignBottom,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = insulinProfileUiState.lowThreshold.mgdl.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            VerticalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 1.dp,
                color = Color.Gray.copy(alpha = 0.3f)
            )

            // Buttons Column (right)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mode Button
                var showModeDialog by remember { mutableStateOf(false) }
                Box {
                    PrimaryButton(
                        onClick = { showModeDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (selectedMode) {
                                ApsMode.AutoCorrection -> SoftGreen
                                ApsMode.BasalOnly -> SoftBlue
                                ApsMode.Suspend -> SoftRed
                            }
                        )
                    ) {
                        Text(selectedMode.toDisplayStringShort(), style = MaterialTheme.typography.titleMedium)
                    }

                    if (showModeDialog) {
                        ApsModeSelectionDialog(
                            selectedMode = selectedMode,
                            availableModes = availableModes,
                            onModeChange = onModeChange,
                            onDismissRequest = { showModeDialog = false }
                        )
                    }
                }

                // Adjustment Button
                val isNeutral = insulinAdjustmentPercentage == 0
                val adjustmentText = buildString {
                    if (adjustmentHint != null) {
                        append(adjustmentHint)
                        append(" (")
                    }
                    append(displayStrategy.format(insulinAdjustmentPercentage.toDouble()))
                    if (adjustmentHint != null) {
                        append(")")
                    }
                }

                if (isNeutral && adjustmentHint == null) {
                    NormalButton(
                        onClick = onAdjustmentClick,
                        enabled = !isSuspended,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(adjustmentText, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    PrimaryButton(
                        onClick = onAdjustmentClick,
                        enabled = !isSuspended,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = displayStrategy.color(insulinAdjustmentPercentage.toDouble())
                        )
                    ) {
                        Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            adjustmentText,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApsMode.toDisplayStringShort(): String = stringResource(id = when (this) {
    ApsMode.Suspend -> R.string.aps_mode_suspend_short
    ApsMode.BasalOnly -> R.string.aps_mode_basal_only_short
    ApsMode.AutoCorrection -> R.string.aps_mode_auto_correction_short
})

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewApsControlCard() {
    AppTheme {
        Surface {
            ApsControlCard(
                modifier = Modifier.padding(16.dp),
                insulinProfileUiState = InsulinProfileUiState(
                    name = "Standard",
                    activeProfileId = null,
                    currentIsf = BgDelta.fromMgDl(50),
                    currentCr = 12.0,
                    currentBasal = InsulinAmount(0.8),
                    isfRange = "50",
                    crRange = "12.0",
                    basalRange = "0.80",
                    target = BgValue.fromMgDl(100),
                    lowThreshold = BgValue.fromMgDl(70),
                    insulinAdjustmentPercentage = 0,
                    targetBgOverride = null,
                    lowThresholdOverride = null,
                    adjustmentHint = null,
                    dia = Minutes(300),
                    peak = Minutes(75),
                    baseLow = BgValue.fromMgDl(70),
                    baseTarget = BgValue.fromMgDl(110)
                ),
                selectedMode = ApsMode.AutoCorrection,
                availableModes = ApsMode.entries,
                onModeChange = {},
                insulinAdjustmentPercentage = 0,
                adjustmentHint = null,
                onAdjustmentClick = {},
                onProfileClick = {}
            )
        }
    }
}
