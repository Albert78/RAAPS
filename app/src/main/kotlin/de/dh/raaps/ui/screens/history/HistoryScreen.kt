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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.dh.raaps.R
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.controls.history.BgHistoryChartOrDefault
import de.dh.raaps.ui.controls.history.DiagramData
import de.dh.raaps.ui.controls.history.HistoryUiState
import de.dh.raaps.ui.controls.history.HistoryViewModel
import de.dh.raaps.ui.controls.history.createSampleHistoryTicks

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
    var menuExpanded by remember { mutableStateOf(false) }

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
                    diagramData = DiagramData.fromTickStates(historyUiState.historyTicks, historyUiState.tickInterval),
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
        historyTicks = createSampleHistoryTicks(120, 5),
        tickInterval = Minutes(5)
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