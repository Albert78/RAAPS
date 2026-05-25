package de.dh.raaps.ui.controls.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Scroll
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
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.ui.composables.BlueA200
import de.dh.raaps.common.ui.composables.DeepOrangeA700
import de.dh.raaps.common.ui.composables.RedA700
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.core.aps.PredictionTickState
import java.util.Calendar
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

private const val INITIAL_SHOW_HOURS = 4.0

data class DiagramData(
    val tickStates: List<PredictionTickState?>,
    val tickInterval: Minutes,
    val validIndices: List<Int>,
    val tickAtIndex0: Int,
    val minX: Int,
    val maxX: Int
) {
    companion object {
        fun fromTickStates(tickStates: List<PredictionTickState?>, tickInterval: Minutes): DiagramData? {
            // Our tick states array contains an entry every tickInterval minutes, but some tick states
            // are empty or contain an invalid glucose value. Create index on valid values.
            val validBgValueIndices = tickStates.indices.filter {
                val bg = tickStates[it]?.bg ?: return@filter false
                bg.sampleKind != BgSampleKind.Invalid
            }

            if (validBgValueIndices.isEmpty()) {
                return null
            }

            // The first tick with is visible in the diagram.
            // We always anchor the diagram index 0, so find the tick contained at index 0 or,
            // if we don't find a valid tick there, find the first valid tick and calculate which
            // tick would be found at index 0 otherwise.
            val firstValidTickIndex = tickStates.indexOfFirst { it != null }
            if (firstValidTickIndex == -1) {
                return null
            }
            val firstValidTick = tickStates[firstValidTickIndex]!!.tick.value

            // Needed for the conversion time <-> x value:
            // - tickAtIndex0 * tickInterval is the time in ms since epoch start
            // - The x value is aligned on the index in our tickStates list.
            val tickAtIndex0 = firstValidTick - firstValidTickIndex
            val tickAtEndIndex = tickAtIndex0 + tickStates.size

            val ticksPerHour = 60 / tickInterval.value

            // For the low value, we don't need to shift the min value to the past because typically,
            // There is more data before our tick states and the diagram should just cut the series.
//            val minTick = (tickAtIndex0 / ticksPerHour) * ticksPerHour - 10 / tickInterval.value
//            val minX = minTick - tickAtIndex0
            val minX = 0

            // Enforce that after the last tick, the next full hour plus 10 minutes is visible in diagram.
            // The calculation in time can be calculated via the tick.
            val maxTick = ((tickAtEndIndex + ticksPerHour - 2) / ticksPerHour) * ticksPerHour + 10 / tickInterval.value
            val maxX = maxTick - tickAtIndex0

            return DiagramData(
                tickStates,
                tickInterval,
                validBgValueIndices,
                tickAtIndex0,
                minX,
                maxX
            )
        }

        fun empty(tickInterval: Minutes = Minutes(5)): DiagramData {
            val tickSizeMs = tickInterval.value * 60 * 1000

            val timestamp = Timestamp.now()
            val tickAtIndex0 = (timestamp.ms / tickSizeMs).toInt()
            return DiagramData(
                tickStates = List(2) { null },
                tickInterval = tickInterval,
                validIndices = listOf(),
                tickAtIndex0 = tickAtIndex0,
                minX = 0,
                maxX = 4 * 60 / tickInterval.value // 4 hours
            )
        }
    }
}

