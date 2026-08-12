package de.dh.raaps.ui.screens.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.LocalGlucoseUnit
import de.dh.raaps.ui.common.composables.screenTitle
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.controls.history.BgHistoryChartOrDefault
import de.dh.raaps.ui.controls.history.BgOverviewChart
import de.dh.raaps.ui.controls.history.HistoryDiagramData
import de.dh.raaps.ui.controls.history.HistoryUiState
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.history.createSampleReadings
import de.dh.raaps.ui.controls.history.rememberBgHistoryChartState

@Composable
fun HistoryScreen(
    historyViewModel: HistoryViewModel
) {
    val historyUiState by historyViewModel.historyUiState.collectAsState()

    HistoryContent(
        historyUiState = historyUiState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    historyUiState: HistoryUiState
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val chartState = rememberBgHistoryChartState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.history_screen_title)),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (historyUiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val glucoseUnit = LocalGlucoseUnit.current
                val diagramData = HistoryDiagramData.fromReadings(historyUiState.readings, glucoseUnit)
                Column(modifier = Modifier.fillMaxSize()) {
                    BgHistoryChartOrDefault(
                        diagramData = diagramData,
                        showMarkers = true,
                        state = chartState,
                        modifier = Modifier.weight(1f)
                    )

                    if (diagramData != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        BgOverviewChart(
                            diagramData = diagramData,
                            state = chartState,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

fun createSampleHistoryUiState(): HistoryUiState {
    return HistoryUiState(
        isLoading = false,
        isError = false,
        readings = createSampleReadings(120, 5)
    )
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    AppTheme {
        HistoryContent(
            historyUiState = createSampleHistoryUiState()
        )
    }
}
