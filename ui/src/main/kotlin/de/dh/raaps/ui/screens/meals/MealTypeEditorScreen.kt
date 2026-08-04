package de.dh.raaps.ui.screens.meals

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTypeEditorScreen(
    viewModel: MealTypeEditorViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(
                    if (uiState.id == null) stringResource(R.string.meal_type_editor_title_new)
                    else stringResource(R.string.meal_type_editor_title_edit)
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.cd_navigate_up)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(onNavigateUp) },
                        enabled = uiState.isValid && !uiState.isSaving
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Speichern")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text(stringResource(R.string.meal_type_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            OutlinedTextField(
                value = uiState.cat,
                onValueChange = { viewModel.onCatChange(it) },
                label = { Text(stringResource(R.string.meal_type_cat_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text(
                text = stringResource(R.string.meal_type_components_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val totalWeight = uiState.components.sumOf { it.weight }
            if (totalWeight != 100) {
                Text(
                    text = stringResource(R.string.error_meal_type_weights_sum),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.components) { index, component ->
                    ComponentItem(
                        component = component,
                        onUpdate = { updated ->
                            val newList = uiState.components.toMutableList()
                            newList[index] = updated
                            viewModel.onComponentsChange(newList)
                        },
                        onDelete = {
                            val newList = uiState.components.toMutableList()
                            newList.removeAt(index)
                            viewModel.onComponentsChange(newList)
                        }
                    )
                }
                item {
                    Button(
                        onClick = {
                            val newList = uiState.components.toMutableList()
                            newList.add(CarbCurveComponentData(0, Minutes(60)))
                            viewModel.onComponentsChange(newList)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Komponente hinzufügen")
                    }
                }
            }
        }
    }
}

@Composable
fun ComponentItem(
    component: CarbCurveComponentData,
    onUpdate: (CarbCurveComponentData) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = component.weight.toString(),
                onValueChange = { newVal ->
                    newVal.toIntOrNull()?.let { onUpdate(component.copy(weight = it)) }
                },
                label = { Text(stringResource(R.string.meal_type_weight_label)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = component.peakMinutes.value.toString(),
                onValueChange = { newVal ->
                    newVal.toIntOrNull()?.let { onUpdate(component.copy(peakMinutes = Minutes(it.toShort()))) }
                },
                label = { Text(stringResource(R.string.meal_type_peak_label)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MealTypeEditorPreview() {
    AppTheme {
        // No easy way to preview with VM, but can preview content if extracted
    }
}