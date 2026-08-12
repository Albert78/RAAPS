package de.dh.raaps.ui.screens.systemcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.pump.PumpCommand
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun PumpTabContent(
    uiState: SystemControlUiState,
    timeFormat: SimpleDateFormat,
    onNavigateToPumpManagement: () -> Unit,
    onRefreshPumpStatus: () -> Unit,
    onCancelPumpJob: (String) -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        PumpOverviewCard(uiState, timeFormat)
        PumpActionsCard(
            uiState = uiState,
            onNavigateToPumpManagement = onNavigateToPumpManagement,
            onRefreshPumpStatus = onRefreshPumpStatus
        )

        if (uiState.pendingPumpJobs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PumpJobsCard(uiState, onCancelPumpJob)
        }

        if (uiState.pumpPluginUiProvider != null) {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Plugin")
            Spacer(modifier = Modifier.height(8.dp))
            uiState.pumpPluginUiProvider.PumpControlSection()
        }
    }
}

@Composable
fun PumpOverviewCard(uiState: SystemControlUiState, timeFormat: SimpleDateFormat) {
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
                label = stringResource(id = R.string.system_control_pump_model_label),
                icon = Icons.Default.Info
            ) {
                Text(
                    text = uiState.pumpModel ?: stringResource(id = R.string.system_control_cgm_not_connected),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
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
            ControlDetailRow(
                label = stringResource(id = R.string.system_control_pump_status_label),
                icon = if (uiState.pumpConnected) Icons.Default.Settings else Icons.Default.Cancel
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            if (uiState.pumpConnected && uiState.pumpStatus != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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

            Spacer(modifier = Modifier.height(12.dp))
            val lastConnText = if (uiState.lastPumpConnection != Timestamp.INVALID) {
                timeFormat.format(Date(uiState.lastPumpConnection.ms))
            } else "--"
            ControlDetailRow(
                label = stringResource(id = R.string.system_control_pump_last_conn_label),
                icon = Icons.Default.Sync
            ) {
                Text(
                    text = lastConnText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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

@Preview(showBackground = true)
@Composable
fun PumpTabPreview() {
    AppTheme {
        PumpTabContent(
            uiState = SystemControlUiState(
                pumpConnected = true,
                pumpModel = "DANA-i",
                pendingPumpJobs = listOf(
                    de.dh.raaps.core.pump.PumpJob(
                        command = de.dh.raaps.core.pump.PumpCommand.DeliverBolus(de.dh.raaps.common.model.InsulinAmount(1.5)),
                        isCancelableAPSCommand = false
                    )
                ),
                pumpPluginUiProvider = object : PumpPluginUiProvider {
                    @Composable
                    override fun PumpControlSection() {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                "Sample Pump Plugin Content from Provider",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            ),
            timeFormat = SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()),
            onNavigateToPumpManagement = {},
            onRefreshPumpStatus = {},
            onCancelPumpJob = {}
        )
    }
}
