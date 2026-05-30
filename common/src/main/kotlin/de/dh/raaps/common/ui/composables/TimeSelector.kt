package de.dh.raaps.common.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Locale

/**
 * A custom time selector that displays the hour and opens a scrollable picker dialog.
 */
@Composable
fun TimeHourSelector(
    hour: Int,
    enabled: Boolean,
    minHour: Int,
    maxHour: Int,
    onHourChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(enabled = enabled) { showDialog = true }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%02d:00", hour),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }

    if (showDialog) {
        HourPickerDialog(
            initialHour = hour,
            minHour = minHour,
            maxHour = maxHour,
            onHourSelected = {
                onHourChanged(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun HourPickerDialog(
    initialHour: Int,
    minHour: Int,
    maxHour: Int,
    onHourSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val itemHeight = 56.dp
    val dialogHeight = 300.dp
    val hours = (minHour..maxHour).toList()
    val initialIndex = hours.indexOf(initialHour).coerceAtLeast(0)
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(180.dp)
                .height(dialogHeight)
        ) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

            Box(contentAlignment = Alignment.Center) {
                // The "Glow" and Stroke marking the center
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .padding(horizontal = 12.dp)
                        .border(
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            RoundedCornerShape(8.dp)
                        )
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = (dialogHeight - itemHeight) / 2),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(hours.size) { index ->
                        val h = hours[index]
                        val isSelected = h == initialHour
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clickable { onHourSelected(h) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:00", h),
                                style = if (isSelected) {
                                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}