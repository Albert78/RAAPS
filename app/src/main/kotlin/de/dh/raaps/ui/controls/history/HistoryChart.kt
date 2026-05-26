package de.dh.raaps.ui.controls.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalBox
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.ui.composables.BlueA200
import de.dh.raaps.common.ui.composables.DeepOrangeA700
import de.dh.raaps.common.ui.composables.RedA700
import de.dh.raaps.common.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

private const val INITIAL_SHOW_HOURS = 4.0
private const val MS_PER_HOUR = 60 * 60 * 1000L
private const val MS_PER_MINUTE = 60 * 1000L

/**
 * State of the BgHistoryChart to observe and control the visible range.
 */
@Stable
class BgHistoryChartState(
    val scrollState: VicoScrollState,
    val zoomState: VicoZoomState
) {
    /**
     * The currently visible X-range in the chart (minutes offset from baseTimestamp).
     */
    var visibleRange by mutableStateOf<ClosedFloatingPointRange<Double>?>(null)
        internal set

    internal var minX by mutableDoubleStateOf(0.0)
    internal var maxX by mutableDoubleStateOf(0.0)

    /**
     * Updates the visible range based on current scroll and actual drawing bounds.
     */
    internal fun updateVisibleRange(layerWidth: Float) {
        val totalXRange = maxX - minX
        if (layerWidth <= 0 || totalXRange <= 0.0 || scrollState.maxValue < 0f) return

        val totalWidthPx = scrollState.maxValue + layerWidth
        val startX = minX + (scrollState.value / totalWidthPx) * totalXRange
        val endX = startX + (layerWidth / totalWidthPx) * totalXRange
        visibleRange = startX..endX
    }

    /**
     * Programmatically sets the visible range of the chart.
     */
    suspend fun setVisibleRange(start: Double, end: Double, layerWidth: Float) {
        val totalXRange = maxX - minX
        val desiredWidth = end - start
        if (desiredWidth <= 0 || layerWidth <= 0 || totalXRange <= 0.0) return

        zoomState.zoom(Zoom.x(desiredWidth))
        scrollState.scroll(Scroll.Absolute.x(x = start))
    }

    /**
     * Scrolls the chart to a specific time offset without changing the zoom level.
     */
    suspend fun scrollTo(start: Double) {
        scrollState.scroll(Scroll.Absolute.x(x = start))
    }
}

@Composable
fun rememberBgHistoryChartState(
    initialShowHours: Double = INITIAL_SHOW_HOURS
): BgHistoryChartState {
    val scrollState = rememberVicoScrollState()
    val zoomState = rememberVicoZoomState(initialZoom = Zoom.x(initialShowHours * 60.0))
    return remember(scrollState, zoomState) {
        BgHistoryChartState(scrollState, zoomState)
    }
}

data class DiagramData(
    val readings: List<BgReading>,
    val baseTimestamp: Long,
    val minX: Double,
    val maxX: Double
) {
    companion object {
        fun fromReadings(readings: List<BgReading>): DiagramData? {
            val validReadings = readings.filter { it.sampleKind == BgSampleKind.Value }
            if (validReadings.isEmpty()) return null

            val firstTs = validReadings.first().timestamp.ms
            val baseTimestamp = (firstTs / MS_PER_HOUR) * MS_PER_HOUR

            val lastTs = validReadings.last().timestamp.ms
            val minX = 0.0

            val endTs = ((lastTs / MS_PER_HOUR) + 1) * MS_PER_HOUR
            val maxX = (endTs - baseTimestamp).toDouble() / MS_PER_MINUTE

            return DiagramData(validReadings, baseTimestamp, minX, maxX)
        }

        fun empty(): DiagramData {
            val now = Timestamp.now().ms
            val baseTimestamp = (now / MS_PER_HOUR) * MS_PER_HOUR
            return DiagramData(emptyList(), baseTimestamp, 0.0, INITIAL_SHOW_HOURS * 60.0)
        }
    }
}

