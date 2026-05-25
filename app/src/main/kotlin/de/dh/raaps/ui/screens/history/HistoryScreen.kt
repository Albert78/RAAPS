package de.dh.raaps.ui.screens.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import de.dh.raaps.R
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.controls.history.BgHistoryChartOrDefault
import de.dh.raaps.ui.controls.history.DiagramData
import de.dh.raaps.ui.controls.history.HistoryUiState
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.history.createSampleReadings

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
                BgHistoryChartOrDefault(
                    diagramData = DiagramData.fromReadings(historyUiState.readings),
                    showMarkers = true,
                    modifier = Modifier.fillMaxSize()
                )
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