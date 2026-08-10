package de.dh.raaps.plugin.simbody.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.R
import de.dh.raaps.plugin.simbody.ui.components.SimBodyEatMealDialog
import de.dh.raaps.ui.common.composables.NormalTextButton
import de.dh.raaps.ui.common.composables.PrimaryButton
import java.util.Locale
import de.dh.raaps.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimBodyMainScreen(
    bodyModel: BodyModel?,
    onNavigateUp: () -> Unit = {},
    onNavigateToImpacts: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    if (bodyModel == null) {
        Text(stringResource(R.string.body_model_not_available))
        return
    }

    val bg by bodyModel.bloodGlucoseFlow.collectAsState()
    val isSensorEnabled by bodyModel.isSensorEnabledFlow.collectAsState()
    val sensorNoiseFactor by bodyModel.sensorNoiseFactorFlow.collectAsState()
    val isLoaded by bodyModel.isLoadedFlow.collectAsState()

    var showEatMealDialog by remember { mutableStateOf(false) }
    var showNoiseFactorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.sim_body_details_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CommonR.string.cd_navigate_up)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Summary Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isLoaded) stringResource(R.string.unit_mg_dl, bg) else "---",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.label_current_bg),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatusItem(stringResource(R.string.label_iob), stringResource(R.string.unit_u, bodyModel.iob.iu))
                        StatusItem(stringResource(R.string.label_cob), stringResource(R.string.unit_g, bodyModel.cob))
                    }
                }
            }

            // Action Buttons
            PrimaryButton(
                onClick = { showEatMealDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = isLoaded
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_eat_meal))
            }

            PrimaryButton(
                onClick = onNavigateToImpacts,
                modifier = Modifier.fillMaxWidth(),
                enabled = isLoaded
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_physiological_impacts))
            }

            PrimaryButton(
                onClick = onNavigateToHistory,
                modifier = Modifier.fillMaxWidth(),
                enabled = isLoaded
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_input_history))
            }

            // Sensor Configuration Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.title_sensor_config),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.label_sensor_enabled), style = MaterialTheme.typography.bodyMedium)
                        Checkbox(
                            checked = isSensorEnabled,
                            onCheckedChange = { bodyModel.isSensorEnabled = it },
                            enabled = isLoaded
                        )
                    }

                    ParameterRow(
                        label = stringResource(R.string.label_noise_factor),
                        value = if (isLoaded) String.format(Locale.US, "%.2f", sensorNoiseFactor) else "---"
                    ) {
                        if (isLoaded) showNoiseFactorDialog = true
                    }
                }
            }
        }
    }

    if (showEatMealDialog) {
        SimBodyEatMealDialog(
            onDismiss = { showEatMealDialog = false },
            onConfirm = { carbs, type -> bodyModel.eat(carbs, type) }
        )
    }

    if (showNoiseFactorDialog) {
        EditDoubleDialog(
            title = stringResource(R.string.dialog_title_edit_noise),
            initialValue = sensorNoiseFactor,
            onDismiss = { showNoiseFactorDialog = false }
        ) {
            bodyModel.sensorNoiseFactor = it.coerceAtLeast(0.0)
        }
    }
}

@Composable
private fun ParameterRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
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

@Composable
private fun StatusItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
