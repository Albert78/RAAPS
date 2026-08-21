package de.dh.raaps.ui.common.treatmentlock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.dh.raaps.core.aps.TreatmentLock
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.composables.PrimaryButton
import de.dh.raaps.ui.common.composables.Red
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import de.dh.raaps.common.R as CommonR

@Composable
fun TreatmentLockScreen(
    viewModel: TreatmentLockViewModel,
    onNavigateUp: () -> Unit,
    title: String,
    content: @Composable (TreatmentLock) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    TreatmentLockScreenContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        title = title,
        content = content
    )
}

@Composable
fun TreatmentLockScreenContent(
    uiState: TreatmentLockUiState,
    onNavigateUp: () -> Unit,
    title: String,
    content: @Composable (TreatmentLock) -> Unit
) {

    // 1. Loading / Busy Status
    if (uiState.status == LockStatus.Loading || uiState.status == LockStatus.Busy) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TreatmentLockHeader(title = title, onNavigateUp = onNavigateUp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.meal_bolus_busy_system, uiState.busyOwner ?: "System"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // 2. Error Status
    if (uiState.status == LockStatus.Error) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TreatmentLockHeader(title = title, onNavigateUp = onNavigateUp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LockErrorCard(owner = uiState.busyOwner, onNavigateUp = onNavigateUp)
            }
        }
    }

    // 3. Acquired Status
    if (uiState.status == LockStatus.Acquired && uiState.acquiredLock != null) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TreatmentLockHeader(title = title, onNavigateUp = onNavigateUp)
            content(uiState.acquiredLock!!)
        }
    }
}

@Composable
private fun LockErrorCard(owner: String?, onNavigateUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Red.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.meal_bolus_lock_error_message, owner ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(onClick = onNavigateUp) {
                Text(stringResource(id = CommonR.string.cd_navigate_up))
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TreatmentLockHeader(
    title: String,
    onNavigateUp: () -> Unit
) {
    androidx.compose.material3.TopAppBar(
        title = {
            Text(title)
        },
        navigationIcon = {
            androidx.compose.material3.IconButton(onClick = onNavigateUp) {
                androidx.compose.material3.Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.cd_close)
                )
            }
        }
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Loading/Busy")
@Composable
fun TreatmentLockScreenBusyPreview() {
    de.dh.raaps.ui.common.theme.AppTheme {
        TreatmentLockScreenContent(
            uiState = TreatmentLockUiState(status = LockStatus.Busy, busyOwner = "Core"),
            onNavigateUp = {},
            title = "Preview Screen",
            content = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Error")
@Composable
fun TreatmentLockScreenErrorPreview() {
    de.dh.raaps.ui.common.theme.AppTheme {
        TreatmentLockScreenContent(
            uiState = TreatmentLockUiState(status = LockStatus.Error, busyOwner = "PumpManager"),
            onNavigateUp = {},
            title = "Preview Screen",
            content = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Acquired")
@Composable
fun TreatmentLockScreenAcquiredPreview() {
    de.dh.raaps.ui.common.theme.AppTheme {
        TreatmentLockScreenContent(
            uiState = TreatmentLockUiState(
                status = LockStatus.Acquired,
                acquiredLock = TreatmentLock("Test")
            ),
            onNavigateUp = {},
            title = "Preview Screen"
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("This is the protected content!")
            }
        }
    }
}