package de.dh.raaps.ui.screens.mealbolus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.ui.DefaultSteppingStrategy
import de.dh.raaps.common.ui.DefaultValueDisplayStrategy
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealBolusScreen(
    viewModel: MealBolusViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isEditMode) stringResource(R.string.meal_edit_screen_title)
                        else stringResource(R.string.meal_bolus_screen_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { focusManager.clearFocus() }
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (uiState.isEditMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.meal_edit_warning_bolus),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // KE Stepper
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.meal_bolus_carbs_label),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                // Convert to Int (10x) for EditableValueStepper
                EditableValueStepper(
                    currentValue = (uiState.carbsKe * 10).toInt(),
                    onValueChange = { viewModel.onCarbsChange(it / 10.0) },
                    steppingStrategy = DefaultSteppingStrategy(5), // 0.5 KE steps
                    displayStrategy = object : de.dh.raaps.common.ui.ValueDisplayStrategy {
                        override fun format(value: Int): String = String.format("%.1f", value / 10.0)
                        override fun color(value: Int): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
                    }
                )
            }

            // Food Type RadioBox
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.meal_bolus_food_type_label),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                FoodTypeSelector(
                    mealTypes = uiState.mealTypes,
                    selectedType = uiState.selectedMealType,
                    onTypeSelected = { viewModel.onMealTypeChange(it) }
                )
            }

            // Calculation Details
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.meal_bolus_calculation_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    Text(stringResource(R.string.meal_bolus_calc_bg_label, uiState.currentBg ?: uiState.targetBg))
                    Text(stringResource(R.string.meal_bolus_calc_factors_label, uiState.isf, uiState.cr))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.meal_bolus_calc_meal_part, uiState.mealPart),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.meal_bolus_calc_correction_part, uiState.correctionPart),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (uiState.iobPart > 0) {
                            Text(
                                text = stringResource(R.string.meal_bolus_calc_iob_part, uiState.iobPart),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (uiState.cobPart > 0) {
                            Text(
                                text = stringResource(R.string.meal_bolus_calc_cob_part, uiState.cobPart),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.meal_bolus_calc_result_label, uiState.proposedBolus),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Final Insulin Stepper
            if (!uiState.isEditMode) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.meal_bolus_insulin_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    EditableValueStepper(
                        currentValue = (uiState.manualBolus * 100).toInt(),
                        onValueChange = { viewModel.onManualBolusChange(it / 100.0) },
                        steppingStrategy = DefaultSteppingStrategy(10), // 0.1 U steps
                        displayStrategy = object : de.dh.raaps.common.ui.ValueDisplayStrategy {
                            override fun format(value: Int): String = String.format("%.2f", value / 100.0)
                            override fun color(value: Int): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
                        }
                    )
                }
            }

            // Bottom Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateUp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.meal_bolus_cancel_button))
                }
                Button(
                    onClick = { viewModel.submit(onNavigateUp) },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSubmitting
                ) {
                    Text(
                        if (uiState.isEditMode) stringResource(R.string.meal_edit_save_button)
                        else stringResource(R.string.meal_bolus_ok_button)
                    )
                }
            }
        }
    }
}

@Composable
fun FoodTypeSelector(
    mealTypes: List<MealType>,
    selectedType: MealType?,
    onTypeSelected: (MealType) -> Unit
) {
    Column(Modifier.selectableGroup()) {
        mealTypes.forEach { type ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .selectable(
                        selected = (type == selectedType),
                        onClick = { onTypeSelected(type) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (type == selectedType),
                    onClick = null // null recommended for accessibility with screen readers
                )
                Text(
                    text = type.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}