package de.dh.raaps.ui.controls.history

import android.content.res.Configuration
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
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
import de.dh.raaps.common.model.MS_PER_HOUR
import de.dh.raaps.common.model.MS_PER_MINUTE
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.ui.composables.BlueA200
import de.dh.raaps.common.ui.composables.DeepOrangeA700
import de.dh.raaps.common.ui.composables.RedA700
import de.dh.raaps.common.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val INITIAL_SHOW_HOURS = 4.0

data class HistoryDiagramData(
    val readings: List<BgReading>,
    val baseTimestamp: Long,
    val minX: Double,
    val maxX: Double,
    // Pre-calculated for performance
    val xValues: List<Double>,
    val yValues: List<Double>,
    val dataSignature: Any
) {
    companion object {
        fun fromReadings(readings: List<BgReading>): HistoryDiagramData? {
            val validReadings = readings.filter { it.sampleKind == BgSampleKind.Value }
            if (validReadings.isEmpty()) return null

            val firstTs = validReadings.first().timestamp.ms
            val baseTimestamp = firstTs

            val lastTs = validReadings.last().timestamp.ms
            val minX = 0.0

            val endTs = (((lastTs + MS_PER_HOUR / 2) / MS_PER_HOUR) + 1) * MS_PER_HOUR
            val maxX = (endTs - baseTimestamp).toDouble() / MS_PER_MINUTE

            return HistoryDiagramData(
                readings = validReadings,
                baseTimestamp = baseTimestamp,
                minX = minX,
                maxX = maxX,
                xValues = validReadings.map { ((it.timestamp.ms - baseTimestamp).toDouble() / MS_PER_MINUTE * 10000).toLong() / 10000.0 },
                yValues = validReadings.map { it.value.mgdl.toDouble() },
                dataSignature = "${validReadings.size}_${validReadings.first().timestamp.ms}_${validReadings.last().timestamp.ms}"
            )
        }

        fun empty(): HistoryDiagramData {
            val now = Timestamp.now().ms
            val baseTimestamp = (now / MS_PER_HOUR) * MS_PER_HOUR
            val maxX = INITIAL_SHOW_HOURS * 60.0
            return HistoryDiagramData(
                readings = emptyList(),
                baseTimestamp = baseTimestamp,
                minX = 0.0,
                maxX = maxX,
                xValues = listOf(0.0, maxX),
                yValues = listOf(0.0, 0.0),
                dataSignature = "empty_$baseTimestamp"
            )
        }
    }
}

