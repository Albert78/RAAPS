package de.dh.raaps.ui.screens.meals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.CARBS_KE_MAX
import de.dh.raaps.common.model.CARBS_KE_MIN
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.DefaultSteppingStrategy
import de.dh.raaps.ui.common.ValueDisplayStrategy
import de.dh.raaps.ui.common.carbsKeUnitLabel
import de.dh.raaps.ui.common.composables.AbsoluteTimeStepper
import de.dh.raaps.ui.common.composables.AppColorBlue
import de.dh.raaps.ui.common.composables.EditableValueStepper
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.composables.StepperDefaults
import de.dh.raaps.ui.common.composables.StepperStyle
import de.dh.raaps.ui.common.composables.WheelPickerDialog
import de.dh.raaps.ui.common.icons.Icon_Minus
import de.dh.raaps.ui.common.icons.Icon_Plus
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.common.time
import de.dh.raaps.ui.controls.meal.FoodTypeSelector
import java.util.Locale
import de.dh.raaps.common.R as CommonR

@Composable
fun EditHistoricalMealScreen(
    viewModel: EditHistoricalMealViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    EditHistoricalMealContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onDelete = { viewModel.deleteMeal(onNavigateUp) },
        onCarbsChange = { viewModel.onCarbsChange(it) },
        onTimestampChange = { viewModel.onTimestampChange(it) },
        onMealTypeChange = { viewModel.onMealTypeChange(it) },
        onSave = { viewModel.saveChanges(onNavigateUp) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHistoricalMealContent(
    uiState: EditHistoricalMealUiState,
    onNavigateUp: () -> Unit,
    onDelete: () -> Unit,
    onCarbsChange: (Double) -> Unit,
    onTimestampChange: (Timestamp) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.meal_edit_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = CommonR.string.cd_navigate_up)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = CommonR.string.cd_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onSave, enabled = !uiState.isSaving) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(id = R.string.cd_save_profile)
                        )
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
            if (uiState.isLoading) {
                // Show nothing or a skeleton
            } else if (uiState.meal == null) {
                Text(text = stringResource(R.string.meal_not_found))
            } else {
                EditMealCard(
                    carbsKe = uiState.editedCarbsKe,
                    timestamp = uiState.editedTimestamp,
                    mealType = uiState.editedMealType,
                    mealTypes = uiState.mealTypes,
                    onCarbsChange = onCarbsChange,
                    onTimestampChange = onTimestampChange,
                    onMealTypeChange = onMealTypeChange
                )

                Spacer(modifier = Modifier.weight(1f))

                PrimaryButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    Text(text = stringResource(R.string.meal_edit_save_button))
                }
            }
        }
    }
}

@Composable
fun EditMealCard(
    carbsKe: Double,
    timestamp: Timestamp,
    mealType: MealType?,
    mealTypes: List<MealType>,
    onCarbsChange: (Double) -> Unit,
    onTimestampChange: (Timestamp) -> Unit,
    onMealTypeChange: (MealType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, AppColorBlue.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.meal_bolus_meal_time_label),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))

                AbsoluteTimeStepper(
                    currentTime = timestamp,
                    onTimeChange = onTimestampChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.meal_bolus_carbs_label),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                EditableValueStepper(
                    currentValue = carbsKe,
                    onValueChange = onCarbsChange,
                    minValue = CARBS_KE_MIN,
                    maxValue = CARBS_KE_MAX,
                    steppingStrategy = DefaultSteppingStrategy(0.5),
                    displayStrategy = object : ValueDisplayStrategy {
                        override fun format(value: Double): String =
                            String.format(Locale.getDefault(), "%.1f", value)

                        override fun color(value: Double): Color = Color.Unspecified
                    },
                    suffix = " ${carbsKeUnitLabel()}"
                )
            }

            FoodTypeSelector(
                mealTypes = mealTypes,
                selectedType = mealType,
                onTypeSelected = onMealTypeChange,
                isMandatory = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditHistoricalMealContentPreview() {
    val sampleMealType = MealType(
        name = "Standard",
        components = listOf(CarbCurveComponentData(weight = 100, peakMinutes = Minutes(45))),
        cat = Minutes(180)
    )
    val sampleMeal = MealEntry(
        timestamp = Timestamp.now(),
        carbGrams = 40.0,
        mealType = sampleMealType
    )
    val sampleUiState = EditHistoricalMealUiState(
        isLoading = false,
        meal = sampleMeal,
        editedCarbsKe = 4.0,
        editedTimestamp = Timestamp.now(),
        editedMealType = sampleMealType,
        mealTypes = listOf(sampleMealType),
        isSaving = false
    )

    AppTheme {
        EditHistoricalMealContent(
            uiState = sampleUiState,
            onNavigateUp = {},
            onDelete = {},
            onCarbsChange = {},
            onTimestampChange = {},
            onMealTypeChange = {},
            onSave = {}
        )
    }
}