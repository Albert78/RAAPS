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
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.core.aps.AlgorithmInsight
import de.dh.raaps.core.aps.AlgorithmReasoning
import de.dh.raaps.ui.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlgorithmDecisionsScreen(
    viewModel: SystemControlViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    AlgorithmDecisionsContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlgorithmDecisionsContent(
    uiState: SystemControlUiState,
    onNavigateUp: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.algorithm_decisions_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.cd_navigate_up)
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
            if (uiState.algorithmInsights.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.algorithm_decisions_no_insights),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(uiState.algorithmInsights) { insight ->
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
fun InsightCard(insight: AlgorithmInsight) {
    val dateTimeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
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
                    color = if (insight.reasoning == AlgorithmReasoning.INTERNAL_ERROR) Color.Red else MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(stringResource(id = R.string.algorithm_insight_label_bg), "${insight.bgFiltered} (raw: ${insight.bgOriginal})")
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.algorithm_insight_label_iob), "%.2f U".format(insight.iobAtPeak))
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.algorithm_insight_label_cob), "%.1f g".format(insight.cobAtPeak))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(stringResource(id = R.string.algorithm_insight_label_pred_peak), "${insight.predictedBgAtPeak} mg/dL")
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.algorithm_insight_label_dev), "%+.1f".format(insight.deviationPerTick))
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.algorithm_insight_label_target), "${insight.targetBg} mg/dL")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(stringResource(id = R.string.algorithm_insight_label_isf), "%.1f".format(insight.isf))
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.algorithm_insight_label_cr), "%.1f".format(insight.cr))
                Spacer(modifier = Modifier.width(16.dp))
                MetricItem(stringResource(id = R.string.algorithm_insight_label_basal_cob), "%.1f g".format(insight.cobEquivalentOfBasalAtPeak))
            }

            val actionText = when {
                insight.actionBolus != null && insight.actionBolus!! > 0.0 -> {
                    stringResource(id = R.string.algorithm_insight_action_bolus, insight.actionBolus!!)
                }
                insight.actionTempBasalPercent != null -> {
                    stringResource(
                        id = R.string.algorithm_insight_action_temp_basal,
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
