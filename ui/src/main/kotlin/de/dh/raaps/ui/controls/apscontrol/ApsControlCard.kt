package de.dh.raaps.ui.controls.apscontrol

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.ui.composables.AppColorBlue
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.SoftBlue
import de.dh.raaps.common.ui.theme.SoftGreen
import de.dh.raaps.common.ui.theme.SoftRed
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.profile.ProfileUiState

@Composable
fun ApsControlCard(
    modifier: Modifier = Modifier,
    profileUiState: ProfileUiState?,
    selectedMode: ApsMode,
    availableModes: List<ApsMode>,
    onModeChange: (ApsMode) -> Unit,
    adjustmentPercentage: Int,
    onAdjustmentClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val isSuspended = selectedMode == ApsMode.Suspend

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
                if (profileUiState == null) {
                    Text(
                        text = stringResource(R.string.aps_control_no_profile)
                    )
                } else {
                    val nameText = if (adjustmentPercentage != 0) {
                        "${profileUiState.name} (${if (adjustmentPercentage > 0) "+" else ""}$adjustmentPercentage%)"
                    } else {
                        profileUiState.name
                    }
                    Text(
                        text = nameText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
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
                                text = profileUiState.target.mgdl.toString(),
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
                                text = profileUiState.lowThreshold.mgdl.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
                                val basalValue = String.format(LocalLocale.current.platformLocale, "%.1f", if (isSuspended) 0.0 else profileUiState.basal)
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
                                        text = " ${stringResource(R.string.aps_control_ic_label, String.format(LocalLocale.current.platformLocale, "%.1f", profileUiState.ic))} ",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = if (isSuspended) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.extraSmall,
                                ) {
                                    Text(
                                        text = " ${stringResource(R.string.aps_control_isf_label, profileUiState.isf.mgdl)} ",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
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
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mode Button
                var showModeMenu by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { showModeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (selectedMode) {
                                ApsMode.AutoCorrection -> SoftGreen
                                ApsMode.BasalOnly -> SoftBlue
                                ApsMode.Suspend -> SoftRed
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(selectedMode.toDisplayString(), style = MaterialTheme.typography.titleMedium)
                    }
                    DropdownMenu(
                        expanded = showModeMenu,
                        onDismissRequest = { showModeMenu = false }
                    ) {
                        availableModes.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.toDisplayString()) },
                                onClick = {
                                    onModeChange(mode)
                                    showModeMenu = false
                                }
                            )
                        }
                    }
                }

                // Adjustment Button
                val isNeutral = adjustmentPercentage == 0
                if (isNeutral) {
                    OutlinedButton(
                        onClick = onAdjustmentClick,
                        enabled = !isSuspended,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.aps_control_adjustment_neutral), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(
                        onClick = onAdjustmentClick,
                        enabled = !isSuspended,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (adjustmentPercentage > 0) SoftRed else SoftBlue
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${if (adjustmentPercentage > 0) "+" else ""}$adjustmentPercentage%",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApsMode.toDisplayString(): String = stringResource(id = when (this) {
    ApsMode.Suspend -> R.string.aps_mode_suspend
    ApsMode.BasalOnly -> R.string.aps_mode_basal_only
    ApsMode.AutoCorrection -> R.string.aps_mode_auto_correction
})

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewApsControlCard() {
    AppTheme {
        Surface {
            ApsControlCard(
                modifier = Modifier.padding(16.dp),
                profileUiState = ProfileUiState(
                    name = "Standard",
                    activeProfileId = null,
                    target = BgValue.fromMgDl(100),
                    lowThreshold = BgValue.fromMgDl(70),
                    basal = 0.8,
                    ic = 12.0,
                    isf = BgDelta.fromMgDl(50),
                    adjustmentPercentage = 0,
                    dia = Minutes(300),
                    peak = Minutes(75)
                ),
                selectedMode = ApsMode.AutoCorrection,
                availableModes = ApsMode.entries,
                onModeChange = {},
                adjustmentPercentage = 0,
                onAdjustmentClick = {},
                onProfileClick = {}
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewApsControlCardNoProfile() {
    AppTheme {
        Surface {
            ApsControlCard(
                modifier = Modifier.padding(16.dp),
                profileUiState = null,
                selectedMode = ApsMode.AutoCorrection,
                availableModes = ApsMode.entries,
                onModeChange = {},
                adjustmentPercentage = 0,
                onAdjustmentClick = {},
                onProfileClick = {}
            )
        }
    }
}