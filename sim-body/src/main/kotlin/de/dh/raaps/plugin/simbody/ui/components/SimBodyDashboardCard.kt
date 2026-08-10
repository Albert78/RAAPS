package de.dh.raaps.plugin.simbody.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.R
import de.dh.raaps.plugin.simbody.model.BodyProfile
import de.dh.raaps.ui.common.composables.NormalTextButton
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.theme.AppTheme
import java.util.Locale

@Composable
fun SimBodyDashboardCard(
    bodyModel: BodyModel,
    onDetailsClick: () -> Unit
) {
    val exercise by bodyModel.exerciseIntensityFlow.collectAsState()
    val illness by bodyModel.illnessFactorFlow.collectAsState()
    val stress by bodyModel.stressLevelFlow.collectAsState()
    val bg by bodyModel.bloodGlucoseFlow.collectAsState()
    val isLoaded by bodyModel.isLoadedFlow.collectAsState()
    val activeProfile by bodyModel.activeProfileFlow.collectAsState()

    var showEditDialog by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.sim_body_status_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                PrimaryButton(
                    onClick = onDetailsClick,
                    enabled = isLoaded
                ) {
                    Text(stringResource(R.string.btn_details))
                }
            }

            ParameterRow(
                stringResource(R.string.label_blood_glucose),
                if (isLoaded) stringResource(R.string.unit_mg_dl, bg) else "---"
            ) { if (isLoaded) showEditDialog = "bg" }

            ParameterRow(
                stringResource(R.string.label_iob_cob),
                if (isLoaded) stringResource(R.string.unit_iob_cob, bodyModel.iob.iu, bodyModel.cob) else "---"
            ) { }

            ParameterRow(
                stringResource(R.string.label_exercise_intensity),
                if (isLoaded) String.format(Locale.US, "%.2f", exercise) else "---"
            ) {
                if (isLoaded) showEditDialog = "exercise"
            }
            ParameterRow(
                stringResource(R.string.label_illness_factor),
                if (isLoaded) String.format(Locale.US, "%.2f", illness) else "---"
            ) {
                if (isLoaded) showEditDialog = "illness"
            }
            ParameterRow(
                stringResource(R.string.label_stress_level),
                if (isLoaded) String.format(Locale.US, "%.1f", stress) else "---"
            ) {
                if (isLoaded) showEditDialog = "stress"
            }

            Text(
                stringResource(R.string.label_body_profile),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            ParameterRow(
                stringResource(R.string.label_isf),
                if (isLoaded) stringResource(R.string.unit_isf, bodyModel.isf) else "---"
            ) { }
            ParameterRow(
                stringResource(R.string.label_cr),
                if (isLoaded) stringResource(R.string.unit_cr, bodyModel.cr) else "---"
            ) { }
            ParameterRow(
                stringResource(R.string.label_liver_output),
                if (isLoaded) stringResource(R.string.unit_liver_output, bodyModel.liverGlucoseOutputGph) else "---"
            ) { }
        }
    }

    when (showEditDialog) {
        "bg" -> EditDoubleDialog(stringResource(R.string.dialog_title_edit_bg), bg, { showEditDialog = null }) {
            bodyModel.bloodGlucose = it
        }
        "exercise" -> EditDoubleDialog(stringResource(R.string.dialog_title_edit_exercise), exercise, { showEditDialog = null }) {
            bodyModel.exerciseIntensity = it.coerceIn(0.0, 1.0)
        }
        "illness" -> EditDoubleDialog(stringResource(R.string.dialog_title_edit_illness), illness, { showEditDialog = null }) {
            bodyModel.illnessFactor = it.coerceAtLeast(1.0)
        }
        "stress" -> EditDoubleDialog(stringResource(R.string.dialog_title_edit_stress), stress, { showEditDialog = null }) {
            bodyModel.stressLevel = it.coerceIn(0.0, 1.0)
        }
    }
}

@Composable
private fun ParameterRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EditDoubleDialog(
    title: String,
    initialValue: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var textValue by remember { mutableStateOf(if (initialValue == 0.0) "" else initialValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_edit_param, title)) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            NormalTextButton(onClick = {
                textValue.toDoubleOrNull()?.let { onConfirm(it) }
                onDismiss()
            }) {
                Text(stringResource(R.string.btn_ok))
            }
        },
        dismissButton = {
            NormalTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SimBodyDashboardCardPreview() {
    val mockProfile = BodyProfile(
        crBlocks = listOf(Block(Minutes.ofHours(24), 10.0)),
        isfBlocks = listOf(Block(Minutes.ofHours(24), 50.0)),
        liverGlucoseOutputBlocks = listOf(Block(Minutes.ofHours(24), 5.0))
    )
    val bodyModel = remember {
        BodyModel(mockProfile).apply {
            bloodGlucose = 140.0
            exerciseIntensity = 0.2
            illnessFactor = 1.1
            stressLevel = 0.5
        }
    }

    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SimBodyDashboardCard(
                bodyModel = bodyModel,
                onDetailsClick = {},
            )
        }
    }
}
