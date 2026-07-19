package de.dh.raaps.common.ui.composables

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Immutable
data class PickerItems<T>(val list: List<T>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> Picker(
    items: PickerItems<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 3,
    textModifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    itemColor: (T) -> Color = { Color.Unspecified },
    dividerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    wrapSelectorWheel: Boolean = true,
    label: (T) -> String = { it.toString() }
) {
    val scope = rememberCoroutineScope()

    // Number of items shown in the list before the current item
    val prefixItemsCount = visibleItemsCount / 2

    data class PickerValues(val lazyColumnSize: Int, val virtualItemStartIndex: Int, val virtualUpperBoundary: Int)

    val itemsList = items.list

    // Calculate and cache/remember some values which depend on our inputs
    val pickerValues = remember(items, selectedItem, wrapSelectorWheel, visibleItemsCount) {
        val numItems = itemsList.size
        val itemStartIndex = itemsList.indexOf(selectedItem)
            .coerceIn(0, numItems - 1)

        val virtualUpperBoundary = if (wrapSelectorWheel) Integer.MAX_VALUE else numItems
        val lazyColumnSize = if (wrapSelectorWheel) Integer.MAX_VALUE else numItems + 2 * prefixItemsCount
        val listOffset = if (wrapSelectorWheel) (Integer.MAX_VALUE / numItems) / 2 * numItems else 0
        val virtualItemStartIndex = itemStartIndex + listOffset

        PickerValues(lazyColumnSize, virtualItemStartIndex, virtualUpperBoundary)
    }

    fun getItem(index: Int): T? {
        if (itemsList.isEmpty()) return null
        if (index !in 0..<pickerValues.virtualUpperBoundary) return null // Makes the list show empty items at start and end
        val mappedIndex = (index % itemsList.size + itemsList.size) % itemsList.size
        return itemsList[mappedIndex]
    }

    // The LazyColumn shows prefixItemsCount items before the first item and the same number of
    // items after the last item, so the actual shown list is bigger than our items list.
    // But the selected item's index is always equal to the LazyColumn's first visible index.
    // That's because when fully scrolled to the beginning, both the LazyColumn has index 0 and
    // the item with index 0 is centered.
    // The prefix items above are addressed with a negative index and function getItem returns null for them.

    val listStartIndex = pickerValues.virtualItemStartIndex // +prefixItemsCount -prefixItemsCount
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = listStartIndex)

    // React to the selection being changed from outside.
    // This updates our internal state to the value given from outside.
    LaunchedEffect(pickerValues) {
        val currentCenteredIndex = listState.firstVisibleItemIndex // +prefixItemsCount -prefixItemsCount
        val currentItem = getItem(currentCenteredIndex)

        if (currentItem != selectedItem) {
            val targetVirtualIndex = pickerValues.virtualItemStartIndex // +prefixItemsCount -prefixItemsCount
            listState.animateScrollToItem(targetVirtualIndex)
        }
    }

    // Wait until scrolling is finished before updating the selected item.
    // This updates the caller's state to our internal state.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centeredIndex = listState.firstVisibleItemIndex // +prefixItemsCount -prefixItemsCount
            val centeredItem = getItem(centeredIndex)
            if (centeredItem != null && centeredItem != selectedItem) {
                onItemSelected(centeredItem)
            }
        }
    }

    val fadingEdgeGradient = remember {
        Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Black, 1f to Color.Transparent)
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val defaultItemColor = MaterialTheme.colorScheme.onSurface

    val (pickerWidthDp, lineHeightPx, lineHeightDp) = remember(items, textStyle) {
        var maxWidth = 0;
        var maxHeight = 0;
        if (items.list.isEmpty()) {
            val size = textMeasurer.measure("XXX", textStyle).size
            maxWidth = size.width
            maxHeight = size.height
        } else {
            items.list.forEach { item ->
                val size = textMeasurer.measure(label(item), textStyle).size
                maxWidth = maxWidth.coerceAtLeast(size.width)
                maxHeight = maxHeight.coerceAtLeast(size.height)
            }
        }
        with(density) {
            Triple(
                maxWidth.toDp() + 16.dp, // Add padding
                maxHeight,
                maxHeight.toDp()
            )
        }
    }

    Box(modifier = modifier
        .width(pickerWidthDp)
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .height(lineHeightDp * visibleItemsCount)
                .fadingEdge(fadingEdgeGradient)
                .fillMaxWidth()
        ) {
            fun selectItemAnimated(index: Int) {
                scope.launch {
                    listState.animateScrollToItem(index - prefixItemsCount)
                }
            }

            items(pickerValues.lazyColumnSize) { index ->
                val item = getItem(index - prefixItemsCount)
                //val isSelected = index == listState.firstVisibleItemIndex + prefixItemsCount
                val isSelected = remember(index, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                    val currentFirstIndex = listState.firstVisibleItemIndex
                    val offset = listState.firstVisibleItemScrollOffset

                    // If more then half of the height has been scrolled,
                    // the next item is visually the centered item
                    val visualIndex = if (offset > lineHeightPx / 2) {
                        currentFirstIndex + 1
                    } else {
                        currentFirstIndex
                    }

                    index == visualIndex + prefixItemsCount
                }
                Text(
                    text = item?.let { label(it) } ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = textStyle,
                    color = item?.let { 
                        val c = itemColor(it)
                        if (c != Color.Unspecified) c else defaultItemColor
                    } ?: defaultItemColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = textModifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            selectItemAnimated(index)
                        }
                )
            }
        }

        HorizontalDivider(
            color = dividerColor,
            modifier = Modifier.offset(y = lineHeightDp * prefixItemsCount)
        )

        HorizontalDivider(
            color = dividerColor,
            modifier = Modifier.offset(y = lineHeightDp * (prefixItemsCount + 1))
        )
    }
}

private fun Modifier.fadingEdge(brush: Brush) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }

@Composable
private fun pixelsToDp(pixels: Int): Dp = with(LocalDensity.current) { pixels.toDp() }

// --- Preview Functions ---

@Preview(showBackground = true, name = "Picker Finite")
@Composable
private fun PickerPreviewFinite() {
    AppTheme {
        Surface {
            var selectedItem by remember { mutableStateOf("Sonntag") }
            Picker(
                items = PickerItems(listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")),
                selectedItem = selectedItem,
                onItemSelected = { selectedItem = it },
                textStyle = MaterialTheme.typography.headlineMedium,
                wrapSelectorWheel = false
            )
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Picker Infinite")
@Composable
private fun PickerPreviewInfinite() {
    AppTheme {
        Surface {
            var selectedItem by remember { mutableStateOf(10) }
            Picker(
                items = PickerItems((0..23).toList()),
                selectedItem = selectedItem,
                onItemSelected = { selectedItem = it },
                textStyle = MaterialTheme.typography.headlineMedium,
                label = { "%02d".format(it) }
            )
        }
    }
}