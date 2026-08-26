package de.dh.raaps.ui.screens.mealbolus

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.BOLUS_MAX
import de.dh.raaps.common.model.BOLUS_MIN
import de.dh.raaps.common.model.CARBS_KE_MAX
import de.dh.raaps.common.model.CARBS_KE_MIN
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.ID_MEAL_FAST
import de.dh.raaps.common.model.ID_MEAL_HIGH_FAT
import de.dh.raaps.common.model.ID_MEAL_SLOW
import de.dh.raaps.common.model.ID_MEAL_STANDARD
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.BolusProjections
import de.dh.raaps.core.aps.TreatmentLock
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.DefaultSteppingStrategy
import de.dh.raaps.ui.common.LocalGlucoseUnit
import de.dh.raaps.ui.common.ModuloSteppingStrategy
import de.dh.raaps.ui.common.ValueDisplayStrategy
import de.dh.raaps.ui.common.carbsGramsValue
import de.dh.raaps.ui.common.carbsKeUnitLabel
import de.dh.raaps.ui.common.composables.AppColorBlue
import de.dh.raaps.ui.common.composables.EditableValueStepper
import de.dh.raaps.ui.common.composables.ImageCaptionWithSwitch
import de.dh.raaps.ui.common.composables.LightGreenA700
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.composables.Red
import de.dh.raaps.ui.common.composables.StepperDefaults
import de.dh.raaps.ui.common.composables.TimeStepper
import de.dh.raaps.ui.common.composables.TimeStepperDefaults
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
fun MealCorrectionBolusScreen(
    viewModel: MealCorrectionBolusViewModel,
    treatmentLock: TreatmentLock,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    MealCorrectionBolusContent(
        uiState = uiState,
        onCarbsChange = { viewModel.onCarbsChange(it) },
        onMealTimeChange = { viewModel.onMealTimeChange(it) },
        onMealTypeChange = { viewModel.onMealTypeChange(it) },
        onManualBolusChange = { viewModel.onManualBolusChange(it) },
        onPlannedInsulinTimeChange = { index, time -> viewModel.onPlannedInsulinTimeChange(index, time) },
        onToggleInsulinPlan = { viewModel.toggleInsulinPlanExpanded() },
        onToggleMealReminder = { viewModel.onToggleMealReminder() },
        onRefreshProjections = { viewModel.onRefreshProjections() },
        onClose = onNavigateUp,
        onSubmit = { viewModel.submit(treatmentLock, onNavigateUp) }
    )
}

