package de.dh.raaps.ui.screens.systemcontrol

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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.composables.screenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpManagementScreen(
    onNavigateUp: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.pump_management_screen_title)),
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
                SectionHeader(title = "Wartung & Wechsel")
                
                ManagementCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = stringResource(id = R.string.pump_management_battery_change),
                    description = "Führt den Dialog für einen Batteriewechsel durch.",
                    onClick = { /* TODO */ }
                )
                
                ManagementCard(
                    icon = Icons.Default.EvStation,
                    title = stringResource(id = R.string.pump_management_reservoir_change),
                    description = "Dialog zum Auffüllen oder Wechseln des Reservoirs.",
                    onClick = { /* TODO */ }
                )
                
                ManagementCard(
                    icon = Icons.Default.Opacity,
                    title = stringResource(id = R.string.pump_management_cannula_change),
                    description = "Dialog zum Setzen eines neuen Katheters inkl. Prime.",
                    onClick = { /* TODO */ }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = stringResource(id = R.string.pump_management_special_commands))
                
                ManagementCard(
                    icon = Icons.Default.Construction,
                    title = stringResource(id = R.string.pump_management_prime_cannula),
                    description = "Füllt den Katheter mit einer definierten Menge Insulin.",
                    onClick = { /* TODO */ }
                )
                
                ManagementCard(
                    icon = Icons.Default.Construction,
                    title = stringResource(id = R.string.pump_management_prime_tubing),
                    description = "Füllt den Schlauch bis zum Ende auf.",
                    onClick = { /* TODO */ }
                )
            }
        }
    }
}

@Composable
private fun ManagementCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
