package de.dh.raaps.ui.controls.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.layout.onSizeChanged
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
import java.util.Calendar
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

private const val INITIAL_SHOW_HOURS = 4.0
private const val MS_PER_HOUR = 60 * 60 * 1000L

/**
 * State of the BgHistoryChart to observe and control the visible range.
 */
@Stable
class BgHistoryChartState(
    val scrollState: VicoScrollState,
    val zoomState: VicoZoomState
) {
    /**
     * The currently visible X-range in the chart (ms offset from baseTimestamp).
     */
    var visibleRange by mutableStateOf<ClosedFloatingPointRange<Double>?>(null)
        internal set

    internal var chartWidth by mutableIntStateOf(0)
    internal var minX by mutableDoubleStateOf(0.0)
    internal var maxX by mutableDoubleStateOf(0.0)

    /**
     * Updates the visible range based on current scroll, zoom and chart width.
     */
    internal fun updateVisibleRange() {
        val totalXRange = maxX - minX
        if (chartWidth <= 0 || totalXRange <= 0.0 || scrollState.maxValue < 0f) return

        val totalWidthPx = scrollState.maxValue + chartWidth
        val startX = minX + (scrollState.value / totalWidthPx) * totalXRange
        val endX = startX + (chartWidth / totalWidthPx) * totalXRange
        visibleRange = startX..endX
    }

    /**
     * Programmatically sets the visible range of the chart.
     */
    suspend fun setVisibleRange(start: Double, end: Double) {
        val totalXRange = maxX - minX
        val desiredWidth = end - start
        if (desiredWidth <= 0 || chartWidth <= 0 || totalXRange <= 0.0) return

        zoomState.zoom(Zoom.x(desiredWidth))
        
        // Use Vico's built-in scroll-to-x functionality
        scrollState.scroll(Scroll.Absolute.x(x = start))
    }
}

@Composable
fun rememberBgHistoryChartState(
    initialShowHours: Double = INITIAL_SHOW_HOURS
): BgHistoryChartState {
    val scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
    val zoomState = rememberVicoZoomState(initialZoom = Zoom.x(initialShowHours * MS_PER_HOUR))
    return remember(scrollState, zoomState) {
        BgHistoryChartState(scrollState, zoomState)
    }
}

