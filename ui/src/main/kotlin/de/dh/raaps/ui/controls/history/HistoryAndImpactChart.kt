package de.dh.raaps.ui.controls.history

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import de.dh.raaps.common.model.MS_PER_HOUR
import de.dh.raaps.common.model.MS_PER_MINUTE
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.ColorBg
import de.dh.raaps.common.ui.theme.ColorCarbs
import de.dh.raaps.common.ui.theme.ColorInsulin
import java.util.Calendar
import java.util.Locale
import kotlin.math.exp

private const val INITIAL_SHOW_HOURS = 4.0

data class HistoryAndImpactDiagramData(
    val baseTimestamp: Long,
    val minX: Double,
    val maxX: Double,
    // Pre-calculated for performance
    val bgXValues: List<Double>,
    val bgYValues: List<Double>,
    val insulinXValues: List<Double> = emptyList(),
    val insulinYValues: List<Double> = emptyList(),
    val carbXValues: List<Double> = emptyList(),
    val carbYValues: List<Double> = emptyList(),
    val dataSignature: Any
) {
    companion object {
        fun create(
            readings: List<BgReading>,
            insulinXValues: List<Double> = emptyList(),
            insulinYValues: List<Double> = emptyList(),
            carbXValues: List<Double> = emptyList(),
            carbYValues: List<Double> = emptyList(),
        ): HistoryAndImpactDiagramData? {
            val validReadings = readings.filter { it.sampleKind == BgSampleKind.Value }
            if (validReadings.isEmpty()) return null

            val firstTs = validReadings.first().timestamp.ms
            val baseTimestamp = firstTs

            val lastTs = validReadings.last().timestamp.ms
            val minX = 0.0

            val endTs = (((lastTs + MS_PER_HOUR / 2) / MS_PER_HOUR) + 1) * MS_PER_HOUR
            val maxX = (endTs - baseTimestamp).toDouble() / MS_PER_MINUTE

            return HistoryAndImpactDiagramData(
                baseTimestamp = baseTimestamp,
                minX = minX,
                maxX = maxX,
                bgXValues = validReadings.map { ((it.timestamp.ms - baseTimestamp).toDouble() / MS_PER_MINUTE * 10000).toLong() / 10000.0 },
                bgYValues = validReadings.map { it.value.mgdl.toDouble() },
                insulinXValues = insulinXValues,
                insulinYValues = insulinYValues,
                carbXValues = carbXValues,
                carbYValues = carbYValues,
                dataSignature = "${validReadings.size}_${validReadings.first().timestamp.ms}_${validReadings.last().timestamp.ms}"
            )
        }

        fun empty(): HistoryAndImpactDiagramData {
            val now = Timestamp.now().ms
            val baseTimestamp = (now / MS_PER_HOUR) * MS_PER_HOUR
            val maxX = INITIAL_SHOW_HOURS * 60.0
            return HistoryAndImpactDiagramData(
                baseTimestamp = baseTimestamp,
                minX = 0.0,
                maxX = maxX,
                bgXValues = listOf(0.0, maxX),
                bgYValues = listOf(0.0, 0.0),
                insulinXValues = emptyList(),
                insulinYValues = emptyList(),
                carbXValues = emptyList(),
                carbYValues = emptyList(),
                dataSignature = "empty_$baseTimestamp"
            )
        }
    }
}