@Composable
fun BgHistoryChart(
    diagramData: HistoryDiagramData,
    modifier: Modifier = Modifier,
    lowBgThreshold: Double = 70.0,
    highBgThreshold: Double = 170.0,
    lowBgColor: Color = RedA700.copy(alpha = 0.2f),
    highBgColor: Color = DeepOrangeA700.copy(alpha = 0.3f),
    showMarkers: Boolean = false,
    onChartClick: (() -> Unit)? = null,
    controlledState: BgHistoryChartState? = null
) {
    val state = controlledState ?: rememberBgHistoryChartState()
    val modelProducer = remember { CartesianChartModelProducer() }

    // Update state bounds immediately to avoid "one frame late" issues in derived states
    state.minX = diagramData.minX
    state.maxX = diagramData.maxX

    var initialScrollToEndDone by remember(diagramData.dataSignature) { mutableStateOf(false) }

    LaunchedEffect(diagramData.dataSignature) {
        modelProducer.runTransaction {
            lineModel { series(x = diagramData.xValues, y = diagramData.yValues) }
        }

        if (!initialScrollToEndDone && diagramData.readings.isNotEmpty()) {
            state.scrollState.scroll(Scroll.Absolute.End)
            initialScrollToEndDone = true
        }
    }

    val rangeProvider = remember(diagramData.minX, diagramData.maxX) {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 40.0
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                (maxY.coerceAtLeast(200.0) + 10.0).coerceAtMost(410.0)
            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.minX
            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.maxX
        }
    }

    // Reuse a single Calendar instance for all formatter and placer calls
    val sharedCalendar = remember { Calendar.getInstance() }

    val xAxisValueFormatter = remember(diagramData.baseTimestamp) {
        CartesianValueFormatter { _, x, _ ->
            synchronized(sharedCalendar) {
                sharedCalendar.timeInMillis = diagramData.baseTimestamp + x.toLong() * MS_PER_MINUTE
                String.format(Locale.getDefault(), "%02d", sharedCalendar.get(Calendar.HOUR_OF_DAY))
            }
        }
    }

    val xItemPlacer = remember(diagramData.baseTimestamp) {
        object : HorizontalAxis.ItemPlacer {
            override fun getLabelValues(
                context: CartesianDrawingContext,
                visibleXRange: ClosedFloatingPointRange<Double>,
                fullXRange: ClosedFloatingPointRange<Double>,
                maxLabelWidth: Float
            ): List<Double> {
                val spacing = 60.0
                val startOffset = synchronized(sharedCalendar) {
                    sharedCalendar.timeInMillis = diagramData.baseTimestamp
                    (spacing - ((sharedCalendar.get(Calendar.HOUR_OF_DAY) * 60 + sharedCalendar.get(Calendar.MINUTE)) % spacing)) % spacing
                }
                val values = mutableListOf<Double>()
                var curr = startOffset
                while (curr <= fullXRange.endInclusive) {
                    if (curr >= fullXRange.start) values.add(curr)
                    curr += spacing
                }
                return values
            }

            override fun getWidthMeasurementLabelValues(
                context: CartesianMeasuringContext,
                layerDimensions: CartesianLayerDimensions,
                fullXRange: ClosedFloatingPointRange<Double>
            ) = listOf(fullXRange.start, fullXRange.endInclusive)

            override fun getHeightMeasurementLabelValues(
                context: CartesianMeasuringContext,
                layerDimensions: CartesianLayerDimensions,
                fullXRange: ClosedFloatingPointRange<Double>,
                maxLabelWidth: Float
            ) = listOf(fullXRange.start, fullXRange.endInclusive)

            override fun getStartLayerMargin(
                context: CartesianMeasuringContext,
                layerDimensions: CartesianLayerDimensions,
                tickThickness: Float,
                maxLabelWidth: Float
            ) = 0f

            override fun getEndLayerMargin(
                context: CartesianMeasuringContext,
                layerDimensions: CartesianLayerDimensions,
                tickThickness: Float,
                maxLabelWidth: Float
            ) = 0f
        }
    }

    val yItemPlacer = remember {
        object : VerticalAxis.ItemPlacer {
            override fun getLabelValues(
                context: CartesianDrawingContext,
                axisHeight: Float,
                maxLabelHeight: Float,
                position: Axis.Position.Vertical
            ) = getValues(context.ranges.getYRange(position).maxY)

            override fun getWidthMeasurementLabelValues(
                context: CartesianMeasuringContext,
                axisHeight: Float,
                maxLabelHeight: Float,
                position: Axis.Position.Vertical
            ) = getValues(context.ranges.getYRange(position).maxY)

            override fun getHeightMeasurementLabelValues(
                context: CartesianMeasuringContext,
                position: Axis.Position.Vertical
            ) = getValues(context.ranges.getYRange(position).maxY)

            private fun getValues(maxY: Double): List<Double> {
                val v = mutableListOf<Double>()
                var c = 50.0
                while (c <= maxY) { v.add(c); c += 50.0 }
                return v
            }

            override fun getTopLayerMargin(
                context: CartesianMeasuringContext,
                verticalLabelPosition: Position.Vertical,
                maxLabelHeight: Float,
                maxLineThickness: Float
            ) = 0f

            override fun getBottomLayerMargin(
                context: CartesianMeasuringContext,
                verticalLabelPosition: Position.Vertical,
                maxLabelHeight: Float,
                maxLineThickness: Float
            ) = 0f
        }
    }

    val marker = if (showMarkers) rememberDefaultCartesianMarker(
        label = rememberAxisLabelComponent(),
        valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull() ?: return@ValueFormatter ""
            val time = synchronized(sharedCalendar) {
                sharedCalendar.timeInMillis = diagramData.baseTimestamp + target.x.toLong() * MS_PER_MINUTE
                String.format(Locale.getDefault(), "%02d:%02d", sharedCalendar.get(Calendar.HOUR_OF_DAY), sharedCalendar.get(Calendar.MINUTE))
            }
            val bgValue = (target as? LineCartesianLayerMarkerTarget)?.points?.firstOrNull()?.entry?.y?.toInt() ?: 0
            if (bgValue == 0) time else "$time | $bgValue mg/dL"
        }
    ) else null

    val lowBgBox = rememberShapeComponent(fill = Fill(lowBgColor))
    val highBgBox = rememberShapeComponent(fill = Fill(highBgColor))

    val decorations = remember(lowBgThreshold, highBgThreshold, lowBgBox, highBgBox, controlledState) {
        listOf(
            // Low BG range
            HorizontalBox(y = { 0.0..lowBgThreshold }, box = lowBgBox),
            // High BG range
            HorizontalBox(y = { highBgThreshold..500.0 }, box = highBgBox)
        ).map { decoration ->
            object : Decoration {
                override fun drawUnderLayers(context: CartesianDrawingContext) {
                    // Side effect: Report drawing width to the state
                    if (state.layerWidth != context.layerBounds.width) {
                        state.layerWidth = context.layerBounds.width
                    }

                    context.canvas.withSave {
                        context.canvas.clipRect(context.layerBounds)
                        decoration.drawUnderLayers(context)
                    }
                }

                override fun drawOverLayers(context: CartesianDrawingContext) {
                    context.canvas.withSave {
                        context.canvas.clipRect(context.layerBounds)
                        decoration.drawOverLayers(context)
                    }
                }
            }
        }
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
            startAxis = VerticalAxis.rememberStart(
                itemPlacer = yItemPlacer,
                size = BaseAxis.Size.Fixed(45.dp)
            ),
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
    diagramData: HistoryDiagramData?,
    modifier: Modifier = Modifier,
    lowBgThreshold: Double = 70.0,
    highBgThreshold: Double = 170.0,
    showMarkers: Boolean = false,
    onChartClick: (() -> Unit)? = null,
    state: BgHistoryChartState? = null
) {
    val data = diagramData ?: remember { HistoryDiagramData.empty() }
    BgHistoryChart(data, modifier, lowBgThreshold, highBgThreshold, showMarkers = showMarkers, onChartClick = onChartClick, controlledState = state)
}

