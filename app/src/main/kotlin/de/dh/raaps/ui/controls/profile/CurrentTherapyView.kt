package de.dh.raaps.ui.controls.profile

import android.util.Range
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.R
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.ui.icValue
import de.dh.raaps.common.ui.isfValue
import de.dh.raaps.common.ui.targetRange
import de.dh.raaps.common.ui.theme.AppTheme

@Composable
fun CurrentTherapyView(
    uiState: CurrentTherapyUiState,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = uiState.profileName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(id = R.string.cd_edit_profile)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                InfoColumn(
                    label = stringResource(id = R.string.therapy_target_label),
                    value = targetRange(uiState.currentTarget, uiState.glucoseUnit),
                    modifier = Modifier.weight(1f)
                )
                InfoColumn(
                    label = stringResource(id = R.string.therapy_isf_label),
                    value = isfValue(uiState.currentIsf, uiState.glucoseUnit),
                    modifier = Modifier.weight(1f)
                )
                InfoColumn(
                    label = stringResource(id = R.string.therapy_ic_label),
                    value = icValue(uiState.currentIc),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun InfoColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
fun CurrentTherapyViewPreview() {
    AppTheme {
        CurrentTherapyView(
            uiState = CurrentTherapyUiState(
                isLoading = false,
                profileName = "Normal",
                currentIsf = BgDelta.fromMgDl(50),
                currentIc = 10.0,
                currentTarget = Range(BgValue.fromMgDl(80), BgValue.fromMgDl(120)),
                glucoseUnit = GlucoseUnit.MG_DL
            ),
            onEditClick = {}
        )
    }
}