@Composable
fun MealCorrectionBolusContent(
    uiState: MealCorrectionBolusUiState,
    onCarbsChange: (Double) -> Unit,
    onMealTimeChange: (Timestamp) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onManualBolusChange: (Double) -> Unit,
    onPlannedInsulinTimeChange: (Int, Timestamp) -> Unit,
    onToggleInsulinPlan: () -> Unit,
    onToggleMealReminder: () -> Unit,
    onRefreshProjections: () -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit
) {
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
        MealCorrectionBolusContextInfo(uiState = uiState, onRefresh = onRefreshProjections)

        if (uiState.showCloseBanner) {
            CloseScreenBanner(onClose = onClose)
        }

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
                        text = stringResource(R.string.meal_correction_bolus_carbs_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    EditableValueStepper(
                        currentValue = uiState.input.carbsKe,
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

                if (uiState.input.carbsKe > 0.0) {
                    FoodTypeSelector(
                        mealTypes = uiState.mealTypes,
                        selectedType = uiState.input.selectedMealType,
                        onTypeSelected = onMealTypeChange,
                        isMandatory = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.meal_correction_bolus_meal_time_label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        TimeStepper(
                            currentTime = uiState.input.mealTimestamp,
                            onTimeChange = onMealTimeChange,
                            modifier = Modifier.fillMaxWidth(),
                            style = TimeStepperDefaults.defaultStyle()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.approx_time_format, time(uiState.input.mealTimestamp)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    ImageCaptionWithSwitch(
                        imageVector = Icons.Default.Notifications,
                        text = stringResource(R.string.meal_correction_bolus_reminder_label),
                        checked = uiState.isMealReminderEnabled,
                        onCheckedChange = { onToggleMealReminder() },
                        modifier = Modifier.fillMaxWidth()
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
                        text = stringResource(R.string.meal_correction_bolus_insulin_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))

                    CalculationDetailsSelector(
                        uiState = uiState,
                        onResultClick = { onManualBolusChange(uiState.calculation.proposedTotal.iu) }
                    )
                    EditableValueStepper(
                        currentValue = uiState.input.manualBolus.iu,
                        onValueChange = onManualBolusChange,
                        minValue = BOLUS_MIN,
                        maxValue = BOLUS_MAX,
                        steppingStrategy = ModuloSteppingStrategy(0.1), // 0.1 U steps
                        displayStrategy = object : ValueDisplayStrategy {
                            override fun format(value: Double): String =
                                String.format(Locale.getDefault(), "%.2f", value)

                            override fun color(value: Double): Color = Color.Unspecified
                        },
                        suffix = " ${insulinUnitLabel()}",
                        style = StepperDefaults.defaultStyle()
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
        val isInputValid = if (uiState.input.carbsKe > 0.0) {
            uiState.input.selectedMealType != null
        } else {
            uiState.input.manualBolus > InsulinAmount.ZERO
        }

        PrimaryButton(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.submissionStatus == SubmissionStatus.NotSubmitted && isInputValid
        ) {
            Text(
                if (uiState.submissionStatus == SubmissionStatus.Success) stringResource(R.string.meal_correction_bolus_administer_button_submitted)
                else stringResource(R.string.meal_correction_bolus_administer_button)
            )
        }
    }
}

@Composable
fun CloseScreenBanner(
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.meal_correction_bolus_close_banner_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.meal_correction_bolus_close_banner_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = stringResource(R.string.meal_correction_bolus_close_banner_button),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationDetailsSelector(
    uiState: MealCorrectionBolusUiState,
    onResultClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            if (!expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                ) {
                    Text(
                        text = stringResource(R.string.meal_correction_bolus_calc_result_label, insulinValue(uiState.calculation.proposedTotal.iu)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onResultClick() }
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(CommonR.string.cd_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = false },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.meal_correction_bolus_calculation_title),
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
                        R.string.meal_correction_bolus_calc_factors_label,
                        isfValue(uiState.isf),
                        crValue(uiState.cr, withUnit = false)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )

                val bgProjection = uiState.projections.bg
                if (bgProjection.isInvalid()) {
                    Text(
                        text = stringResource(R.string.meal_correction_bolus_calc_no_bg_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                } else if (bgProjection <= uiState.lowThreshold) {
                    Text(
                        text = stringResource(
                            R.string.meal_correction_bolus_calc_low_bg_warning,
                            glucoseValue(uiState.lowThreshold, withUnit = true),
                            time(uiState.projections.timestamp)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.meal_correction_bolus_calc_meal_part, insulinValue(uiState.calculation.mealPart.iu, signed = true)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.meal_correction_bolus_calc_correction_part, insulinValue(uiState.calculation.correctionPart.iu, signed = true)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.calculation.iobPart > InsulinAmount.ZERO) {
                        Text(
                            text = stringResource(R.string.meal_correction_bolus_calc_iob_part, insulinValue(-uiState.calculation.iobPart.iu, signed = true)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (uiState.calculation.cobPart > InsulinAmount.ZERO) {
                        Text(
                            text = stringResource(R.string.meal_correction_bolus_calc_cob_part, insulinValue(uiState.calculation.cobPart.iu, signed = true)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (uiState.calculation.futureCarbsPart > InsulinAmount.ZERO) {
                        Text(
                            text = stringResource(R.string.meal_correction_bolus_calc_future_carbs_part, insulinValue(uiState.calculation.futureCarbsPart.iu, signed = true)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (uiState.calculation.deferredBolusPart > InsulinAmount.ZERO) {
                        Text(
                            text = stringResource(R.string.meal_correction_bolus_calc_deferred_part, insulinValue(-uiState.calculation.deferredBolusPart.iu, signed = true)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.meal_correction_bolus_calc_result_label, insulinValue(uiState.calculation.proposedTotal.iu)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResultClick() }
                )
            }
        }
    }
}

@Composable
fun MealCorrectionBolusContextInfo(
    uiState: MealCorrectionBolusUiState,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (uiState.projections.isProjected) {
                        Text(
                            text = stringResource(R.string.approx_prefix),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }

                    val displayBgValue = uiState.projections.bg
                    val bgText = glucoseValue(displayBgValue, default = "??")
                    val textColor = if (displayBgValue.isInvalid()) {
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
                    text = stringResource(R.string.at_time_format, time(uiState.projections.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.meal_correction_bolus_active_carbs_format, carbsGramsValue(uiState.projections.cob)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.meal_correction_bolus_active_insulin_format, insulinValue(uiState.projections.iob.iu)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.isProjectionsStale) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_calculations),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinPlanCard(
    plan: List<PlannedInsulinUiModel>,
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
                        text = stringResource(R.string.meal_correction_bolus_insulin_plan_title),
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

                            val lastOffset = plan.lastOrNull()?.timeFromNow ?: Minutes(0)
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
                                    text = if (item.partWeight == null)
                                        stringResource(R.string.insulin_part_none)
                                    else
                                        stringResource(R.string.insulin_part_n, index + 1, item.partWeight),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = insulinValue(item.amount.iu),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TimeStepper(
                                    currentTime = item.timestamp,
                                    onTimeChange = { onTimeChange(index, it) },
                                    showPreposition = true,
                                    forceSign = false,
                                    style = TimeStepperDefaults.smallStyle()
                                )
                                Text(
                                    text = time(item.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
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
fun MealCorrectionBolusZeroKePreview() {
    AppTheme {
        CompositionLocalProvider(LocalGlucoseUnit provides GlucoseUnit.MG_DL) {
            Surface {
                MealCorrectionBolusContent(
                    uiState = MealCorrectionBolusUiState(
                        isLoading = false,
                        input = MealInput(
                            carbsKe = 0.0,
                            manualBolus = InsulinAmount(0.8),
                        ),
                        mealTypes = emptyList(),
                        projections = BolusProjections(
                            bg = BgValue(140),
                        ),
                        targetBg = BgValue(100),
                        isf = BgDelta.fromMgDl(50),
                        cr = 10.0,
                        calculation = BolusCalculationDetails(
                            proposedTotal = InsulinAmount(0.8)
                        ),
                        submissionStatus = SubmissionStatus.NotSubmitted
                    ),
                    onCarbsChange = {},
                    onMealTimeChange = {},
                    onMealTypeChange = {},
                    onManualBolusChange = {},
                    onPlannedInsulinTimeChange = { _, _ -> },
                    onToggleInsulinPlan = {},
                    onToggleMealReminder = {},
                    onRefreshProjections = {},
                    onClose = {},
                    onSubmit = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Default Mode")
@Preview(showBackground = true, name = "Default Mode - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MealCorrectionBolusDefaultPreview() {
    val sampleMealTypes = listOf(
        MealType(id = ID_MEAL_FAST, name = "Schnell", components = listOf(CarbCurveComponentData(100, Minutes(30))), cat = Minutes(120)),
        MealType(id = ID_MEAL_STANDARD, name = "Standard", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
        MealType(id = ID_MEAL_HIGH_FAT, name = "Fettreiches Essen", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
        MealType(id = ID_MEAL_SLOW, name = "Langsam", components = listOf(CarbCurveComponentData(100, Minutes(90))), cat = Minutes(240)),
    )
    AppTheme {
        CompositionLocalProvider(LocalGlucoseUnit provides GlucoseUnit.MG_DL) {
            Surface {
                MealCorrectionBolusContent(
                    uiState = MealCorrectionBolusUiState(
                        isLoading = false,
                        input = MealInput(
                            carbsKe = 4.5,
                            selectedMealType = sampleMealTypes[0],
                            manualBolus = InsulinAmount(5.3),
                            mealTimestamp = Timestamp.now().plusMinutes(15),
                        ),
                        mealTypes = sampleMealTypes,
                        projections = BolusProjections(
                            timestamp = Timestamp.now().plusMinutes(15),
                            isProjected = true,
                            bg = BgValue(145),
                            iob = InsulinAmount(1.2),
                            cob = 25.0,
                            futureCarbs = 10.0
                        ),
                        targetBg = BgValue(100),
                        isf = BgDelta.fromMgDl(50),
                        cr = 10.0,
                        calculation = BolusCalculationDetails(
                            mealPart = InsulinAmount(4.5),
                            correctionPart = InsulinAmount(0.8),
                            proposedTotal = InsulinAmount(5.3),
                        ),
                        submissionStatus = SubmissionStatus.NotSubmitted
                    ),
                    onCarbsChange = {},
                    onMealTimeChange = {},
                    onMealTypeChange = {},
                    onManualBolusChange = {},
                    onPlannedInsulinTimeChange = { _, _ -> },
                    onToggleInsulinPlan = {},
                    onToggleMealReminder = {},
                    onRefreshProjections = {},
                    onClose = {},
                    onSubmit = {}
                )
            }
        }
    }
}