@Composable
fun BgOverviewChart(
    diagramData: HistoryDiagramData,
    state: BgHistoryChartState,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val scope = rememberCoroutineScope()
    var layerBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(diagramData.dataSignature) {
        modelProducer.runTransaction {
            lineModel { series(x = diagramData.xValues, y = diagramData.yValues) }
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
                    rangeProvider = remember(diagramData.minX, diagramData.maxX) {
                        object : CartesianLayerRangeProvider {
                            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 40.0
                            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = (maxY.coerceAtLeast(200.0) + 10.0).coerceAtMost(410.0)
                            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.minX
                            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.maxX
                        }
                    }
                ),
                startAxis = VerticalAxis.rememberStart(
                    size = BaseAxis.Size.Fixed(45.dp),
                    itemPlacer = remember {
                    object : VerticalAxis.ItemPlacer {
                        override fun getLabelValues(
                            context: CartesianDrawingContext,
                            axisHeight: Float,
                            maxLabelHeight: Float,
                            position: Axis.Position.Vertical
                        ) = getValues(context.ranges.getYRange(position).maxY)

                        override fun getWidthMeasurementLabelValues(
                            context: CartesianMeasuringContext,
                            axisHeight: Float,
                            maxLabelHeight: Float,
                            position: Axis.Position.Vertical
                        ) = getValues(context.ranges.getYRange(position).maxY)

                        override fun getHeightMeasurementLabelValues(
                            context: CartesianMeasuringContext,
                            position: Axis.Position.Vertical
                        ) = getValues(context.ranges.getYRange(position).maxY)

                        private fun getValues(maxY: Double): List<Double> {
                            val v = mutableListOf<Double>()
                            var c = 150.0
                            while (c <= maxY) { v.add(c); c += 100.0 }
                            return v
                        }

                        override fun getTopLayerMargin(
                            context: CartesianMeasuringContext,
                            verticalLabelPosition: Position.Vertical,
                            maxLabelHeight: Float,
                            maxLineThickness: Float
                        ) = 0f

                        override fun getBottomLayerMargin(
                            context: CartesianMeasuringContext,
                            verticalLabelPosition: Position.Vertical,
                            maxLabelHeight: Float,
                            maxLineThickness: Float
                        ) = 0f
                    }
                }),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = { _, x, _ ->
                        val cal = Calendar.getInstance().apply { timeInMillis = diagramData.baseTimestamp + x.toLong() * MS_PER_MINUTE }
                        String.format(Locale.getDefault(), "%02d", cal.get(Calendar.HOUR_OF_DAY))
                    },
                    itemPlacer = remember(diagramData.baseTimestamp) {
                        object : HorizontalAxis.ItemPlacer {
                            override fun getLabelValues(
                                context: CartesianDrawingContext,
                                visibleXRange: ClosedFloatingPointRange<Double>,
                                fullXRange: ClosedFloatingPointRange<Double>,
                                maxLabelWidth: Float
                            ): List<Double> {
                                val spacing = 360.0
                                val cal = Calendar.getInstance().apply { timeInMillis = diagramData.baseTimestamp }
                                val offset = (spacing - ((cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) % spacing)) % spacing
                                val values = mutableListOf<Double>()
                                var curr = offset; while (curr <= fullXRange.endInclusive) { if (curr >= fullXRange.start) values.add(curr); curr += spacing }
                                return values
                            }

                            override fun getWidthMeasurementLabelValues(
                                context: CartesianMeasuringContext,
                                layerDimensions: CartesianLayerDimensions,
                                fullXRange: ClosedFloatingPointRange<Double>
                            ) = listOf(fullXRange.start, fullXRange.endInclusive)

                            override fun getHeightMeasurementLabelValues(
                                context: CartesianMeasuringContext,
                                layerDimensions: CartesianLayerDimensions,
                                fullXRange: ClosedFloatingPointRange<Double>,
                                maxLabelWidth: Float
                            ) = listOf(fullXRange.start, fullXRange.endInclusive)

                            override fun getStartLayerMargin(
                                context: CartesianMeasuringContext,
                                layerDimensions: CartesianLayerDimensions,
                                tickThickness: Float,
                                maxLabelWidth: Float
                            ) = 0f

                            override fun getEndLayerMargin(
                                context: CartesianMeasuringContext,
                                layerDimensions: CartesianLayerDimensions,
                                tickThickness: Float,
                                maxLabelWidth: Float
                            ) = 0f
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
                modifier = Modifier.fillMaxSize().pointerInput(diagramData.dataSignature, totalRange) {
                    var dragStartRange: ClosedFloatingPointRange<Double>? = null
                    var accumulatedDelta = 0.0
                    detectDragGestures(
                        onDragStart = { dragStartRange = state.visibleRange; accumulatedDelta = 0.0 },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val startRange = dragStartRange ?: return@detectDragGestures
                            val currentLayerWidth = layerBounds.width
                            if (currentLayerWidth > 0) {
                                accumulatedDelta += (dragAmount.x / currentLayerWidth) * totalRange
                                scope.launch { state.scrollTo(startRange.start + accumulatedDelta) }
                            }
                        }
                    )
                }
            ) {
                translate(left = layerBounds.left, top = layerBounds.top) {
                    val left = leftFrac.toFloat() * layerBounds.width
                    val right = rightFrac.toFloat() * layerBounds.width
                    drawRect(Color.White.copy(alpha = 0.3f), Offset(left, 0f), Size(right - left, layerBounds.height))
                    drawRect(Color.White, Offset(left, 0f), Size(right - left, layerBounds.height), style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

fun createSampleDiagramData(size: Int, minsInterval: Short): HistoryDiagramData {
    val readings = createSampleReadings(size, minsInterval)
    return HistoryDiagramData.fromReadings(readings)!!
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HistoryChart5Preview() {
    val diagramData = remember { createSampleDiagramData(120, 5) }
    AppTheme { BgHistoryChart(diagramData, modifier = Modifier.height(300.dp)) }
}

@Preview(showBackground = true)
@Composable
fun HistoryChart1Preview() {
    val diagramData = remember { createSampleDiagramData(600, 1) }
    AppTheme { BgHistoryChart(diagramData, modifier = Modifier.height(300.dp)) }
}

@Preview(showBackground = true)
@Composable
fun HistoryChartDefaultPreview() {
    AppTheme { BgHistoryChartOrDefault(diagramData = null, modifier = Modifier.height(300.dp)) }
}