@Composable
fun BgHistoryChart(
    diagramData: DiagramData,
    modifier: Modifier = Modifier,
    lowBgThreshold: Double = 70.0,
    highBgThreshold: Double = 170.0,
    lowBgColor: Color = RedA700.copy(alpha = 0.2f),
    highBgColor: Color = DeepOrangeA700.copy(alpha = 0.3f),
    showMarkers: Boolean = false,
    onChartClick: (() -> Unit)? = null,
    state: BgHistoryChartState = rememberBgHistoryChartState()
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    state.minX = diagramData.minX
    state.maxX = diagramData.maxX

    var isInitialized by remember(diagramData.baseTimestamp) { mutableStateOf(false) }

    LaunchedEffect(diagramData) {
        modelProducer.runTransaction {
            if (diagramData.readings.isEmpty()) {
                lineSeries { series(x = listOf(0.0, diagramData.maxX), y = listOf(0.0, 0.0)) }
            } else {
                lineSeries {
                    series(
                        x = diagramData.readings.map {
                            ((it.timestamp.ms - diagramData.baseTimestamp).toDouble() / MS_PER_MINUTE * 10000).toLong() / 10000.0
                        },
                        y = diagramData.readings.map { it.value.mgdl.toDouble() }
                    )
                }
            }
        }

        // Scroll to the end (latest data) when data is first loaded
        if (!isInitialized && diagramData.readings.isNotEmpty()) {
            state.scrollState.scroll(Scroll.Absolute.End)
            isInitialized = true
        }
    }

    val rangeProvider = remember(diagramData) {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 40.0
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                (maxY.coerceAtLeast(200.0) + 10.0).coerceAtMost(410.0)
            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.minX
            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.maxX
        }
    }

    val xAxisValueFormatter = remember(diagramData) {
        CartesianValueFormatter { _, x, _ ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = diagramData.baseTimestamp + x.toLong() * MS_PER_MINUTE
            }
            String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.HOUR_OF_DAY))
        }
    }

    val xItemPlacer = remember(diagramData) {
        object : HorizontalAxis.ItemPlacer {
            override fun getLabelValues(ctx: CartesianDrawingContext, v: ClosedFloatingPointRange<Double>, f: ClosedFloatingPointRange<Double>, m: Float): List<Double> {
                val spacing = 60.0
                val cal = Calendar.getInstance().apply { timeInMillis = diagramData.baseTimestamp }
                val startOffset = (spacing - ((cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) % spacing)) % spacing
                val values = mutableListOf<Double>()
                var curr = startOffset
                while (curr <= f.endInclusive) {
                    if (curr >= f.start) values.add(curr)
                    curr += spacing
                }
                return values
            }
            override fun getWidthMeasurementLabelValues(ctx: CartesianMeasuringContext, l: CartesianLayerDimensions, f: ClosedFloatingPointRange<Double>) = listOf(f.start, f.endInclusive)
            override fun getHeightMeasurementLabelValues(ctx: CartesianMeasuringContext, l: CartesianLayerDimensions, f: ClosedFloatingPointRange<Double>, m: Float) = listOf(f.start, f.endInclusive)
            override fun getStartLayerMargin(ctx: CartesianMeasuringContext, l: CartesianLayerDimensions, t: Float, m: Float) = 0f
            override fun getEndLayerMargin(ctx: CartesianMeasuringContext, l: CartesianLayerDimensions, t: Float, m: Float) = 0f
        }
    }

    val yItemPlacer = remember {
        object : VerticalAxis.ItemPlacer {
            override fun getLabelValues(ctx: CartesianDrawingContext, h: Float, m: Float, p: Axis.Position.Vertical) = getValues(ctx.ranges.getYRange(p).maxY)
            override fun getWidthMeasurementLabelValues(ctx: CartesianMeasuringContext, h: Float, m: Float, p: Axis.Position.Vertical) = getValues(ctx.ranges.getYRange(p).maxY)
            override fun getHeightMeasurementLabelValues(ctx: CartesianMeasuringContext, p: Axis.Position.Vertical) = getValues(ctx.ranges.getYRange(p).maxY)
            private fun getValues(maxY: Double): List<Double> {
                val v = mutableListOf<Double>()
                var c = 50.0
                while (c <= maxY) { v.add(c); c += 50.0 }
                return v
            }
            override fun getTopLayerMargin(ctx: CartesianMeasuringContext, v: Position.Vertical, m: Float, l: Float) = 0f
            override fun getBottomLayerMargin(ctx: CartesianMeasuringContext, v: Position.Vertical, m: Float, l: Float) = 0f
        }
    }

    val marker = if (showMarkers) rememberDefaultCartesianMarker(
        label = rememberAxisLabelComponent(),
        valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull() ?: return@ValueFormatter ""
            val cal = Calendar.getInstance().apply { timeInMillis = diagramData.baseTimestamp + target.x.toLong() * MS_PER_MINUTE }
            val time = String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val bgValue = (target as? LineCartesianLayerMarkerTarget)?.points?.firstOrNull()?.entry?.y?.toInt() ?: 0
            if (bgValue == 0) time else "$time | $bgValue mg/dL"
        }
    ) else null

    val lowBgBox = rememberShapeComponent(fill = Fill(lowBgColor))
    val highBgBox = rememberShapeComponent(fill = Fill(highBgColor))

    val decorations = remember(lowBgThreshold, highBgThreshold, lowBgBox, highBgBox, state) {
        listOf(
            object : Decoration {
                override fun drawUnderLayers(context: CartesianDrawingContext) {
                    state.updateVisibleRange(context.layerBounds.width)
                    context.canvas.withSave {
                        context.canvas.clipRect(context.layerBounds)
                        HorizontalBox(y = { 0.0..lowBgThreshold }, box = lowBgBox).drawUnderLayers(context)
                        HorizontalBox(y = { highBgThreshold..500.0 }, box = highBgBox).drawUnderLayers(context)
                    }
                }
            }
        )
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                        pointProvider = LineCartesianLayer.PointProvider.single(
                            LineCartesianLayer.Point(
                                rememberShapeComponent(shape = CircleShape, fill = Fill(BlueA200)),
                                size = 6.dp
                            )
                        )
                    )
                ),
                rangeProvider = rangeProvider
            ),
            startAxis = VerticalAxis.rememberStart(itemPlacer = yItemPlacer),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xAxisValueFormatter, itemPlacer = xItemPlacer),
            marker = marker,
            decorations = decorations,
        ),
        modelProducer = modelProducer,
        scrollState = state.scrollState,
        zoomState = state.zoomState,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = onChartClick != null,
            onClick = { onChartClick?.invoke() }
        ),
    )
}