@Composable
fun HistoryAndImpactChart(
    diagramData: HistoryAndImpactDiagramData,
    modifier: Modifier = Modifier,
    onChartClick: (() -> Unit)? = null,
    controlledState: BgHistoryChartState? = null,
    showInsulin: Boolean = true,
    showCarbs: Boolean = true
) {
    val state = controlledState ?: rememberBgHistoryChartState()
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(diagramData.minX, diagramData.maxX) {
        state.minX = diagramData.minX
        state.maxX = diagramData.maxX
    }

    LaunchedEffect(diagramData.dataSignature, showInsulin, showCarbs) {
        modelProducer.runTransaction {
            lineModel {
                // Series 0: BG (always present)
                series(x = diagramData.bgXValues, y = diagramData.bgYValues)

                // Series 1: Insulin
                val insX = diagramData.insulinXValues.ifEmpty { listOf(0.0) }
                series(
                    x = insX,
                    y = if (showInsulin && diagramData.insulinXValues.isNotEmpty()) {
                        diagramData.insulinYValues
                    } else {
                        insX.map { 0.0 }
                    }
                )

                // Series 2: Carbs
                val carbX = diagramData.carbXValues.ifEmpty { listOf(0.0) }
                series(
                    x = carbX,
                    y = if (showCarbs && diagramData.carbXValues.isNotEmpty()) {
                        diagramData.carbYValues
                    } else {
                        carbX.map { 0.0 }
                    }
                )
            }
        }
    }

    val rangeProvider = remember(diagramData.minX, diagramData.maxX) {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 0.0
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                maxY.coerceAtLeast(200.0) + 20.0
            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.minX
            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.maxX
        }
    }

    val sharedCalendar = remember { Calendar.getInstance() }

    val xAxisValueFormatter = remember(diagramData.baseTimestamp) {
        CartesianValueFormatter { _, x, _ ->
            synchronized(sharedCalendar) {
                sharedCalendar.timeInMillis = diagramData.baseTimestamp + x.toLong() * 60 * 1000L
                String.format(Locale.getDefault(), "%02d:00", sharedCalendar.get(Calendar.HOUR_OF_DAY))
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

    val bgLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(ColorBg)),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(shape = CircleShape, fill = Fill(ColorBg)), size = 4.dp)
        )
    )
    val insulinLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(if (showInsulin) ColorInsulin else Color.Transparent)),
        areaFill = LineCartesianLayer.AreaFill.single(Fill(if (showInsulin) ColorInsulin.copy(alpha = 0.2f) else Color.Transparent)),
        pointProvider = null
    )
    val carbLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(if (showCarbs) ColorCarbs else Color.Transparent)),
        areaFill = LineCartesianLayer.AreaFill.single(Fill(if (showCarbs) ColorCarbs.copy(alpha = 0.2f) else Color.Transparent)),
        pointProvider = null
    )

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(listOf(bgLine, insulinLine, carbLine)),
                rangeProvider = rangeProvider
            ),
            startAxis = VerticalAxis.rememberStart(
                itemPlacer = remember {
                    object : VerticalAxis.ItemPlacer {
                        override fun getLabelValues(context: CartesianDrawingContext, axisHeight: Float, maxLabelHeight: Float, position: Axis.Position.Vertical) =
                            getValues(context.ranges.getYRange(position).maxY)
                        override fun getWidthMeasurementLabelValues(context: CartesianMeasuringContext, axisHeight: Float, maxLabelHeight: Float, position: Axis.Position.Vertical) =
                            getValues(300.0)
                        override fun getHeightMeasurementLabelValues(context: CartesianMeasuringContext, position: Axis.Position.Vertical) =
                            getValues(300.0)
                        private fun getValues(maxY: Double): List<Double> {
                            val v = mutableListOf<Double>()
                            var c = 0.0
                            while (c <= maxY) { v.add(c); c += 50.0 }
                            return v
                        }
                        override fun getTopLayerMargin(context: CartesianMeasuringContext, verticalLabelPosition: Position.Vertical, maxLabelHeight: Float, maxLineThickness: Float) = 0f
                        override fun getBottomLayerMargin(context: CartesianMeasuringContext, verticalLabelPosition: Position.Vertical, maxLabelHeight: Float, maxLineThickness: Float) = 0f
                    }
                },
                horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                line = null
            ),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xAxisValueFormatter, itemPlacer = xItemPlacer),
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

private fun calculateInsulinImpact(tMinutes: Double, startMinutes: Double, units: Double): Double {
    val tHours = (tMinutes - startMinutes) / 60.0
    return if (tHours >= 0) {
        // Approximate insulin action curve (e.g., peak at 1h, duration 4-5h)
        // Scaled so 1 unit has a visible peak impact
        (units * 10.0 * tHours * exp(-tHours)).coerceAtLeast(0.0)
    } else 0.0
}

private fun calculateCarbImpact(tMinutes: Double, startMinutes: Double, grams: Double): Double {
    val tHours = (tMinutes - startMinutes) / 60.0
    return if (tHours >= 0) {
        // Approximate carb absorption curve (e.g., peak at 30-45min)
        // Scaled so 10g have a visible peak impact
        (grams * 10 * tHours * exp(-2.0 * tHours)).coerceAtLeast(0.0)
    } else 0.0
}

fun createSampleImpactDiagramData(): HistoryAndImpactDiagramData {
    val readings = createSampleReadings(120, 5)

    // Events: (Time in Minutes, Amount)
    val insulinEvents = listOf(30.0 to 5.0, 280.0 to 8.0, 450.0 to 4.0)
    val carbEvents = listOf(20.0 to 50.0, 300.0 to 80.0, 460.0 to 40.0)

    val insulinX = mutableListOf<Double>()
    val insulinY = mutableListOf<Double>()
    val carbX = mutableListOf<Double>()
    val carbY = mutableListOf<Double>()

    for (i in 0..600 step 5) {
        val x = i.toDouble()
        insulinX.add(x)
        insulinY.add(insulinEvents.sumOf { calculateInsulinImpact(x, it.first, it.second) })

        carbX.add(x)
        carbY.add(carbEvents.sumOf { calculateCarbImpact(x, it.first, it.second) })
    }

    return HistoryAndImpactDiagramData.create(
        readings = readings,
        insulinXValues = insulinX,
        insulinYValues = insulinY,
        carbXValues = carbX,
        carbYValues = carbY
    )!!.let { it.copy(dataSignature = "impact_${it.dataSignature}") }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HistoryAndImpactChartPreview() {
    val diagramData = remember { createSampleImpactDiagramData() }
    AppTheme {
        HistoryAndImpactChart(
            diagramData = diagramData,
            modifier = Modifier.height(300.dp)
        )
    }
}