// TODO: Make it also usable for MMOL
@Composable
fun BgHistoryChart(
    diagramData: DiagramData,
    modifier: Modifier = Modifier,
    lowBgThreshold: Double = 70.0,
    highBgThreshold: Double = 170.0,
    showMarkers: Boolean = false,
    onChartClick: (() -> Unit)? = null
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(diagramData) {
        modelProducer.runTransaction {
            if (diagramData.validIndices.isEmpty()) {
                lineSeries {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            } else{
                lineSeries {
                    series(
                        x = diagramData.validIndices,
                        y = diagramData.validIndices.map { diagramData.tickStates[it]!!.bg!!.value.mgdl.toFloat() }
                    )
                }
            }
        }
    }

    val rangeProvider = remember(diagramData) {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 40.0
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
                val baseMax = maxY.coerceAtLeast(200.0) + 10.0
                return baseMax.coerceAtMost(410.0)
            }

            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
                return diagramData.minX.toDouble()
            }

            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
                return diagramData.maxX.toDouble()
            }
        }
    }

    val xAxisValueFormatter = remember(diagramData) {
        CartesianValueFormatter { _, x, _ ->
            val virtualTick = diagramData.tickAtIndex0 + x.toInt()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = virtualTick * diagramData.tickInterval.value * 60_000L
            }
            String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.HOUR_OF_DAY))
        }
    }

    val xItemPlacer = remember(diagramData) {
        val ticksPerHour = 60 / diagramData.tickInterval.value.toInt()

        val ticksMinXToNextHour = ticksPerHour - (diagramData.tickAtIndex0 + diagramData.minX) % ticksPerHour

        HorizontalAxis.ItemPlacer.aligned(spacing = { ticksPerHour }, offset = { ticksMinXToNextHour })
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

    val scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
    val zoomState = rememberVicoZoomState (
        initialZoom = Zoom.x(INITIAL_SHOW_HOURS * 60.0 / diagramData.tickInterval.value.toDouble())
    )

    val markerValueFormatter = remember(diagramData) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val x = targets.firstOrNull()?.x ?: return@ValueFormatter ""

            val virtualTick = diagramData.tickAtIndex0 + x.toInt()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = virtualTick * diagramData.tickInterval.value * 60_000L
            }
            val timeStr = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
            )

            val bgValue = diagramData.tickStates.getOrNull(x.toInt())?.bg?.value?.mgdl?.toInt() ?: 0
            if (bgValue == 0) return@ValueFormatter timeStr

            "$timeStr | $bgValue mg/dL"
        }
    }

    val marker = if (showMarkers) rememberDefaultCartesianMarker(
        label = rememberAxisLabelComponent(),
        valueFormatter = markerValueFormatter
    ) else null

    val lowBgBox = rememberShapeComponent(fill = Fill(RedA700.copy(alpha = 0.2f)))
    val highBgBox = rememberShapeComponent(fill = Fill(DeepOrangeA700.copy(alpha = 0.3f)))

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
                    // Style for BG values (Blue)
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
        scrollState = scrollState,
        zoomState = zoomState,
        modifier = modifier.clickable(
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
    onChartClick: (() -> Unit)? = null
) {
    BgHistoryChart(
        diagramData ?: DiagramData.empty(),
        modifier,
        lowBgThreshold,
        highBgThreshold,
        showMarkers,
        onChartClick
    )
}

fun generatedBg(minsInterval: Short, index: Int, startTs: Timestamp): BgReading {
    val base = 170.0
    val curve = 100.0 * sin(index * minsInterval / 50.0) + 15.0 * sin(index * minsInterval / 60.0)
    val noise = Random.nextDouble(-5.0, 5.0)

    val bgValue = (base + curve + noise).toInt().coerceIn(40, 400)

    return BgReading(
        BgValue.fromMgDl(bgValue),
        BgSampleKind.Value,
        Timestamp(startTs.ms + index * minsInterval * 60_000L)
    )
}

fun createSampleHistoryTicks(size: Int, minsInterval: Short, startTs: Timestamp = Timestamp.now()): List<PredictionTickState> {
    return List(size) { index ->
        val bg = generatedBg(minsInterval, index, startTs)
        PredictionTickState(
            ID_UNDEFINED, Tick((bg.timestamp.ms / (minsInterval * 60_000L)).toInt()), bg
        )
    }
}

fun createSampleDiagramData(size: Int, minsInterval: Short, startTs: Timestamp = Timestamp.now()): DiagramData {
    val tickStates = createSampleHistoryTicks(size, minsInterval, startTs)
    return DiagramData.fromTickStates(tickStates, Minutes(minsInterval))!!
}

@Preview(showBackground = true)
@Composable
fun HistoryChart5Preview() {
    val MS_IN_HOUR = 60 * 60 * 1000L
    val MS_IN_TEN_MINUTES = 10 * 60 * 1000L
    val startTs: Timestamp = Timestamp((System.currentTimeMillis() / MS_IN_HOUR) * MS_IN_HOUR + MS_IN_TEN_MINUTES)
    val diagramData = createSampleDiagramData(120, 5, startTs)
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