@Composable
fun BgHistoryChartOrDefault(
    diagramData: DiagramData?,
    modifier: Modifier = Modifier,
    lowBgThreshold: Double = 70.0,
    highBgThreshold: Double = 170.0,
    showMarkers: Boolean = false,
    onChartClick: (() -> Unit)? = null,
    state: BgHistoryChartState = rememberBgHistoryChartState()
) {
    BgHistoryChart(diagramData ?: DiagramData.empty(), modifier, lowBgThreshold, highBgThreshold, showMarkers = showMarkers, onChartClick = onChartClick, state = state)
}

@Composable
fun BgOverviewChart(
    diagramData: DiagramData,
    state: BgHistoryChartState,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val scope = rememberCoroutineScope()
    var layerBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(diagramData) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = diagramData.readings.map { ((it.timestamp.ms - diagramData.baseTimestamp).toDouble() / MS_PER_MINUTE * 10000).toLong() / 10000.0 },
                    y = diagramData.readings.map { it.value.mgdl.toDouble() }
                )
            }
        }
    }

    val decorations = remember {
        listOf(object : Decoration {
            override fun drawOverLayers(context: CartesianDrawingContext) {
                layerBounds = context.layerBounds
            }
        })
    }

    Box(modifier = modifier.height(100.dp).fillMaxWidth()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                            pointProvider = LineCartesianLayer.PointProvider.single(
                                LineCartesianLayer.Point(
                                    rememberShapeComponent(shape = CircleShape, fill = Fill(BlueA200.copy(alpha = 0.5f))),
                                    4.dp
                                )
                            )
                        )
                    ),
                    rangeProvider = remember(diagramData) {
                        object : CartesianLayerRangeProvider {
                            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 40.0
                            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = (maxY.coerceAtLeast(200.0) + 10.0).coerceAtMost(410.0)
                            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.minX
                            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.maxX
                        }
                    }
                ),
                startAxis = VerticalAxis.rememberStart(itemPlacer = remember {
                    object : VerticalAxis.ItemPlacer {
                        override fun getLabelValues(ctx: CartesianDrawingContext, h: Float, m: Float, p: Axis.Position.Vertical) = getValues(ctx.ranges.getYRange(p).maxY)
                        override fun getWidthMeasurementLabelValues(ctx: CartesianMeasuringContext, h: Float, m: Float, p: Axis.Position.Vertical) = getValues(ctx.ranges.getYRange(p).maxY)
                        override fun getHeightMeasurementLabelValues(ctx: CartesianMeasuringContext, p: Axis.Position.Vertical) = getValues(ctx.ranges.getYRange(p).maxY)
                        private fun getValues(maxY: Double): List<Double> {
                            val v = mutableListOf<Double>()
                            var c = 150.0
                            while (c <= maxY) { v.add(c); c += 100.0 }
                            return v
                        }
                        override fun getTopLayerMargin(ctx: CartesianMeasuringContext, v: Position.Vertical, m: Float, l: Float) = 0f
                        override fun getBottomLayerMargin(ctx: CartesianMeasuringContext, v: Position.Vertical, m: Float, l: Float) = 0f
                    }
                }),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = { _, x, _ ->
                        val cal = Calendar.getInstance().apply { timeInMillis = diagramData.baseTimestamp + x.toLong() * MS_PER_MINUTE }
                        String.format(Locale.getDefault(), "%02d", cal.get(Calendar.HOUR_OF_DAY))
                    },
                    itemPlacer = remember(diagramData) {
                        object : HorizontalAxis.ItemPlacer {
                            override fun getLabelValues(ctx: CartesianDrawingContext, v: ClosedFloatingPointRange<Double>, f: ClosedFloatingPointRange<Double>, m: Float): List<Double> {
                                val spacing = 360.0
                                val cal = Calendar.getInstance().apply { timeInMillis = diagramData.baseTimestamp }
                                val offset = (spacing - ((cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) % spacing)) % spacing
                                val values = mutableListOf<Double>()
                                var curr = offset; while (curr <= f.endInclusive) { if (curr >= f.start) values.add(curr); curr += spacing }
                                return values
                            }
                            override fun getWidthMeasurementLabelValues(ctx: CartesianMeasuringContext, l: CartesianLayerDimensions, f: ClosedFloatingPointRange<Double>) = listOf(f.start, f.endInclusive)
                            override fun getHeightMeasurementLabelValues(ctx: CartesianMeasuringContext, l: CartesianLayerDimensions, f: ClosedFloatingPointRange<Double>, m: Float) = listOf(f.start, f.endInclusive)
                            override fun getStartLayerMargin(ctx: CartesianMeasuringContext, l: CartesianLayerDimensions, t: Float, m: Float) = 0f
                            override fun getEndLayerMargin(ctx: CartesianMeasuringContext, l: CartesianLayerDimensions, t: Float, m: Float) = 0f
                        }
                    }
                ),
                decorations = decorations
            ),
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
            modifier = Modifier.fillMaxSize()
        )

        val visibleRange = state.visibleRange
        if (visibleRange != null && layerBounds.width > 0) {
            val totalRange = diagramData.maxX - diagramData.minX
            val leftFrac = ((visibleRange.start - diagramData.minX) / totalRange).coerceIn(0.0, 1.0)
            val rightFrac = ((visibleRange.endInclusive - diagramData.minX) / totalRange).coerceIn(0.0, 1.0)

            Canvas(
                modifier = Modifier.fillMaxSize().pointerInput(diagramData, totalRange, layerBounds) {
                    var dragStartRange: ClosedFloatingPointRange<Double>? = null
                    var accumulatedDelta = 0.0
                    detectDragGestures(
                        onDragStart = { dragStartRange = state.visibleRange; accumulatedDelta = 0.0 },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val startRange = dragStartRange ?: return@detectDragGestures
                            accumulatedDelta += (dragAmount.x / layerBounds.width) * totalRange
                            scope.launch { state.scrollTo(startRange.start + accumulatedDelta) }
                        }
                    )
                }
            ) {
                translate(left = layerBounds.left, top = layerBounds.top) {
                    val left = leftFrac.toFloat() * layerBounds.width
                    val right = rightFrac.toFloat() * layerBounds.width
                    drawRect(Color.White.copy(alpha = 0.3f), Offset(left, 0f), Size(right - left, layerBounds.height))
                    drawLine(Color.White, Offset(left, 0f), Offset(left, layerBounds.height), 2.dp.toPx())
                    drawLine(Color.White, Offset(right, 0f), Offset(right, layerBounds.height), 2.dp.toPx())
                }
            }
        }
    }
}

