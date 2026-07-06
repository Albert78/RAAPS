package de.dh.raaps.plugin.simbody.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R
import de.dh.raaps.plugin.simbody.BodyModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimBodyDetailScreen(
    bodyModel: BodyModel?,
    onNavigateUp: () -> Unit = {}
) {
    if (bodyModel == null) {
        Text("Body Model not available")
        return
    }

    val dateTimeFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Sim Body Historical Inputs")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                Text("Meals", style = MaterialTheme.typography.titleLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            items(bodyModel.meals) { meal ->
                val carbs = String.format(Locale.US, "%.2f", meal.carbGrams)
                Text("${carbs}g at ${dateTimeFormat.format(Date(meal.timestamp.ms))}")
            }

            item {
                Text("Insulin", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            items(bodyModel.insulinApplications) { insulin ->
                val units = String.format(Locale.US, "%.2f", insulin.amount)
                Text("${units}U at ${dateTimeFormat.format(Date(insulin.timestamp.ms))} (${insulin.origin})")
            }

            item {
                Button(
                    onClick = {
                        bodyModel.meals.clear()
                        bodyModel.insulinApplications.clear()
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Clear History")
                }
            }
        }
    }
}
