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
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.CARBS_KE_MAX
import de.dh.raaps.common.model.CARBS_KE_MIN
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.DefaultSteppingStrategy
import de.dh.raaps.ui.common.ValueDisplayStrategy
import de.dh.raaps.ui.common.carbsKeUnitLabel
import de.dh.raaps.ui.common.composables.AppColorBlue
import de.dh.raaps.ui.common.composables.EditableValueStepper
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.composables.StepperDefaults
import de.dh.raaps.ui.common.composables.StepperStyle
import de.dh.raaps.ui.common.composables.WheelPickerDialog
import de.dh.raaps.ui.common.icons.Icon_Minus
import de.dh.raaps.ui.common.icons.Icon_Plus
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

@Composable
fun AbsoluteTimeStepper(
    currentTime: Timestamp,
    onTimeChange: (Timestamp) -> Unit,
    modifier: Modifier = Modifier,
    stepMinutes: Int = 5,
    style: StepperStyle = StepperDefaults.defaultStyle()
) {
    var showPickerDialog by remember { mutableStateOf(false) }

    val now = Timestamp.now()
    val minTime = now - Minutes(120)
    val maxTime = now + Minutes(30)

    val allowedTimestamps = remember(now) {
        val stepMs = stepMinutes * 60000L
        // Align baseNow to the nearest multiple of 5 minutes relative to epoch
        // (Works for most timezones as they are offset by multiples of 5 mins)
        val baseNow = (now.ms / stepMs) * stepMs
        val startMs = baseNow - 120 * 60000
        val endMs = baseNow + 30 * 60000
        (startMs..endMs step stepMs).map { Timestamp(it) }
    }

    if (showPickerDialog) {
        // Find nearest allowed timestamp for initial selection
        val initialSelection = allowedTimestamps.minByOrNull { kotlin.math.abs(it.ms - currentTime.ms) } ?: currentTime
        
        WheelPickerDialog(
            initialValue = initialSelection,
            options = allowedTimestamps,
            onValueSelected = { onTimeChange(it) },
            onDismiss = { showPickerDialog = false },
            labelProvider = { time(it) },
            width = 150.dp
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = {
                // Snap to previous multiple of 5
                val currentMs = currentTime.ms
                val stepMs = stepMinutes * 60000L
                val remainder = currentMs % stepMs
                val nextTime = if (remainder == 0L) currentTime - Minutes(stepMinutes.toShort()) 
                               else Timestamp(currentMs - remainder)
                onTimeChange(nextTime)
            },
            modifier = Modifier.size(style.buttonSize),
            enabled = currentTime > minTime
        ) {
            Icon(Icon_Minus, contentDescription = null, modifier = Modifier.size(style.buttonSize * 0.5f))
        }

        Spacer(Modifier.width(style.spacing))

        Text(
            text = time(currentTime),
            style = style.textStyle,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(style.valueWidth)
                .clip(RoundedCornerShape(8.dp))
                .clickable { showPickerDialog = true }
                .padding(vertical = 4.dp)
        )

        Spacer(Modifier.width(style.spacing))

        IconButton(
            onClick = {
                // Snap to next multiple of 5
                val currentMs = currentTime.ms
                val stepMs = stepMinutes * 60000L
                val remainder = currentMs % stepMs
                val nextTime = if (remainder == 0L) currentTime + Minutes(stepMinutes.toShort())
                               else Timestamp(currentMs + (stepMs - remainder))
                onTimeChange(nextTime)
            },
            modifier = Modifier.size(style.buttonSize),
            enabled = currentTime < maxTime
        ) {
            Icon(Icon_Plus, contentDescription = null, modifier = Modifier.size(style.buttonSize * 0.5f))
        }
    }
}