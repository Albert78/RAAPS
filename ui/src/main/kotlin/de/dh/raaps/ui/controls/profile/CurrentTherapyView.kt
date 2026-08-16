package de.dh.raaps.ui.controls.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R as CommonR
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.LocalGlucoseUnit
import de.dh.raaps.ui.common.glucoseValue
import de.dh.raaps.ui.common.crValue
import de.dh.raaps.ui.common.composables.InsulinProfileSelectionDialog

@Composable
fun CurrentTherapyView(
    uiState: CurrentTherapyUiState,
    onProfileSelect: (InsulinProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var showProfileDialog by remember { mutableStateOf(false) }

    val activeProfile = uiState.activeInsulinProfile

    OutlinedCard(
        modifier = modifier
            .padding(vertical = 8.dp)
            .clickable { showProfileDialog = true },
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = activeProfile.name.ifBlank { "-" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            InfoRow(
                label = stringResource(id = CommonR.string.therapy_target_label),
                value = glucoseValue(activeProfile.target, withUnit = true),
                unit = "",
                icon = Icons.Default.Adjust,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            InfoRow(
                label = stringResource(id = CommonR.string.therapy_low_threshold_label),
                value = glucoseValue(activeProfile.lowThreshold, withUnit = true),
                unit = "",
                icon = Icons.Default.VerticalAlignBottom,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            InfoRow(
                label = stringResource(id = CommonR.string.therapy_basal_label),
                value = activeProfile.basalRange,
                unit = stringResource(id = CommonR.string.unit_u_per_h),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            InfoRow(
                label = stringResource(id = CommonR.string.therapy_isf_label),
                value = activeProfile.isfRange, // This range is still formatted in ViewModel. I should probably fix that too.
                unit = "",
                modifier = Modifier.padding(bottom = 2.dp)
            )
            InfoRow(
                label = stringResource(id = CommonR.string.therapy_cr_label),
                value = activeProfile.crRange, // Same here.
                unit = stringResource(id = CommonR.string.unit_g_per_u)
            )
        }
    }

    if (showProfileDialog) {
        InsulinProfileSelectionDialog(
            profiles = uiState.availableInsulinProfiles,
            activeProfileId = activeProfile.activeProfileId,
            onProfileSelected = {
                onProfileSelect(it)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp).size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = "$value $unit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}