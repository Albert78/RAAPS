package de.dh.raaps.ui.screens.systemcontrol

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R as CommonR
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.core.aps.CoreInsight
import de.dh.raaps.core.aps.CoreReasoning
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.LocalGlucoseUnit
import de.dh.raaps.ui.common.glucoseValue
import de.dh.raaps.ui.common.isfValue
import de.dh.raaps.ui.common.insulinValue
import de.dh.raaps.ui.common.crValue
import de.dh.raaps.ui.common.deltaValue
import de.dh.raaps.ui.common.composables.screenTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CoreDecisionsScreen(
    viewModel: SystemControlViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    CoreDecisionsContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreDecisionsContent(
    uiState: SystemControlUiState,
    onNavigateUp: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.core_decisions_screen_title)),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (uiState.coreInsights.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.core_decisions_no_insights),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(uiState.coreInsights) { insight ->
                    InsightCard(insight)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun InsightCard(insight: CoreInsight) {
    val dateTimeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val glucoseUnit = LocalGlucoseUnit.current
    val unitStr = when (glucoseUnit) {
        de.dh.raaps.common.model.data.GlucoseUnit.MG_DL -> stringResource(CommonR.string.glucose_unit_mgdl)
        de.dh.raaps.common.model.data.GlucoseUnit.MMOL -> stringResource(CommonR.string.glucose_unit_mmol)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = dateTimeFormat.format(Date(insight.timestamp.ms)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = insight.reasoning.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (insight.reasoning == CoreReasoning.INTERNAL_ERROR) Color.Red else MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(stringResource(id = R.string.core_insight_label_bg), "${glucoseValue(insight.bgFiltered, glucoseUnit)} ${stringResource(R.string.bg_raw_format, glucoseValue(insight.bgOriginal, glucoseUnit))}")
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.core_insight_label_iob), insulinValue(insight.futureActiveInsulin.iu))
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.core_insight_label_cob), "%.1f g".format(insight.futureActiveCarbs))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(stringResource(id = R.string.core_insight_label_pred_peak), "${glucoseValue(insight.predictedBgAtPeak, glucoseUnit)} $unitStr")
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.core_insight_label_dev), deltaValue(insight.deviationPerTick, glucoseUnit))
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.core_insight_label_target), "${glucoseValue(insight.targetBg, glucoseUnit)} $unitStr")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(stringResource(id = R.string.core_insight_label_isf), isfValue(insight.isf, glucoseUnit))
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.core_insight_label_cr), crValue(insight.cr))
            }

            val actionText = when {
                insight.actionBolus != null && insight.actionBolus!! > InsulinAmount.ZERO -> {
                    stringResource(id = R.string.core_insight_action_bolus, insulinValue(insight.actionBolus!!.iu))
                }
                insight.actionTempBasalPercent != null -> {
                    stringResource(
                        id = R.string.core_insight_action_temp_basal,
                        insight.actionTempBasalPercent!!,
                        insight.actionTempBasalDurationInHours ?: 0
                    )
                }
                else -> null
            }

            actionText?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}