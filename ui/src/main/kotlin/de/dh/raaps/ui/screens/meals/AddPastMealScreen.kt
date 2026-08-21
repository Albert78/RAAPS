package de.dh.raaps.ui.screens.meals

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R as CommonR
import de.dh.raaps.common.model.CARBS_KE_MAX
import de.dh.raaps.common.model.CARBS_KE_MIN
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.ID_MEAL_FAST
import de.dh.raaps.common.model.ID_MEAL_SLOW
import de.dh.raaps.common.model.ID_MEAL_STANDARD
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.DefaultSteppingStrategy
import de.dh.raaps.ui.common.LocalGlucoseUnit
import de.dh.raaps.ui.common.ValueDisplayStrategy
import de.dh.raaps.ui.common.carbsKeUnitLabel
import de.dh.raaps.ui.common.composables.AppColorBlue
import de.dh.raaps.ui.common.composables.EditableValueStepper
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.composables.StepperDefaults
import de.dh.raaps.ui.common.composables.TimeStepper
import de.dh.raaps.ui.common.composables.TimeStepperDefaults
import de.dh.raaps.ui.common.composables.contentScrollIndicator
import de.dh.raaps.ui.common.composables.screenTitle
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.common.time
import de.dh.raaps.ui.controls.meal.FoodTypeSelector
import java.util.Locale

@Composable
fun AddPastMealScreen(
    viewModel: AddPastMealViewModel,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    AddPastMealContent(
        uiState = uiState,
        onCarbsChange = { viewModel.onCarbsChange(it) },
        onMealTimeChange = { viewModel.onMealTimeChange(it) },
        onMealTypeChange = { viewModel.onMealTypeChange(it) },
        onNavigateUp = onNavigateUp,
        onSubmit = { viewModel.submit(onNavigateUp) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPastMealContent(
    uiState: AddPastMealUiState,
    onCarbsChange: (Double) -> Unit,
    onMealTimeChange: (Timestamp) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onNavigateUp: () -> Unit,
    onSubmit: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.add_past_meal_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = CommonR.string.cd_navigate_up)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .contentScrollIndicator(scrollState)
                .verticalScroll(scrollState)
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Meal Card (Carbs + Food Type + Meal Time)
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
                            text = stringResource(R.string.add_past_meal_carbs_label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        EditableValueStepper(
                            currentValue = uiState.carbsKe,
                            onValueChange = onCarbsChange,
                            minValue = CARBS_KE_MIN,
                            maxValue = CARBS_KE_MAX,
                            steppingStrategy = DefaultSteppingStrategy(0.5), // 0.5 KE steps
                            displayStrategy = object : ValueDisplayStrategy {
                                override fun format(value: Double): String =
                                    String.format(Locale.getDefault(), "%.1f", value)

                                override fun color(value: Double): Color = Color.Unspecified
                            },
                            suffix = " ${carbsKeUnitLabel()}",
                            style = StepperDefaults.defaultStyle()
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.meal_bolus_meal_time_label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        TimeStepper(
                            currentTime = uiState.mealTimestamp,
                            onTimeChange = onMealTimeChange,
                            modifier = Modifier.fillMaxWidth(),
                            style = TimeStepperDefaults.defaultStyle()
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.approx_time_format, time(uiState.mealTimestamp)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.isTimeValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!uiState.isTimeValid) {
                            Text(
                                text = stringResource(R.string.meal_past_time_validation_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    FoodTypeSelector(
                        mealTypes = uiState.mealTypes,
                        selectedType = uiState.selectedMealType,
                        onTypeSelected = onMealTypeChange,
                        isMandatory = true
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = (!uiState.isSubmitting) && uiState.isTimeValid && (uiState.carbsKe > 0) && (uiState.selectedMealType != null)
            ) {
                Text(stringResource(R.string.add_past_meal_save_button))
            }
        }
    }
}

@Preview(showBackground = true, name = "Default Mode")
@Preview(showBackground = true, name = "Default Mode - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddPastMealPreview() {
    val sampleMealTypes = listOf(
        MealType(id = ID_MEAL_FAST, name = "Schnell", components = listOf(CarbCurveComponentData(100, Minutes(30))), cat = Minutes(120)),
        MealType(id = ID_MEAL_STANDARD, name = "Standard", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
        MealType(id = ID_MEAL_SLOW, name = "Langsam", components = listOf(CarbCurveComponentData(100, Minutes(90))), cat = Minutes(240)),
    )
    AppTheme {
        CompositionLocalProvider(LocalGlucoseUnit provides GlucoseUnit.MG_DL) {
            Surface {
                AddPastMealContent(
                    uiState = AddPastMealUiState(
                        isLoading = false,
                        carbsKe = 3.5,
                        mealTypes = sampleMealTypes,
                        selectedMealType = sampleMealTypes[1],
                        mealTimestamp = Timestamp.now().minusMinutes(45),
                        isTimeValid = true
                    ),
                    onCarbsChange = {},
                    onMealTimeChange = {},
                    onMealTypeChange = {},
                    onNavigateUp = {},
                    onSubmit = {}
                )
            }
        }
    }
}