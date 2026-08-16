package de.dh.raaps.ui.screens.mealbolus

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.dh.raaps.common.model.BOLUS_MAX
import de.dh.raaps.common.model.BOLUS_MIN
import de.dh.raaps.common.model.CARBS_KE_MAX
import de.dh.raaps.common.model.CARBS_KE_MIN
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.PlannedInsulin
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.DefaultSteppingStrategy
import de.dh.raaps.ui.common.LocalGlucoseUnit
import de.dh.raaps.ui.common.ValueDisplayStrategy
import de.dh.raaps.ui.common.carbsGramsValue
import de.dh.raaps.ui.common.carbsKeUnitLabel
import de.dh.raaps.ui.common.composables.AppColorBlue
import de.dh.raaps.ui.common.composables.EditableValueStepper
import de.dh.raaps.ui.common.composables.LightGreenA700
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.composables.Red
import de.dh.raaps.ui.common.composables.StepperDefaults
import de.dh.raaps.ui.common.composables.TimeStepper
import de.dh.raaps.ui.common.composables.Yellow
import de.dh.raaps.ui.common.composables.contentScrollIndicator
import de.dh.raaps.ui.common.crValue
import de.dh.raaps.ui.common.glucoseUnitLabel
import de.dh.raaps.ui.common.glucoseValue
import de.dh.raaps.ui.common.insulinUnitLabel
import de.dh.raaps.ui.common.insulinValue
import de.dh.raaps.ui.common.isfValue
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.common.time
import de.dh.raaps.ui.common.withinTimeDescription
import de.dh.raaps.ui.controls.meal.FoodTypeSelector
import java.util.Locale
import de.dh.raaps.common.R as CommonR

