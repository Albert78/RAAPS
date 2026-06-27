package de.dh.raaps.plugin.simbody.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.dh.raaps.common.navigation.FeatureNavGraph
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.plugin.simbody.BodyModel
import java.util.Locale

class SimBodyNavGraph(
    private val navViewModel: NavigationViewModel,
    private val bodyModel: BodyModel?
) : FeatureNavGraph {
    override fun getEntry(key: NavKey): NavEntry<NavKey>? {
        return when (key) {
            is SimBodyMainRoute -> NavEntry(key) {
                SimBodyDetailScreen(bodyModel)
            }
            else -> null
        }
    }

    @Composable
    private fun SimBodyDetailScreen(bodyModel: BodyModel?) {
        if (bodyModel == null) {
            Text("Body Model not available")
            return
        }

        Scaffold(
            topBar = {
                Text(
                    "Sim Body Detailed Controls",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                item {
                    Text("Historical Inputs", style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                item {
                    Text("Meals", style = MaterialTheme.typography.titleMedium)
                }
                items(bodyModel.meals) { meal ->
                    Text("${meal.carbGrams}g at ${meal.timestamp}")
                }

                item {
                    Text("Insulin", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                }
                items(bodyModel.insulinApplications) { insulin ->
                    Text("${insulin.amount}U at ${insulin.timestamp} (${insulin.origin})")
                }

                item {
                    Button(
                        onClick = {
                            bodyModel.meals.clear()
                            bodyModel.insulinApplications.clear()
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Clear History")
                    }
                }
            }
        }
    }

    @Composable
    override fun DashboardExtension() {
        if (bodyModel == null) return

        val exercise by bodyModel.exerciseIntensityFlow.collectAsState()
        val illness by bodyModel.illnessFactorFlow.collectAsState()
        val stress by bodyModel.stressLevelFlow.collectAsState()
        val bg by bodyModel.bloodGlucoseFlow.collectAsState()
        val activeProfile by bodyModel.activeProfileFlow.collectAsState()

        var showEditDialog by remember { mutableStateOf<String?>(null) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sim Body Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = { navViewModel.push(SimBodyMainRoute) }) {
                        Text("Details")
                    }
                }

                ParameterRow("Blood Glucose", "${bg.mgdl} mg/dL") { showEditDialog = "bg" }
                ParameterRow("Exercise Intensity", String.format(Locale.US, "%.2f", exercise)) { showEditDialog = "exercise" }
                ParameterRow("Illness Factor", String.format(Locale.US, "%.2f", illness)) { showEditDialog = "illness" }
                ParameterRow("Stress Level", String.format(Locale.US, "%.2f", stress)) { showEditDialog = "stress" }

                Text("Body Profile", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                ParameterRow("ISF", "${bodyModel.isf} mg/dL/U") { }
                ParameterRow("IC", "${bodyModel.ic} g/U") { }
                ParameterRow("Liver Output", "${bodyModel.liverGlucoseOutputUph} U/h") { }

                Text(
                    "Inputs: ${bodyModel.meals.size} meals, ${bodyModel.insulinApplications.size} boluses",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        when (showEditDialog) {
            "bg" -> EditDoubleDialog("BG (mg/dL)", bg.mgdl.toDouble(), { showEditDialog = null }) {
                bodyModel.bloodGlucose = de.dh.raaps.common.model.data.BgValue.fromMgDl(it.toInt())
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
}
