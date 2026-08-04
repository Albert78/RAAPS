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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.ui.R
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.ui.composables.ProfileSelectionDialog
import de.dh.raaps.common.ui.isfValue
import de.dh.raaps.common.ui.theme.AppTheme
import java.util.Locale

@Composable
fun CurrentTherapyView(
    uiState: CurrentTherapyUiState,
    onProfileSelect: (InsulinProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var showProfileDialog by remember { mutableStateOf(false) }

    val unitStr = when(uiState.glucoseUnit) {
        GlucoseUnit.MG_DL -> stringResource(id = R.string.glucose_unit_mgdl)
        GlucoseUnit.MMOL -> stringResource(id = R.string.glucose_unit_mmol)
    }

    val activeProfile = uiState.activeProfile

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
                text = activeProfile?.name ?: "-",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            InfoRow(
                label = stringResource(id = de.dh.raaps.common.R.string.therapy_target_label),
                value = activeProfile?.target?.toString(uiState.glucoseUnit) ?: "-",
                unit = unitStr,
                icon = Icons.Default.Adjust,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            InfoRow(
                label = stringResource(id = de.dh.raaps.common.R.string.therapy_low_threshold_label),
                value = activeProfile?.lowThreshold?.toString(uiState.glucoseUnit) ?: "-",
                unit = unitStr,
                icon = Icons.Default.VerticalAlignBottom,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            InfoRow(
                label = stringResource(id = de.dh.raaps.common.R.string.therapy_basal_label),
                value = activeProfile?.basal?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "-",
                unit = stringResource(id = R.string.unit_u_per_h),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            InfoRow(
                label = stringResource(id = de.dh.raaps.common.R.string.therapy_isf_label),
                value = activeProfile?.isf?.let { isfValue(it, uiState.glucoseUnit) } ?: "-",
                unit = "$unitStr/U",
                modifier = Modifier.padding(bottom = 2.dp)
            )
            InfoRow(
                label = stringResource(id = de.dh.raaps.common.R.string.therapy_cr_label),
                value = activeProfile?.cr?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "-",
                unit = stringResource(id = R.string.unit_g_per_u)
            )
        }
    }

    if (showProfileDialog) {
        ProfileSelectionDialog(
            profiles = uiState.availableProfiles,
            activeProfileId = activeProfile?.activeProfileId,
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