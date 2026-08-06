package de.dh.raaps.plugin.simbody.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R as CommonR
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.DEFAULT_SIM_BODY_PROFILE
import de.dh.raaps.plugin.simbody.Impacts
import de.dh.raaps.plugin.simbody.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimBodyImpactsScreen(
    bodyModel: BodyModel?,
    onNavigateUp: () -> Unit = {}
) {
    if (bodyModel == null) {
        Text(stringResource(R.string.body_model_not_available))
        return
    }

    val dateTimeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.title_metabolic_impacts))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CommonR.string.cd_navigate_up)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.subtitle_bg_impact_over_time),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (bodyModel.impactHistory.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_impacts_yet))
                }
            } else {
                items(bodyModel.impactHistory) { impact ->
                    ImpactCard(impact, dateTimeFormat)
                }
            }
        }
    }
}

@Composable
private fun ImpactCard(impact: Impacts, timeFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.label_time, timeFormat.format(Date(impact.currentTimestamp.ms))),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                
                val totalDelta = impact.carbImpact - impact.insulinImpact + impact.endogenousImpact - impact.exerciseImpact + impact.stressImpact
                Text(
                    text = if (totalDelta >= 0) stringResource(R.string.unit_delta_bg_pos, totalDelta) else stringResource(R.string.unit_delta_bg_neg, -totalDelta),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (totalDelta >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            ImpactRow(stringResource(R.string.impact_carbs), stringResource(R.string.unit_delta_bg_pos, impact.carbImpact))
            ImpactRow(stringResource(R.string.impact_insulin), stringResource(R.string.unit_delta_bg_neg, impact.insulinImpact))
            ImpactRow(stringResource(R.string.impact_liver), stringResource(R.string.unit_delta_bg_pos, impact.endogenousImpact))
            ImpactRow(stringResource(R.string.impact_exercise), stringResource(R.string.unit_delta_bg_neg, impact.exerciseImpact))
            ImpactRow(stringResource(R.string.impact_stress), stringResource(R.string.unit_delta_bg_pos, impact.stressImpact))
        }
    }
}

@Composable
private fun ImpactRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
fun SimBodyImpactsScreenPreview() {
    val bodyModel = remember {
        BodyModel(DEFAULT_SIM_BODY_PROFILE).apply {
            impactHistory.add(
                Impacts(
                    carbImpact = 1.25,
                    insulinImpact = 0.8,
                    endogenousImpact = 0.5,
                    exerciseImpact = 0.2,
                    stressImpact = 0.1,
                    currentTimestamp = Timestamp(System.currentTimeMillis())
                )
            )
            impactHistory.add(
                Impacts(
                    carbImpact = 0.0,
                    insulinImpact = 1.5,
                    endogenousImpact = 0.5,
                    exerciseImpact = 0.0,
                    stressImpact = 0.0,
                    currentTimestamp = Timestamp(System.currentTimeMillis() - 300000)
                )
            )
        }
    }
    SimBodyImpactsScreen(bodyModel = bodyModel, onNavigateUp = {})
}