package de.dh.raaps.plugin.simbody.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.model.BodyProfile
import java.util.Locale

@Composable
fun SimBodyDashboardCard(
    bodyModel: BodyModel,
    onDetailsClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val exercise by bodyModel.exerciseIntensityFlow.collectAsState()
    val illness by bodyModel.illnessFactorFlow.collectAsState()
    val stress by bodyModel.stressLevelFlow.collectAsState()
    val bg by bodyModel.bloodGlucoseFlow.collectAsState()
    val isSensorEnabled by bodyModel.isSensorEnabledFlow.collectAsState()
    val sensorNoiseFactor by bodyModel.sensorNoiseFactorFlow.collectAsState()
    val isLoaded by bodyModel.isLoadedFlow.collectAsState()
    val activeProfile by bodyModel.activeProfileFlow.collectAsState()

    var showEditDialog by remember { mutableStateOf<String?>(null) }
    var showEatMealDialog by remember { mutableStateOf(false) }

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
                Row {
                    Button(
                        onClick = { showEatMealDialog = true },
                        enabled = isLoaded
                    ) {
                        Text("Essen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDetailsClick,
                        enabled = isLoaded
                    ) {
                        Text("Details")
                    }
                }
            }

            ParameterRow(
                "Blood Glucose",
                if (isLoaded) String.format(Locale.US, "%.1f mg/dL", bg) else "---"
            ) { if (isLoaded) showEditDialog = "bg" }

            ParameterRow(
                "IOB / COB",
                if (isLoaded) String.format(Locale.US, "%.2f U / %.1f g", bodyModel.iob, bodyModel.cob) else "---"
            ) { }

            ParameterRow(
                "Exercise Intensity",
                if (isLoaded) String.format(Locale.US, "%.2f", exercise) else "---"
            ) {
                if (isLoaded) showEditDialog = "exercise"
            }
            ParameterRow(
                "Illness Factor",
                if (isLoaded) String.format(Locale.US, "%.2f", illness) else "---"
            ) {
                if (isLoaded) showEditDialog = "illness"
            }
            ParameterRow(
                "Stress Level",
                if (isLoaded) String.format(Locale.US, "%.2f", stress) else "---"
            ) {
                if (isLoaded) showEditDialog = "stress"
            }

            Text(
                "Sensor Config",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sensor Enabled", style = MaterialTheme.typography.bodyMedium)
                Checkbox(
                    checked = isSensorEnabled,
                    onCheckedChange = { bodyModel.isSensorEnabled = it },
                    enabled = isLoaded
                )
            }
            ParameterRow(
                "Noise Factor",
                if (isLoaded) String.format(Locale.US, "%.2f", sensorNoiseFactor) else "---"
            ) {
                if (isLoaded) showEditDialog = "noise"
            }

            Text(
                "Body Profile",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            ParameterRow(
                "ISF",
                if (isLoaded) String.format(Locale.US, "%.1f mg/dL/U", bodyModel.isf) else "---"
            ) { }
            ParameterRow(
                "CR",
                if (isLoaded) String.format(Locale.US, "%.1f g/U", bodyModel.cr) else "---"
            ) { }
            ParameterRow(
                "Liver Output",
                if (isLoaded) "${bodyModel.liverGlucoseOutputGph} g/h" else "---"
            ) { }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isLoaded, onClick = onHistoryClick)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isLoaded) "Inputs: ${bodyModel.meals.size} meals, ${bodyModel.insulinApplications.size} boluses" else "Loading...",
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
        "bg" -> EditDoubleDialog("BG (mg/dL)", bg, { showEditDialog = null }) {
            bodyModel.bloodGlucose = it
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
        "noise" -> EditDoubleDialog("Noise Factor (>= 0)", sensorNoiseFactor, { showEditDialog = null }) {
            bodyModel.sensorNoiseFactor = it.coerceAtLeast(0.0)
        }
    }

    if (showEatMealDialog) {
        SimBodyEatMealDialog(
            onDismiss = { showEatMealDialog = false },
            onConfirm = { carbs, type -> bodyModel.eat(carbs, type) }
        )
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
                onHistoryClick = {},
            )
        }
    }
}