@Composable
fun MealBolusScreen(
    viewModel: MealBolusViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    MealBolusContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onCarbsChange = { viewModel.onCarbsChange(it) },
        onMealTimeChange = { viewModel.onMealTimeChange(it) },
        onMealTypeChange = { viewModel.onMealTypeChange(it) },
        onManualBolusChange = { viewModel.onManualBolusChange(it) },
        onPlannedInsulinTimeChange = { index, time -> viewModel.onPlannedInsulinTimeChange(index, time) },
        onToggleInsulinPlan = { viewModel.toggleInsulinPlanExpanded() },
        onSubmit = { viewModel.submit(onNavigateUp) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealBolusContent(
    uiState: MealBolusUiState,
    onNavigateUp: () -> Unit,
    onCarbsChange: (Double) -> Unit,
    onMealTimeChange: (Timestamp) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onManualBolusChange: (Double) -> Unit,
    onPlannedInsulinTimeChange: (Int, Timestamp) -> Unit,
    onToggleInsulinPlan: () -> Unit,
    onSubmit: () -> Unit
) {
    if (uiState.isBusy) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier
                    .size(150.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.meal_bolus_busy_system),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.meal_add_screen_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.cd_close))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header with BG, IOB, COB (Fixed)
            MealBolusHeader(uiState)

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .contentScrollIndicator(scrollState)
                    .verticalScroll(scrollState)
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.lockError) {
                    LockErrorCard(uiState.lockBusyOwner, onNavigateUp)
                } else {
                    // Mahlzeit Card (Carbs + Food Type + Meal Time)
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
                                    text = stringResource(R.string.meal_bolus_carbs_label),
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
                                    style = StepperDefaults.smallStyle()
                                )
                            }

                            if (uiState.carbsKe > 0.0) {
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
                                        style = StepperDefaults.smallStyle()
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.at_time_format, time(uiState.mealTimestamp)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                FoodTypeSelector(
                                    mealTypes = uiState.mealTypes,
                                    selectedType = uiState.selectedMealType,
                                    onTypeSelected = onMealTypeChange,
                                    isMandatory = true
                                )
                            }
                        }
                    }

                    // Insulin Card (Final Insulin Stepper)
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
                                    text = stringResource(R.string.meal_bolus_insulin_label),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(8.dp))

                                CalculationDetailsSelector(uiState = uiState)
                                EditableValueStepper(
                                    currentValue = uiState.manualBolus.iu,
                                    onValueChange = onManualBolusChange,
                                    minValue = BOLUS_MIN,
                                    maxValue = BOLUS_MAX,
                                    steppingStrategy = DefaultSteppingStrategy(0.1), // 0.1 U steps
                                    displayStrategy = object : ValueDisplayStrategy {
                                        override fun format(value: Double): String =
                                            String.format(Locale.getDefault(), "%.2f", value)

                                        override fun color(value: Double): Color = Color.Unspecified
                                    },
                                    suffix = " ${insulinUnitLabel()}",
                                    style = StepperDefaults.smallStyle()
                                )
                            }
                        }
                    }

                    // Insulin Plan Card
                    if (uiState.insulinPlan.isNotEmpty()) {
                        InsulinPlanCard(
                            plan = uiState.insulinPlan,
                            isExpanded = uiState.isInsulinPlanExpanded,
                            onToggleExpanded = onToggleInsulinPlan,
                            onTimeChange = onPlannedInsulinTimeChange
                        )
                    }

                    // Bottom Button
                    val isInputValid = if (uiState.carbsKe > 0.0) {
                        uiState.selectedMealType != null
                    } else {
                        uiState.manualBolus > InsulinAmount.ZERO
                    }

                    PrimaryButton(
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSubmitting && isInputValid
                    ) {
                        Text(stringResource(R.string.meal_bolus_administer_button))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationDetailsSelector(
    uiState: MealBolusUiState
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, AppColorBlue.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            if (!expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.meal_bolus_calc_result_label, insulinValue(uiState.proposedBolus.iu)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(CommonR.string.cd_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.meal_bolus_calculation_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(CommonR.string.cd_collapse),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))

                Text(
                    stringResource(
                        R.string.meal_bolus_calc_factors_label,
                        isfValue(BgDelta(uiState.isf.toShort())),
                        crValue(uiState.cr, withUnit = false)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )

                if (uiState.referenceBg == null) {
                    Text(
                        text = stringResource(R.string.meal_bolus_calc_no_bg_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                } else if (uiState.referenceBg.mgdl <= uiState.lowThreshold.mgdl) {
                    Text(
                        text = stringResource(
                            R.string.meal_bolus_calc_low_bg_warning,
                            glucoseValue(uiState.lowThreshold, withUnit = true),
                            time(uiState.referenceTimestamp)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.meal_bolus_calc_meal_part, insulinValue(uiState.mealPart.iu, signed = true)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.meal_bolus_calc_correction_part, insulinValue(uiState.correctionPart.iu, signed = true)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.iobPart > InsulinAmount.ZERO) {
                        Text(
                            text = stringResource(R.string.meal_bolus_calc_iob_part, insulinValue(-uiState.iobPart.iu, signed = true)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (uiState.cobPart > InsulinAmount.ZERO) {
                        Text(
                            text = stringResource(R.string.meal_bolus_calc_cob_part, insulinValue(uiState.cobPart.iu, signed = true)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.meal_bolus_calc_result_label, insulinValue(uiState.proposedBolus.iu)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun MealBolusHeader(
    uiState: MealBolusUiState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.isProjected) {
                    Text(
                        text = stringResource(R.string.approx_prefix),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                val displayBgValue = uiState.referenceBg
                val bgText = glucoseValue(displayBgValue, default = "??")
                val textColor = if (displayBgValue == null || displayBgValue.isInvalid()) {
                    Color.Gray
                } else when {
                    displayBgValue.mgdl < 70 -> Red
                    displayBgValue.mgdl < 180 -> LightGreenA700
                    else -> Yellow
                }
                Text(
                    text = bgText,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                Text(
                    text = glucoseUnitLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray,
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 12.dp)
                )
            }

                Text(
                    text = stringResource(R.string.at_time_format, time(uiState.referenceTimestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.meal_bolus_active_carbs_format, carbsGramsValue(uiState.projectedCob)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.meal_bolus_active_insulin_format, insulinValue(uiState.projectedIob.iu)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LockErrorCard(owner: String?, onNavigateUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Red.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.meal_bolus_lock_error_message, owner ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(onClick = onNavigateUp) {
                Text(stringResource(id = CommonR.string.cd_navigate_up))
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinPlanCard(
    plan: List<PlannedInsulin>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onTimeChange: (Int, Timestamp) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggleExpanded,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, AppColorBlue.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.meal_bolus_insulin_plan_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!isExpanded) {
                        val planSummary = buildString {
                            plan.forEachIndexed { index, item ->
                                append(insulinValue(item.amount.iu))
                                if (index < plan.size - 1) append(" + ")
                            }

                            val lastOffset = plan.lastOrNull()?.offsetMinutes ?: 0
                            append(" (")
                            append(withinTimeDescription(lastOffset))
                            append(")")
                        }
                        Text(
                            text = planSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (isExpanded) {
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    plan.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = insulinValue(item.amount.iu),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.at_time_format, time(item.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TimeStepper(
                                currentTime = item.timestamp,
                                onTimeChange = { onTimeChange(index, it) },
                                style = StepperDefaults.compactStyle().copy(valueWidth = 70.dp)
                            )
                        }
                        if (index < plan.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = LocalContentColor.current.copy(alpha = 0.12f)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true, name = "0 KE Mode")
@Composable
fun MealBolusZeroKePreview() {
    AppTheme {
        CompositionLocalProvider(LocalGlucoseUnit provides GlucoseUnit.MG_DL) {
            MealBolusContent(
                uiState = MealBolusUiState(
                    isLoading = false,
                    carbsKe = 0.0,
                    mealTypes = emptyList(),
                    selectedMealType = null,
                    referenceBg = BgValue(140),
                    referenceTimestamp = Timestamp.now(),
                    isProjected = false,
                    targetBg = BgValue(100),
                    isf = 50,
                    cr = 10.0,
                    proposedBolus = InsulinAmount(0.8),
                    manualBolus = InsulinAmount(0.8)
                ),
                onNavigateUp = {},
                onCarbsChange = {},
                onMealTimeChange = {},
                onMealTypeChange = {},
                onManualBolusChange = {},
                onPlannedInsulinTimeChange = { _, _ -> },
                onToggleInsulinPlan = {},
                onSubmit = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Default Mode")
@Preview(showBackground = true, name = "Default Mode - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MealBolusDefaultPreview() {
    val sampleMealTypes = listOf(
        MealType(name = "Schnelle KE", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
        MealType(name = "Standard-Essen", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
        MealType(name = "Fettreiches Essen", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
        MealType(name = "Langsames Essen", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
    )
    AppTheme {
        CompositionLocalProvider(LocalGlucoseUnit provides GlucoseUnit.MG_DL) {
            MealBolusContent(
                uiState = MealBolusUiState(
                    isLoading = false,
                    carbsKe = 4.5,
                    mealTypes = sampleMealTypes,
                    selectedMealType = sampleMealTypes[0],
                    referenceBg = BgValue(145),
                    referenceTimestamp = Timestamp.now().plusMinutes(15),
                    isProjected = true,
                    targetBg = BgValue(100),
                    isf = 50,
                    cr = 10.0,
                    iob = InsulinAmount(1.2),
                    cob = 25.0,
                    projectedIob = InsulinAmount(1.1),
                    projectedCob = 20.0,
                    mealPart = InsulinAmount(4.5),
                    correctionPart = InsulinAmount(0.8),
                    proposedBolus = InsulinAmount(5.3),
                    manualBolus = InsulinAmount(5.3)
                ),
                onNavigateUp = {},
                onCarbsChange = {},
                onMealTimeChange = {},
                onMealTypeChange = {},
                onManualBolusChange = {},
                onPlannedInsulinTimeChange = { _, _ -> },
                onToggleInsulinPlan = {},
                onSubmit = {}
            )
        }
    }
}