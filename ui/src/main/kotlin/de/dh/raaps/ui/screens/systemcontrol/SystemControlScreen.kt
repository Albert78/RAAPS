package de.dh.raaps.ui.screens.systemcontrol

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.pump.PumpCommand
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.composables.screenTitle
import de.dh.raaps.ui.common.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val SYSTEM_CONTROL_TAB_CGM = 0
const val SYSTEM_CONTROL_TAB_PUMP = 1
const val SYSTEM_CONTROL_TAB_ALGORITHM = 2

@Composable
fun SystemControlScreen(
    onNavigateUp: () -> Unit,
    onNavigateToAlgorithmDecisions: () -> Unit,
    onNavigateToPumpManagement: () -> Unit,
    viewModel: SystemControlViewModel,
    initialTab: Int = SYSTEM_CONTROL_TAB_CGM
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SystemControlContent(
        uiState = uiState,
        initialTab = initialTab,
        onNavigateUp = onNavigateUp,
        onNavigateToAlgorithmDecisions = onNavigateToAlgorithmDecisions,
        onNavigateToPumpManagement = onNavigateToPumpManagement,
        onCancelPumpJob = viewModel::cancelPumpJob,
        onRefreshPumpStatus = viewModel::refreshPumpStatus
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemControlContent(
    uiState: SystemControlUiState,
    initialTab: Int = SYSTEM_CONTROL_TAB_CGM,
    onNavigateUp: () -> Unit,
    onNavigateToAlgorithmDecisions: () -> Unit,
    onNavigateToPumpManagement: () -> Unit,
    onCancelPumpJob: (String) -> Unit,
    onRefreshPumpStatus: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var selectedTabIndex by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf(
        stringResource(id = R.string.system_control_tab_cgm),
        stringResource(id = R.string.system_control_tab_pump),
        stringResource(id = R.string.system_control_tab_algorithm)
    )

    Scaffold(
        topBar = {
            Column {
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
                PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTabIndex) {
                SYSTEM_CONTROL_TAB_CGM -> {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        CgmOverviewCard(uiState, timeFormat)
                    }
                }

                SYSTEM_CONTROL_TAB_PUMP -> {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        PumpOverviewCard(uiState, timeFormat)
                        PumpActionsCard(
                            uiState = uiState,
                            onNavigateToPumpManagement = onNavigateToPumpManagement,
                            onRefreshPumpStatus = onRefreshPumpStatus
                        )
                    }

                    if (uiState.pendingPumpJobs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            PumpJobsCard(uiState, onCancelPumpJob)
                        }
                    }
                }

                SYSTEM_CONTROL_TAB_ALGORITHM -> {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
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
                                Text(
                                    "Algorithm Status: Operational",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PrimaryButton(onClick = onNavigateToAlgorithmDecisions) {
                                    Text(stringResource(id = R.string.system_control_algorithm_history_button))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun CgmOverviewCard(uiState: SystemControlUiState, timeFormat: SimpleDateFormat) {
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
            InfoRow(
                label = stringResource(id = R.string.system_control_cgm_source_label),
                value = uiState.glucoseSourceName ?: stringResource(id = R.string.system_control_cgm_not_connected)
            )

            if (uiState.glucoseSourceName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow(
                    label = stringResource(id = R.string.system_control_cgm_sensor_type_label),
                    value = uiState.sensorTypeName ?: "--"
                )

                Spacer(modifier = Modifier.height(8.dp))
                val intervalText = when (uiState.readingsInterval) {
                    BgReadingsInterval.OneMinute -> stringResource(id = R.string.system_control_cgm_interval_1min)
                    BgReadingsInterval.FiveMinutes -> stringResource(id = R.string.system_control_cgm_interval_5min)
                    BgReadingsInterval.AdHoc -> stringResource(id = R.string.system_control_cgm_interval_adhoc)
                    null -> "--"
                }
                InfoRow(
                    label = stringResource(id = R.string.system_control_cgm_interval_label),
                    value = intervalText
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val unitText = stringResource(
                id = if (uiState.glucoseUnit == GlucoseUnit.MG_DL)
                    R.string.glucose_unit_mgdl else R.string.glucose_unit_mmol
            )
            val bgValueText = uiState.lastBgReading?.let {
                "${it.value.toString(uiState.glucoseUnit)} $unitText"
            } ?: "--"
            val timeText = uiState.lastBgReading?.timestamp?.let { timeFormat.format(Date(it.ms)) } ?: "--"

            InfoRow(
                label = stringResource(id = R.string.system_control_cgm_last_value_label),
                value = stringResource(id = R.string.system_control_cgm_value_at_time, bgValueText, timeText)
            )

            if (uiState.nextPredictedTimestamp != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val nextTimeText = timeFormat.format(Date(uiState.nextPredictedTimestamp.ms))
                InfoRow(
                    label = stringResource(id = R.string.system_control_cgm_next_value_label),
                    value = stringResource(id = R.string.system_control_cgm_at_time, nextTimeText)
                )
            }
        }
    }
}

@Composable
fun PumpOverviewCard(uiState: SystemControlUiState, timeFormat: SimpleDateFormat) {
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
            InfoRow(
                label = stringResource(id = R.string.system_control_pump_model_label),
                value = uiState.pumpModel ?: stringResource(id = R.string.system_control_cgm_not_connected)
            )

            Spacer(modifier = Modifier.height(8.dp))
            val statusText = when {
                !uiState.pumpConnected -> stringResource(id = R.string.system_control_pump_state_disconnected)
                uiState.pumpStatus?.pumpSuspended == true -> stringResource(id = R.string.system_control_pump_state_suspended)
                else -> stringResource(id = R.string.system_control_pump_state_active)
            }
            val statusColor = when {
                !uiState.pumpConnected -> MaterialTheme.colorScheme.error
                uiState.pumpStatus?.pumpSuspended == true -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            InfoRow(
                label = stringResource(id = R.string.system_control_pump_status_label),
                value = statusText,
                valueColor = statusColor
            )

            if (uiState.pumpConnected && uiState.pumpStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        StatusItem(
                            icon = Icons.Default.BatteryFull,
                            label = stringResource(id = R.string.system_control_pump_battery_label),
                            value = "${uiState.pumpStatus.batteryRemainingPercent}%"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StatusItem(
                            icon = Icons.Default.WaterDrop,
                            label = stringResource(id = R.string.system_control_pump_reservoir_label),
                            value = stringResource(id = R.string.insulin_unit_label_format, uiState.pumpStatus.reservoirRemainingUnits.iu)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val lastConnText = if (uiState.lastPumpConnection != Timestamp.INVALID) {
                timeFormat.format(Date(uiState.lastPumpConnection.ms))
            } else "--"
            InfoRow(
                label = stringResource(id = R.string.system_control_pump_last_conn_label),
                value = lastConnText
            )
        }
    }
}

@Composable
fun PumpActionsCard(
    uiState: SystemControlUiState,
    onNavigateToPumpManagement: () -> Unit,
    onRefreshPumpStatus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val isSuspended = uiState.pumpStatus?.pumpSuspended == true
                ActionButton(
                    icon = if (isSuspended) Icons.Default.PlayArrow else Icons.Default.Pause,
                    label = if (isSuspended) stringResource(id = R.string.system_control_pump_action_resume)
                            else stringResource(id = R.string.system_control_pump_action_suspend),
                    onClick = { /* TODO: Implement Suspend/Resume */ },
                    enabled = uiState.pumpConnected
                )
                ActionButton(
                    icon = Icons.Default.Sync,
                    label = stringResource(id = R.string.system_control_pump_action_sync),
                    onClick = onRefreshPumpStatus,
                    enabled = uiState.pumpConnected
                )
                ActionButton(
                    icon = Icons.Default.Settings,
                    label = stringResource(id = R.string.system_control_pump_action_manage),
                    onClick = onNavigateToPumpManagement
                )
            }
        }
    }
}

@Composable
fun PumpJobsCard(uiState: SystemControlUiState, onCancelJob: (String) -> Unit) {
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
            Text(
                text = stringResource(id = R.string.system_control_pump_jobs_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            uiState.pendingPumpJobs.forEach { job ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val commandText = when (val cmd = job.command) {
                        is PumpCommand.DeliverBolus -> stringResource(id = R.string.system_control_pump_job_type_bolus, cmd.amount.iu)
                        is PumpCommand.SetTempBasal -> stringResource(id = R.string.system_control_pump_job_type_temp_basal, cmd.percent)
                        is PumpCommand.SetProfile -> stringResource(id = R.string.system_control_pump_job_type_profile)
                        is PumpCommand.CancelTempBasal -> stringResource(id = R.string.system_control_pump_job_type_cancel_temp_basal)
                        is PumpCommand.CancelBolus -> stringResource(id = R.string.system_control_pump_job_type_cancel_bolus)
                    }
                    Text(
                        text = commandText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCancelJob(job.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = stringResource(id = R.string.system_control_pump_job_cancel),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun StatusItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
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

@Preview(showBackground = true, name = "CGM Tab")
@Composable
fun SystemControlCgmPreview() {
    AppTheme {
        SystemControlContent(
            uiState = previewUiState(),
            initialTab = SYSTEM_CONTROL_TAB_CGM,
            onNavigateUp = {},
            onNavigateToAlgorithmDecisions = {},
            onNavigateToPumpManagement = {},
            onCancelPumpJob = {},
            onRefreshPumpStatus = {}
        )
    }
}

@Preview(showBackground = true, name = "Pump Tab")
@Composable
fun SystemControlPumpPreview() {
    AppTheme {
        SystemControlContent(
            uiState = previewUiState().copy(
                pendingPumpJobs = listOf(
                    de.dh.raaps.core.pump.PumpJob(
                        command = de.dh.raaps.core.pump.PumpCommand.DeliverBolus(de.dh.raaps.common.model.InsulinAmount(1.5)),
                        isCancelableAPSCommand = false
                    )
                )
            ),
            initialTab = SYSTEM_CONTROL_TAB_PUMP,
            onNavigateUp = {},
            onNavigateToAlgorithmDecisions = {},
            onNavigateToPumpManagement = {},
            onCancelPumpJob = {},
            onRefreshPumpStatus = {}
        )
    }
}

@Preview(showBackground = true, name = "Algorithm Tab")
@Composable
fun SystemControlAlgorithmPreview() {
    AppTheme {
        SystemControlContent(
            uiState = previewUiState(),
            initialTab = SYSTEM_CONTROL_TAB_ALGORITHM,
            onNavigateUp = {},
            onNavigateToAlgorithmDecisions = {},
            onNavigateToPumpManagement = {},
            onCancelPumpJob = {},
            onRefreshPumpStatus = {}
        )
    }
}

private fun previewUiState() = SystemControlUiState(
    glucoseSourceName = "Dexcom G6",
    sensorTypeName = "G6-Sensor",
    readingsInterval = BgReadingsInterval.FiveMinutes,
    lastBgReading = null,
    nextPredictedTimestamp = de.dh.raaps.common.model.data.Timestamp(System.currentTimeMillis() + 300000),
    glucoseUnit = de.dh.raaps.common.model.data.GlucoseUnit.MG_DL,
    pumpConnected = true,
    pumpModel = "DANA-i"
)
