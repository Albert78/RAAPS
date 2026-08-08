package de.dh.raaps.plugin.simbody.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R as CommonR
import de.dh.raaps.common.ui.composables.NormalButton
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.plugin.simbody.DEFAULT_SIM_BODY_PROFILE
import de.dh.raaps.plugin.simbody.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimBodyHistoryScreen(
    bodyModel: BodyModel?,
    onNavigateUp: () -> Unit = {}
) {
    if (bodyModel == null) {
        Text(stringResource(R.string.body_model_not_available))
        return
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.title_sim_body_history))
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
                .padding(16.dp)
        ) {
            item {
                Text(stringResource(R.string.label_meals), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            items(bodyModel.meals) { meal ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.unit_g, meal.carbGrams))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = meal.mealType.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = timeFormat.format(Date(meal.timestamp.ms)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateFormat.format(Date(meal.timestamp.ms)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            item {
                Text(stringResource(R.string.label_insulin), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            items(bodyModel.insulinApplications) { insulin ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.unit_u, insulin.amount))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${insulin.origin})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = timeFormat.format(Date(insulin.timestamp.ms)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateFormat.format(Date(insulin.timestamp.ms)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            item {
                NormalButton(
                    onClick = {
                        bodyModel.meals.clear()
                        bodyModel.insulinApplications.clear()
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(stringResource(R.string.btn_clear_history))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SimBodyHistoryScreenPreview() {
    val bodyModel = remember {
        BodyModel(DEFAULT_SIM_BODY_PROFILE).apply {
            eat(50.0, BodyModel.SIM_MEAL_TYPES[0])
            eat(25.0, BodyModel.SIM_MEAL_TYPES[1])
            bolus(5.0)
            bolus(2.5)
        }
    }
    AppTheme {
        SimBodyHistoryScreen(bodyModel = bodyModel)
    }
}
