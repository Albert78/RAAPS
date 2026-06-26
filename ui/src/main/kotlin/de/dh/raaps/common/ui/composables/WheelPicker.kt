package de.dh.raaps.common.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A generic wheel picker button that opens a selection dialog.
 */
@Composable
fun <T> WheelPicker(
    value: T,
    options: List<T>,
    onValueSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dialogTitle: String? = null,
    dialogWidth: Dp = 200.dp
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
            text = labelProvider(value),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }

    if (showDialog) {
        WheelPickerDialog(
            initialValue = value,
            options = options,
            onValueSelected = onValueSelected,
            onDismiss = { showDialog = false },
            labelProvider = labelProvider,
            width = dialogWidth
        )
    }
}

/**
 * A dialog containing a scrollable list for value selection.
 */
@Composable
fun <T> WheelPickerDialog(
    initialValue: T,
    options: List<T>,
    onValueSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    labelProvider: (T) -> String,
    width: Dp = 180.dp
) {
    val itemHeight = 56.dp
    val dialogHeight = 300.dp
    val density = LocalDensity.current

    // Remember the index ONLY when the dialog opens to prevent re-initialization of listState
    val initialIndex = remember { options.indexOf(initialValue).coerceAtLeast(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(width)
                .height(dialogHeight)
        ) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
            val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

            // Local state for the index that is currently in the center, derived from scroll
            val currentCenteredIndex by remember {
                derivedStateOf {
                    val itemHeightPx = with(density) { itemHeight.toPx() }
                    val index = listState.firstVisibleItemIndex
                    val offset = listState.firstVisibleItemScrollOffset
                    if (offset > itemHeightPx / 2) index + 1 else index
                }
            }

            // Sync the external state with the current center
            LaunchedEffect(currentCenteredIndex) {
                if (currentCenteredIndex in options.indices) {
                    onValueSelected(options[currentCenteredIndex])
                }
            }

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
                    flingBehavior = snapFlingBehavior,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = (dialogHeight - itemHeight) / 2),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(options.size) { index ->
                        val item = options[index]
                        val isCentered = index == currentCenteredIndex

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clickable {
                                    onValueSelected(item)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = labelProvider(item),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isCentered) FontWeight.ExtraBold else FontWeight.Normal
                                ),
                                color = if (isCentered) {
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