data class DiagramData(
    val readings: List<BgReading>,
    // All X values in the diagram are offsets to baseTimestamp
    val baseTimestamp: Long,
    val minX: Double,
    val maxX: Double
) {
    companion object {
        fun fromReadings(readings: List<BgReading>): DiagramData? {
            val validReadings = readings.filter { it.sampleKind == BgSampleKind.Value }
            if (validReadings.isEmpty()) return null

            // Use the start of the first hour of the first reading as base
            val firstTs = validReadings.first().timestamp.ms
            val baseTimestamp = (firstTs / MS_PER_HOUR) * MS_PER_HOUR

            val lastTs = validReadings.last().timestamp.ms
            val minX = 0.0 // Always start at the full hour defined by baseTimestamp

            // Show up to the next full hour after the last reading
            val endTs = ((lastTs / MS_PER_HOUR) + 1) * MS_PER_HOUR
            val maxX = (endTs - baseTimestamp).toDouble()

            return DiagramData(
                validReadings,
                baseTimestamp,
                minX,
                maxX
            )
        }

        fun empty(): DiagramData {
            val now = Timestamp.now().ms
            val baseTimestamp = (now / MS_PER_HOUR) * MS_PER_HOUR
            return DiagramData(
                readings = emptyList(),
                baseTimestamp = baseTimestamp,
                minX = 0.0,
                maxX = INITIAL_SHOW_HOURS * MS_PER_HOUR
            )
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

    // Update state with current diagram bounds
    state.minX = diagramData.minX
    state.maxX = diagramData.maxX

    LaunchedEffect(diagramData) {
        modelProducer.runTransaction {
            if (diagramData.readings.isEmpty()) {
                lineSeries {
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            } else {
                lineSeries {
                    series(
                        x = diagramData.readings.map { (it.timestamp.ms - diagramData.baseTimestamp).toDouble() },
                        y = diagramData.readings.map { it.value.mgdl.toDouble() }
                    )
                }
            }
        }
    }

    // Sync Vico state changes back to our visibleRange
    LaunchedEffect(state.scrollState.value, state.scrollState.maxValue, state.chartWidth, diagramData) {
        state.updateVisibleRange()
    }

    val rangeProvider = remember(diagramData) {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 40.0
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
                val baseMax = maxY.coerceAtLeast(200.0) + 10.0
                return baseMax.coerceAtMost(410.0)
            }

            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
                return diagramData.minX
            }

            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
                return diagramData.maxX
            }
        }
    }

    val xAxisValueFormatter = remember(diagramData) {
        CartesianValueFormatter { _, x, _ ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = diagramData.baseTimestamp + x.toLong()
            }
            String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.HOUR_OF_DAY))
        }
    }

    val xItemPlacer = remember(diagramData) {
        // Place a label at every full hour
        HorizontalAxis.ItemPlacer.aligned(
            spacing = { MS_PER_HOUR.toInt() },
            offset = { 0 }
        )
    }

    val yItemPlacer = remember {
        object : VerticalAxis.ItemPlacer {
            override fun getLabelValues(
                context: CartesianDrawingContext,
                axisHeight: Float,
                maxLabelHeight: Float,
                position: Axis.Position.Vertical,
            ): List<Double> = getValues(context.ranges.getYRange(position).maxY)

            override fun getWidthMeasurementLabelValues(
                context: CartesianMeasuringContext,
                axisHeight: Float,
                maxLabelHeight: Float,
                position: Axis.Position.Vertical,
            ): List<Double> = getValues(context.ranges.getYRange(position).maxY)

            override fun getHeightMeasurementLabelValues(
                context: CartesianMeasuringContext,
                position: Axis.Position.Vertical,
            ): List<Double> = getValues(context.ranges.getYRange(position).maxY)

            private fun getValues(maxY: Double): List<Double> {
                val values = mutableListOf<Double>()
                var current = 50.0
                while (current <= maxY) {
                    values.add(current)
                    current += 50.0
                }
                return values
            }

            override fun getTopLayerMargin(
                context: CartesianMeasuringContext,
                verticalLabelPosition: Position.Vertical,
                maxLabelHeight: Float,
                maxLineThickness: Float
            ): Float = 0f

            override fun getBottomLayerMargin(
                context: CartesianMeasuringContext,
                verticalLabelPosition: Position.Vertical,
                maxLabelHeight: Float,
                maxLineThickness: Float
            ): Float = 0f
        }
    }

    val markerValueFormatter = remember(diagramData) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull() ?: return@ValueFormatter ""
            val x = target.x
            val timestamp = diagramData.baseTimestamp + x.toLong()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = timestamp
            }
            val timeStr = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
            )

            val bgValue = (target as? LineCartesianLayerMarkerTarget)?.points?.firstOrNull()?.entry?.y?.toInt() ?: 0
            if (bgValue == 0) return@ValueFormatter timeStr

            "$timeStr | $bgValue mg/dL"
        }
    }

    val marker = if (showMarkers) rememberDefaultCartesianMarker(
        label = rememberAxisLabelComponent(),
        valueFormatter = markerValueFormatter
    ) else null

    val lowBgBox = rememberShapeComponent(fill = Fill(lowBgColor))
    val highBgBox = rememberShapeComponent(fill = Fill(highBgColor))

    val decorations = remember(lowBgThreshold, highBgThreshold, lowBgBox, highBgBox) {
        listOf(
            HorizontalBox(
                y = { 0.0..lowBgThreshold },
                box = lowBgBox
            ),
            HorizontalBox(
                y = { highBgThreshold..500.0 },
                box = highBgBox
            )
        ).map { decoration ->
            object : Decoration {
                override fun drawUnderLayers(context: CartesianDrawingContext) {
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
                                rememberShapeComponent(
                                    shape = CircleShape,
                                    fill = Fill(BlueA200)
                                ),
                                size = 6.dp
                            )
                        )
                    )
                ),
                rangeProvider = rangeProvider
            ),
            startAxis = VerticalAxis.rememberStart(itemPlacer = yItemPlacer),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = xAxisValueFormatter,
                itemPlacer = xItemPlacer
            ),
            marker = marker,
            decorations = decorations,
        ),
        modelProducer = modelProducer,
        scrollState = state.scrollState,
        zoomState = state.zoomState,
        modifier = modifier
            .onSizeChanged { state.chartWidth = it.width }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // No ripple to not interfere with chart visuals
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
    BgHistoryChart(
        diagramData = diagramData ?: DiagramData.empty(),
        modifier = modifier,
        lowBgThreshold = lowBgThreshold,
        highBgThreshold = highBgThreshold,
        showMarkers = showMarkers,
        onChartClick = onChartClick,
        state = state
    )
}

fun generatedBg(minsInterval: Short, index: Int, startTs: Timestamp): BgReading {
    val base = 170.0
    val curve = 100.0 * sin(index * minsInterval / 50.0) + 15.0 * sin(index * minsInterval / 60.0)
    val noise = Random.nextDouble(-5.0, 5.0)

    val bgValue = (base + curve + noise).toInt().coerceIn(40, 400)

    return BgReading(
        value = BgValue.fromMgDl(bgValue),
        sampleKind = BgSampleKind.Value,
        timestamp = startTs.plusMinutes(index * minsInterval)
    )
}

fun createSampleReadings(size: Int, minsInterval: Short): List<BgReading> {
    val startTs = Timestamp.now().minusMinutes(minsInterval * size + 10)
    return List(size) { index ->
        generatedBg(minsInterval, index, startTs)
    }
}

fun createSampleDiagramData(size: Int, minsInterval: Short): DiagramData {
    val readings = createSampleReadings(size, minsInterval)
    return DiagramData.fromReadings(readings)!!
}

@Preview(showBackground = true)
@Composable
fun HistoryChart5Preview() {
    val diagramData = createSampleDiagramData(120, 5)
    AppTheme {
        BgHistoryChart(
            diagramData
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryChart1Preview() {
    val diagramData = createSampleDiagramData(600, 1)
    AppTheme {
        BgHistoryChart(
            diagramData
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryChartDefaultPreview() {
    AppTheme {
        BgHistoryChartOrDefault(
            diagramData = null
        )
    }
}