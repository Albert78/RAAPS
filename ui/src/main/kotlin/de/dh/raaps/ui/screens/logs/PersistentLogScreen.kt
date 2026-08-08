package de.dh.raaps.ui.screens.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dh.raaps.common.util.PersistentLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistentLogScreen(
    onNavigateUp: () -> Unit
) {
    var logLines by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun loadLogs() {
        scope.launch {
            val file = PersistentLogger.getLogFile()
            if (file != null && file.exists()) {
                val lines = withContext(Dispatchers.IO) {
                    file.readLines().reversed() // Show newest first
                }
                logLines = lines
            } else {
                logLines = listOf("No log file found.")
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Persistent Debug Logs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadLogs() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        PersistentLogger.clearLogs()
                        loadLogs()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(logLines) { line ->
                Text(
                    text = line,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (line.contains("BOLUS")) Color.Magenta else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
        }
    }
}
