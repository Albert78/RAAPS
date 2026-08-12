package de.dh.raaps.ui.screens.systemcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.icons.next
import de.dh.raaps.ui.common.icons.previous
import de.dh.raaps.ui.common.shortRelativeTimeAgo
import de.dh.raaps.ui.common.shortRelativeTimeUntil
import de.dh.raaps.ui.common.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun CgmTabContent(
    uiState: SystemControlUiState,
    timeFormat: SimpleDateFormat,
    tick: Long
) {
    Spacer(modifier = Modifier.height(16.dp))
    CgmOverviewCard(uiState, timeFormat, tick)
}

@Composable
fun CgmOverviewCard(uiState: SystemControlUiState, timeFormat: SimpleDateFormat, tick: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            ControlDetailRow(
                label = stringResource(id = R.string.system_control_cgm_source_label),
                icon = Icons.Default.Info
            ) {
                Text(
                    text = uiState.glucoseSourceName ?: stringResource(id = R.string.system_control_cgm_not_connected),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            if (uiState.glucoseSourceName != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        ControlDetailRow(
                            label = stringResource(id = R.string.system_control_cgm_sensor_type_label)
                        ) {
                            Text(
                                text = uiState.sensorTypeName ?: "--",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ControlDetailRow(
                            label = stringResource(id = R.string.system_control_cgm_interval_label)
                        ) {
                            val intervalText = when (uiState.readingsInterval) {
                                BgReadingsInterval.OneMinute -> stringResource(id = R.string.system_control_cgm_interval_1min)
                                BgReadingsInterval.FiveMinutes -> stringResource(id = R.string.system_control_cgm_interval_5min)
                                BgReadingsInterval.AdHoc -> stringResource(id = R.string.system_control_cgm_interval_adhoc)
                                null -> "--"
                            }
                            Text(
                                text = intervalText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 20.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                val unitText = stringResource(
                    id = if (uiState.glucoseUnit == GlucoseUnit.MG_DL)
                        R.string.glucose_unit_mgdl else R.string.glucose_unit_mmol
                )

                ControlDetailRow(
                    label = stringResource(id = R.string.system_control_cgm_last_value_label),
                    icon = previous
                ) {
                    val bgValueText = uiState.lastBgReading?.let {
                        "${it.value.toString(uiState.glucoseUnit)} $unitText"
                    } ?: "--"
                    val timeText = uiState.lastBgReading?.timestamp?.let { timeFormat.format(Date(it.ms)) } ?: "--"
                    val relativeTime = uiState.lastBgReading?.timestamp?.let {
                        shortRelativeTimeAgo(tick - it.ms)
                    }

                    GlucoseFragments(
                        value = bgValueText,
                        time = timeText,
                        extra = relativeTime,
                        stackVertical = true
                    )
                }

                if (uiState.nextPredictedTimestamp != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ControlDetailRow(
                        label = stringResource(id = R.string.system_control_cgm_next_value_label),
                        icon = next
                    ) {
                        val nextTimeText = timeFormat.format(Date(uiState.nextPredictedTimestamp.ms))
                        val remainingTime = run {
                            val diffMs = uiState.nextPredictedTimestamp.ms - tick
                            if (diffMs > 0) {
                                shortRelativeTimeUntil(diffMs)
                            } else null
                        }

                        GlucoseFragments(
                            value = "--",
                            time = nextTimeText,
                            extra = remainingTime,
                            stackVertical = true
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlucoseFragments(value: String, time: String, extra: String?, stackVertical: Boolean = false) {
    if (stackVertical) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (extra != null) {
                    Text(
                        text = "($extra)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (value.isNotEmpty() && value != "--") {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        FlowRow(
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (value.isNotEmpty() && value != "--") {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            if (extra != null) {
                Text(
                    text = "($extra)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CgmTabPreview() {
    AppTheme {
        CgmTabContent(
            uiState = SystemControlUiState(
                glucoseSourceName = "Dexcom G6",
                sensorTypeName = "G6-Sensor",
                readingsInterval = BgReadingsInterval.FiveMinutes,
                lastBgReading = null,
                nextPredictedTimestamp = de.dh.raaps.common.model.data.Timestamp(System.currentTimeMillis() + 300000),
                glucoseUnit = de.dh.raaps.common.model.data.GlucoseUnit.MG_DL,
                pumpConnected = true,
                pumpModel = "DANA-i"
            ),
            timeFormat = SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()),
            tick = System.currentTimeMillis()
        )
    }
}
