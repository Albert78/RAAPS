package de.dh.raaps.ui.screens.systemcontrol

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.theme.AppTheme

@Composable
fun CoreTabContent(
    onNavigateToCoreDecisions: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
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
                label = "APS Status",
                icon = Icons.Default.CheckCircle
            ) {
                Text(
                    "Operational",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                onClick = onNavigateToCoreDecisions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.system_control_core_history_button))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CoreTabPreview() {
    AppTheme {
        CoreTabContent(onNavigateToCoreDecisions = {})
    }
}
