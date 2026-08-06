package de.dh.raaps.plugin.simbody.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.ui.components.SimBodyEatMealDialog
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimBodyMainScreen(
    bodyModel: BodyModel?,
    onNavigateUp: () -> Unit = {},
    onNavigateToImpacts: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    if (bodyModel == null) {
        Text("Body Model not available")
        return
    }

    val bg by bodyModel.bloodGlucoseFlow.collectAsState()
    val isLoaded by bodyModel.isLoadedFlow.collectAsState()
    
    var showEatMealDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Sim Body Details")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Summary Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isLoaded) String.format(Locale.US, "%.1f mg/dL", bg) else "---",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Aktueller Blutzucker",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatusItem("IOB", String.format(Locale.US, "%.2f U", bodyModel.iob))
                        StatusItem("COB", String.format(Locale.US, "%.1f g", bodyModel.cob))
                    }
                }
            }

            // Action Buttons
            Button(
                onClick = { showEatMealDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = isLoaded
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mahlzeit essen")
            }

            Button(
                onClick = onNavigateToImpacts,
                modifier = Modifier.fillMaxWidth(),
                enabled = isLoaded
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Physiologische Einflüsse")
            }

            Button(
                onClick = onNavigateToHistory,
                modifier = Modifier.fillMaxWidth(),
                enabled = isLoaded
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Eingabe-Historie")
            }
        }
    }

    if (showEatMealDialog) {
        SimBodyEatMealDialog(
            onDismiss = { showEatMealDialog = false },
            onConfirm = { carbs, type -> bodyModel.eat(carbs, type) }
        )
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}