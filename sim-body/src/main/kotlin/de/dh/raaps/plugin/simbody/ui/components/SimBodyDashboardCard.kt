package de.dh.raaps.plugin.simbody.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.model.BodyProfile
import java.util.Locale

@Composable
fun SimBodyDashboardCard(
    bodyModel: BodyModel,
    onDetailsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    treatmentProfileName: String? = null,
    treatmentBasal: String? = null,
    treatmentIsf: String? = null,
    treatmentIc: String? = null,
    treatmentTarget: String? = null,
    treatmentLowThreshold: String? = null
) {
    val exercise by bodyModel.exerciseIntensityFlow.collectAsState()
    val illness by bodyModel.illnessFactorFlow.collectAsState()
    val stress by bodyModel.stressLevelFlow.collectAsState()
    val bg by bodyModel.bloodGlucoseFlow.collectAsState()
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
                    "Sim Body Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onDetailsClick) {
                    Text("Details")
                }
            }

            ParameterRow("Blood Glucose", "${bg.mgdl} mg/dL") { showEditDialog = "bg" }
            ParameterRow("IOB / COB", String.format(Locale.US, "%.2f U / %.1f g", bodyModel.iob, bodyModel.cob)) { }
            ParameterRow("Exercise Intensity", String.format(Locale.US, "%.2f", exercise)) {
                showEditDialog = "exercise"
            }
            ParameterRow("Illness Factor", String.format(Locale.US, "%.2f", illness)) {
                showEditDialog = "illness"
            }
            ParameterRow("Stress Level", String.format(Locale.US, "%.2f", stress)) {
                showEditDialog = "stress"
            }

            Text(
                "Body Profile",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            ParameterRow("ISF", "${bodyModel.isf} mg/dL/U") { }
            ParameterRow("IC", "${bodyModel.ic} g/U") { }
            ParameterRow("Liver Output", "${bodyModel.liverGlucoseOutputGph} g/h") { }

            if (treatmentProfileName != null) {
                Text(
                    "Treatment Profile: $treatmentProfileName",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (treatmentBasal != null) ParameterRow("Basal", treatmentBasal) { }
                if (treatmentIsf != null) ParameterRow("ISF", treatmentIsf) { }
                if (treatmentIc != null) ParameterRow("IC", treatmentIc) { }
                if (treatmentTarget != null) ParameterRow("Target", treatmentTarget) { }
                if (treatmentLowThreshold != null) ParameterRow("Low Threshold", treatmentLowThreshold) { }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHistoryClick)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Inputs: ${bodyModel.meals.size} meals, ${bodyModel.insulinApplications.size} boluses",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Show History",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    when (showEditDialog) {
        "bg" -> EditDoubleDialog("BG (mg/dL)", bg.mgdl.toDouble(), { showEditDialog = null }) {
            bodyModel.bloodGlucose = BgValue.fromMgDl(it.toInt())
        }
        "exercise" -> EditDoubleDialog("Exercise Intensity (0-1)", exercise, { showEditDialog = null }) {
            bodyModel.exerciseIntensity = it.coerceIn(0.0, 1.0)
        }
        "illness" -> EditDoubleDialog("Illness Factor (>= 1.0)", illness, { showEditDialog = null }) {
            bodyModel.illnessFactor = it.coerceAtLeast(1.0)
        }
        "stress" -> EditDoubleDialog("Stress Level (0-1)", stress, { showEditDialog = null }) {
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
    var textValue by remember { mutableStateOf(initialValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit $title") },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                textValue.toDoubleOrNull()?.let { onConfirm(it) }
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SimBodyDashboardCardPreview() {
    val mockProfile = BodyProfile(
        icBlocks = listOf(Block(Minutes.ofHours(24), 10.0)),
        isfBlocks = listOf(Block(Minutes.ofHours(24), 50.0)),
        liverGlucoseOutputBlocks = listOf(Block(Minutes.ofHours(24), 5.0))
    )
    val bodyModel = remember {
        BodyModel(mockProfile).apply {
            bloodGlucose = BgValue.fromMgDl(140)
            exerciseIntensity = 0.2
            illnessFactor = 1.1
            stressLevel = 0.5
        }
    }

    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SimBodyDashboardCard(
                bodyModel = bodyModel,
                onDetailsClick = {},
                onHistoryClick = {},
            )
        }
    }
}