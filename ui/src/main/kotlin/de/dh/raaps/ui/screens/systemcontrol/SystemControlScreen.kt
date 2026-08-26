package de.dh.raaps.ui.screens.systemcontrol

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R as CommonR
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.core.pump.PumpCommand
import de.dh.raaps.core.pump.PumpJob
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.composables.screenTitle
import de.dh.raaps.ui.common.theme.AppTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

const val SYSTEM_CONTROL_TAB_CGM = 0
const val SYSTEM_CONTROL_TAB_PUMP = 1
const val SYSTEM_CONTROL_TAB_CORE = 2

@Composable
fun SystemControlScreen(
    onNavigateUp: () -> Unit,
    onNavigateToCoreDecisions: () -> Unit,
    onNavigateToPumpManagement: () -> Unit,
    viewModel: SystemControlViewModel,
    initialTab: Int = SYSTEM_CONTROL_TAB_CGM
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SystemControlContent(
        uiState = uiState,
        initialTab = initialTab,
        onNavigateUp = onNavigateUp,
        onNavigateToCoreDecisions = onNavigateToCoreDecisions,
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
    onNavigateToCoreDecisions: () -> Unit,
    onNavigateToPumpManagement: () -> Unit,
    onCancelPumpJob: (String) -> Unit,
    onRefreshPumpStatus: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var selectedTabIndex by remember { mutableIntStateOf(initialTab) }

    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            tick = now
            val next10s = ((now / 10000) + 1) * 10000
            delay((next10s - now).milliseconds)
        }
    }

    val tabs = listOf(
        stringResource(id = R.string.system_control_tab_cgm),
        stringResource(id = R.string.system_control_tab_pump),
        stringResource(id = R.string.system_control_tab_core)
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
                                contentDescription = stringResource(id = CommonR.string.cd_navigate_up)
                            )
                        }
                    }
                )
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                when (selectedTabIndex) {
                    SYSTEM_CONTROL_TAB_CGM -> CgmTabContent(uiState, timeFormat, tick)
                    SYSTEM_CONTROL_TAB_PUMP -> PumpTabContent(
                        uiState = uiState,
                        timeFormat = timeFormat,
                        onNavigateToPumpManagement = onNavigateToPumpManagement,
                        onRefreshPumpStatus = onRefreshPumpStatus,
                        onCancelPumpJob = onCancelPumpJob
                    )
                    SYSTEM_CONTROL_TAB_CORE -> CoreTabContent(onNavigateToCoreDecisions)
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
            onNavigateToCoreDecisions = {},
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
                    PumpJob(
                        command = PumpCommand.DeliverBolus(de.dh.raaps.common.model.InsulinAmount(1.5)),
                        isCancelableAPSCommand = false
                    )
                )
            ),
            initialTab = SYSTEM_CONTROL_TAB_PUMP,
            onNavigateUp = {},
            onNavigateToCoreDecisions = {},
            onNavigateToPumpManagement = {},
            onCancelPumpJob = {},
            onRefreshPumpStatus = {}
        )
    }
}

@Preview(showBackground = true, name = "Core Tab")
@Composable
fun SystemControlCorePreview() {
    AppTheme {
        SystemControlContent(
            uiState = previewUiState(),
            initialTab = SYSTEM_CONTROL_TAB_CORE,
            onNavigateUp = {},
            onNavigateToCoreDecisions = {},
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