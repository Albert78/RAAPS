package de.dh.raaps.ui.screens.systemcontrol

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.composables.screenTitle
import de.dh.raaps.ui.common.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SystemControlScreen(
    onNavigateUp: () -> Unit,
    onNavigateToAlgorithmDecisions: () -> Unit,
    viewModel: SystemControlViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SystemControlContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onNavigateToAlgorithmDecisions = onNavigateToAlgorithmDecisions
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemControlContent(
    uiState: SystemControlUiState,
    onNavigateUp: () -> Unit,
    onNavigateToAlgorithmDecisions: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.system_control_screen_title)),
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
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = stringResource(id = R.string.system_control_cgm_subsystem))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.system_control_cgm_source_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.glucoseSourceName ?: stringResource(id = R.string.system_control_cgm_not_connected),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (uiState.glucoseSourceName != null) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.system_control_cgm_sensor_type_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.sensorTypeName ?: "--",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.system_control_cgm_interval_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val intervalText = when (uiState.readingsInterval) {
                                    BgReadingsInterval.OneMinute -> stringResource(id = R.string.system_control_cgm_interval_1min)
                                    BgReadingsInterval.FiveMinutes -> stringResource(id = R.string.system_control_cgm_interval_5min)
                                    BgReadingsInterval.AdHoc -> stringResource(id = R.string.system_control_cgm_interval_adhoc)
                                    null -> "--"
                                }
                                Text(
                                    text = intervalText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.system_control_cgm_last_value_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val unitText = stringResource(
                                id = if (uiState.glucoseUnit == GlucoseUnit.MG_DL)
                                    R.string.glucose_unit_mgdl else R.string.glucose_unit_mmol
                            )
                            val bgValueText = uiState.lastBgReading?.let {
                                "${it.value.toString(uiState.glucoseUnit)} $unitText"
                            } ?: "--"
                            val timeText = uiState.lastBgReading?.timestamp?.let { timeFormat.format(Date(it.ms)) } ?: "--"

                            Text(
                                text = stringResource(id = R.string.system_control_cgm_value_at_time, bgValueText, timeText),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (uiState.nextPredictedTimestamp != null) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.system_control_cgm_next_value_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val nextTimeText = timeFormat.format(Date(uiState.nextPredictedTimestamp.ms))
                                Text(
                                    text = stringResource(id = R.string.system_control_cgm_at_time, nextTimeText),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = stringResource(id = R.string.system_control_pump_subsystem))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("TODO: Pump Status & Control", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = stringResource(id = R.string.system_control_algorithm_subsystem))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TODO: Algorithm Status & Control", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        PrimaryButton(onClick = onNavigateToAlgorithmDecisions) {
                            Text(stringResource(id = R.string.system_control_algorithm_history_button))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun SystemControlPreview() {
    AppTheme {
        SystemControlContent(
            uiState = SystemControlUiState(
                glucoseSourceName = "Dexcom G6",
                sensorTypeName = "G6-Sensor",
                readingsInterval = BgReadingsInterval.FiveMinutes,
                lastBgReading = null,
                nextPredictedTimestamp = de.dh.raaps.common.model.data.Timestamp(System.currentTimeMillis() + 300000),
                glucoseUnit = de.dh.raaps.common.model.data.GlucoseUnit.MG_DL
            ),
            onNavigateUp = {},
            onNavigateToAlgorithmDecisions = {}
        )
    }
}
