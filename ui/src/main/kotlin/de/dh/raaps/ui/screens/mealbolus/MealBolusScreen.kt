package de.dh.raaps.ui.screens.mealbolus

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import de.dh.raaps.common.ui.composables.contentScrollIndicator
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.ui.DefaultSteppingStrategy
import de.dh.raaps.common.ui.ValueDisplayStrategy
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.composables.LightGreenA700
import de.dh.raaps.common.ui.composables.PrimaryButton
import de.dh.raaps.common.ui.composables.Red
import de.dh.raaps.common.ui.composables.Yellow
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.history.BgTrend
import de.dh.raaps.ui.controls.history.CurrentBgData
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.meal.FoodTypeSelector
import de.dh.raaps.ui.screens.meals.getIcon
import java.util.Locale

@Composable
fun MealBolusScreen(
    viewModel: MealBolusViewModel,
    historyViewModel: HistoryViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentBgUiState by historyViewModel.currentBgUiState.collectAsState()
    val iob by historyViewModel.iob.collectAsState()
    val cob by historyViewModel.cob.collectAsState()

    MealBolusContent(
        uiState = uiState,
        currentBgValue = currentBgUiState.currentBgValue,
        iob = iob,
        cob = cob,
        onNavigateUp = onNavigateUp,
        onCarbsChange = { viewModel.onCarbsChange(it) },
        onMealTypeChange = { viewModel.onMealTypeChange(it) },
        onManualBolusChange = { viewModel.onManualBolusChange(it) },
        onSubmit = { viewModel.submit(onNavigateUp) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealBolusContent(
    uiState: MealBolusUiState,
    currentBgValue: CurrentBgData?,
    iob: Double,
    cob: Double,
    onNavigateUp: () -> Unit,
    onCarbsChange: (Double) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onManualBolusChange: (Double) -> Unit,
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
                        text = "System belegt...",
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
                    Text(
                        if (uiState.isEditMode) stringResource(R.string.meal_edit_screen_title)
                        else stringResource(R.string.meal_add_screen_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.cd_close))
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
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header with BG, IOB, COB (Full width background)
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
                        val bgText = currentBgValue?.bgValue?.toString(currentBgValue.glucoseUnit) ?: "?"
                        val textColor = if (currentBgValue == null || (currentBgValue.isValueOld)) {
                            Color.Gray
                        } else when {
                            currentBgValue.bgValue.mgdl < 70 -> Red
                            currentBgValue.bgValue.mgdl < 180 -> LightGreenA700
                            else -> Yellow
                        }
                        Text(
                            text = bgText,
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = stringResource(R.string.glucose_unit_mgdl),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray,
                            modifier = Modifier
                                .align(Alignment.Bottom)
                                .padding(bottom = 12.dp)
                        )
                        if (currentBgValue?.trend != null && currentBgValue.trend != BgTrend.NotComputable) {
                            val trendRotation = when (currentBgValue.trend) {
                                BgTrend.DoubleUp, BgTrend.SingleUp -> -90f
                                BgTrend.FortyFiveUp -> -45f
                                BgTrend.Flat -> 0f
                                BgTrend.FortyFiveDown -> 45f
                                BgTrend.SingleDown, BgTrend.DoubleDown -> 90f
                                BgTrend.NotComputable -> 0f
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .rotate(trendRotation),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Aktive Kohlenhydrate: " + stringResource(R.string.cob_format).format(cob),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Aktives Insulin: " + stringResource(R.string.iob_format).format(iob),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (uiState.lockError) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Das System ist aktuell belegt (${uiState.lockBusyOwner}). Bitte probieren Sie es später noch einmal.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(onClick = onNavigateUp) {
                                Text("Zurück")
                            }
                        }
                    }
                    return@Column
                }

                if (uiState.isEditMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
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

                // Mahlzeit Card (Carbs + Food Type)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
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
                                suffix = " KE"
                            )
                        }

                        FoodTypeSelector(
                            mealTypes = uiState.mealTypes,
                            selectedType = uiState.selectedMealType,
                            onTypeSelected = onMealTypeChange
                        )
                    }
                }

                // Insulin Card (Final Insulin Stepper)
                if (!uiState.isEditMode) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
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
                                    currentValue = uiState.manualBolus,
                                    onValueChange = onManualBolusChange,
                                    minValue = BOLUS_MIN,
                                    maxValue = BOLUS_MAX,
                                    steppingStrategy = DefaultSteppingStrategy(0.1), // 0.1 U steps
                                    displayStrategy = object : ValueDisplayStrategy {
                                        override fun format(value: Double): String =
                                            String.format(Locale.getDefault(), "%.2f", value)

                                        override fun color(value: Double): Color = Color.Unspecified
                                    },
                                    suffix = " U"
                                )
                            }
                        }
                    }
                }

                // Bottom Button
                val isInputValid = uiState.carbsKe > 0.0 || uiState.manualBolus > 0.0
                PrimaryButton(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    enabled = !uiState.isSubmitting && isInputValid
                ) {
                    Text(
                        if (uiState.manualBolus > 0.0) {
                            stringResource(R.string.meal_bolus_administer_button)
                        } else {
                            stringResource(R.string.meal_edit_save_button)
                        }
                    )
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
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
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
                        text = stringResource(R.string.meal_bolus_calc_result_label, uiState.proposedBolus),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
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
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))

                Text(
                    stringResource(R.string.meal_bolus_calc_factors_label, uiState.isf, uiState.cr),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.meal_bolus_calc_meal_part, uiState.mealPart),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.meal_bolus_calc_correction_part, uiState.correctionPart),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.meal_bolus_calc_result_label, uiState.proposedBolus),
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
private fun MealBolusPreview() {
    val sampleMealTypes = listOf(
        MealType(name = "Schnelle KE", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
        MealType(name = "Standard-Essen", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
        MealType(name = "Fettreiches Essen", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
        MealType(name = "Langsames Essen", components = listOf(CarbCurveComponentData(100, Minutes(60))), cat = Minutes(180)),
    )
    AppTheme {
        MealBolusContent(
            uiState = MealBolusUiState(
                isLoading = false,
                carbsKe = 4.5,
                mealTypes = sampleMealTypes,
                selectedMealType = sampleMealTypes[0],
                currentBg = 140,
                targetBg = 100,
                isf = 50,
                cr = 10.0,
                mealPart = 4.5,
                correctionPart = 0.8,
                proposedBolus = 5.3,
                manualBolus = 5.3,
                isAutomaticMode = false
            ),
            currentBgValue = CurrentBgData.valid(
                bgValue = BgValue(140),
                delta = BgDelta(5),
                trend = BgTrend.FortyFiveUp,
                timestamp = Timestamp.now()
            ),
            iob = 1.2,
            cob = 25.0,
            onNavigateUp = {},
            onCarbsChange = {},
            onMealTypeChange = {},
            onManualBolusChange = {},
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true, name = "Default Mode")
@Preview(showBackground = true, name = "Default Mode - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MealBolusDefaultPreview() {
    MealBolusPreview()
}