fun generatedBg(minsInterval: Short, index: Int, startTs: Timestamp): BgReading {
    val base = 170.0
    val curve = 100.0 * sin(index * minsInterval / 50.0) + 15.0 * sin(index * minsInterval / 60.0)
    val noise = Random.nextDouble(-5.0, 5.0)
    return BgReading(value = BgValue.fromMgDl((base + curve + noise).toInt().coerceIn(40, 400)), sampleKind = BgSampleKind.Value, timestamp = startTs.plusMinutes(index * minsInterval))
}

fun createSampleReadings(size: Int, minsInterval: Short): List<BgReading> {
    val startTs = Timestamp.now().minusMinutes(minsInterval * size + 10)
    return List(size) { index -> generatedBg(minsInterval, index, startTs) }
}

fun createSampleDiagramData(size: Int, minsInterval: Short): DiagramData {
    val readings = createSampleReadings(size, minsInterval)
    return DiagramData.fromReadings(readings)!!
}

@Preview(showBackground = true)
@Composable
fun HistoryChart5Preview() {
    val diagramData = createSampleDiagramData(120, 5)
    AppTheme { BgHistoryChart(diagramData) }
}

@Preview(showBackground = true)
@Composable
fun HistoryChart1Preview() {
    val diagramData = createSampleDiagramData(600, 1)
    AppTheme { BgHistoryChart(diagramData) }
}

@Preview(showBackground = true)
@Composable
fun HistoryChartDefaultPreview() {
    AppTheme { BgHistoryChartOrDefault(diagramData = null) }
}