package de.dh.raaps.ui.controls.history

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.DEFAULT_DIA_MINUTES
import de.dh.raaps.common.model.DEFAULT_PEAK_MINUTES
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinCategory
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MS_PER_HOUR
import de.dh.raaps.common.model.MS_PER_MINUTE
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.calculation.CarbCurveComponent
import de.dh.raaps.common.model.calculation.InsulinCurve
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timeline
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.LocalGlucoseUnit
import de.dh.raaps.ui.common.glucoseUnitLabel
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.common.theme.ColorBg
import de.dh.raaps.ui.common.theme.ColorCarbs
import de.dh.raaps.ui.common.theme.ColorInsulin
import de.dh.raaps.ui.common.theme.ExtendedTheme
import java.util.Calendar
import java.util.Locale

private const val INITIAL_SHOW_HOURS = 4.0

data class HistoryAndImpactDiagramData(
    val baseTimestamp: Long,
    val minX: Double,
    val maxX: Double,
    // Pre-calculated for performance
    val bgXValues: List<Double>,
    val bgYValues: List<Double>,
    val glucoseUnit: GlucoseUnit,
    val insulinXValues: List<Double> = emptyList(),
    val insulinYValues: List<Double> = emptyList(),
    val carbXValues: List<Double> = emptyList(),
    val carbYValues: List<Double> = emptyList(),
    val dataSignature: Any
) {
    companion object {
        fun create(
            readings: List<BgReading>,
            glucoseUnit: GlucoseUnit,
            insulinApplications: List<InsulinApplication> = emptyList(),
            meals: List<MealEntry> = emptyList(),
            dia: Minutes = Minutes(DEFAULT_DIA_MINUTES.toShort()),
            peak: Minutes = Minutes(DEFAULT_PEAK_MINUTES.toShort())
        ): HistoryAndImpactDiagramData? {
            /**
             * Axis scales:
             * X-Axis: 1 unit = 1 minute
             * Y-Axis (BG): mg/dL or mmol/l
             * Y-Axis (Impact): Insulin Activity (I.E./h) or Carb Absorption (KE/h)
             * The calculation interval is based on the default system tick interval to match BG data density.
             */
            val validReadings = readings.filter { it.sampleKind == BgSampleKind.Value }
            if (validReadings.isEmpty()) return null

            val firstTs = validReadings.first().timestamp.ms
            val baseTimestamp = firstTs

            val lastReadingTs = validReadings.last().timestamp.ms
            val minX = 0.0

            val now = System.currentTimeMillis()
            val lastFutureEntryTs = (insulinApplications.map { it.timestamp.ms + 4 * MS_PER_HOUR } + meals.map { it.timestamp.ms + 4 * MS_PER_HOUR })
                .filter { it > now }
                .maxOrNull()

            val baseEndTs = (((lastReadingTs + MS_PER_HOUR / 2) / MS_PER_HOUR) + 1) * MS_PER_HOUR
            val endTs = if (lastFutureEntryTs != null) {
                maxOf(baseEndTs, minOf(now + 3 * MS_PER_HOUR, lastFutureEntryTs))
            } else {
                baseEndTs
            }
            val maxX = ((endTs - baseTimestamp).toDouble() / MS_PER_MINUTE).
                coerceAtLeast(INITIAL_SHOW_HOURS * 60.0)

            val insulinX = mutableListOf<Double>()
            val insulinY = mutableListOf<Double>()
            val carbX = mutableListOf<Double>()
            val carbY = mutableListOf<Double>()

            val insulinCurve = InsulinCurve(dia.value.toDouble(), peak.value.toDouble())

            // Optimization: Pre-calculate meal curves
            val mealCurves = meals.map { it.mealType }.distinct().associateWith { mealType ->
                mealType.components.map { comp ->
                    CarbCurveComponent(comp.peakMinutes.value.toDouble()) to comp.weight.toDouble() / 100.0
                }
            }

            for (i in 0..maxX.toInt() step Timeline.DEFAULT_TICK_INTERVAL.value.toInt()) {
                val x = i.toDouble()
                insulinX.add(x)
                carbX.add(x)

                var totalInsulinActivity = 0.0
                for (app in insulinApplications) {
                    val xStart = (app.timestamp.ms - baseTimestamp).toDouble() / MS_PER_MINUTE
                    val timeSinceApp = x - xStart
                    // normalizedActivity * 60.0 gives activity as hourly rate (IU/h)
                    totalInsulinActivity += app.amount.iu * insulinCurve.normalizedActivity(timeSinceApp) * 60.0
                }
                insulinY.add(totalInsulinActivity)

                var totalCarbAbsorption = 0.0
                for (meal in meals) {
                    val xStart = (meal.timestamp.ms - baseTimestamp).toDouble() / MS_PER_MINUTE
                    val timeSinceMeal = x - xStart
                    if (timeSinceMeal >= 0) {
                        val components = mealCurves[meal.mealType] ?: emptyList()
                        var mealAbsorptionRate = 0.0
                        for ((curve, weight) in components) {
                            mealAbsorptionRate += curve.normalizedActivity(timeSinceMeal) * weight
                        }
                        // Multiply by 60.0 for hourly rate (g/h), divide by 10.0 for KE/h
                        totalCarbAbsorption += (meal.carbGrams * mealAbsorptionRate * 60.0) / 10.0
                    }
                }
                carbY.add(totalCarbAbsorption)
            }

            return HistoryAndImpactDiagramData(
                baseTimestamp = baseTimestamp,
                minX = minX,
                maxX = maxX,
                bgXValues = validReadings.map { ((it.timestamp.ms - baseTimestamp).toDouble() / MS_PER_MINUTE * 10000).toLong() / 10000.0 },
                bgYValues = validReadings.map {
                    if (glucoseUnit == GlucoseUnit.MG_DL) it.value.mgdl.toDouble()
                    else it.value.mmol
                },
                glucoseUnit = glucoseUnit,
                insulinXValues = insulinX,
                insulinYValues = insulinY,
                carbXValues = carbX,
                carbYValues = carbY,
                dataSignature = "${validReadings.size}_${validReadings.first().timestamp.ms}_${validReadings.last().timestamp.ms}_${insulinApplications.size}_${meals.size}_$glucoseUnit"
            )
        }

        fun empty(glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL): HistoryAndImpactDiagramData {
            val now = Timestamp.now().ms
            val baseTimestamp = (now / MS_PER_HOUR) * MS_PER_HOUR
            val maxX = INITIAL_SHOW_HOURS * 60.0
            return HistoryAndImpactDiagramData(
                baseTimestamp = baseTimestamp,
                minX = 0.0,
                maxX = maxX,
                bgXValues = listOf(0.0, maxX),
                bgYValues = listOf(0.0, 0.0),
                glucoseUnit = glucoseUnit,
                insulinXValues = emptyList(),
                insulinYValues = emptyList(),
                carbXValues = emptyList(),
                carbYValues = emptyList(),
                dataSignature = "empty_${baseTimestamp}_$glucoseUnit"
            )
        }
    }
}

/**
 * State of the BgHistoryChart to observe and control the visible range.
 */
@Stable
class BgHistoryChartState(
    val scrollState: VicoScrollState,
    val zoomState: VicoZoomState
) {
    internal var minX by mutableDoubleStateOf(0.0)
    internal var maxX by mutableDoubleStateOf(0.0)
    internal var layerWidth by mutableFloatStateOf(0f)

    /**
     * The currently visible X-range in the chart (minutes offset from baseTimestamp).
     * Uses derivedStateOf to avoid state-write loops and unnecessary recompositions.
     */
    val visibleRange by derivedStateOf {
        val totalXRange = maxX - minX
        if (layerWidth <= 0 || totalXRange <= 0.0 || scrollState.maxValue < 0f) {
            null
        } else {
            // totalWidthPx is the virtual width of the entire chart
            val totalWidthPx = scrollState.maxValue + layerWidth
            // scrollState.value is the current horizontal scroll offset
            val startX = minX + (scrollState.value / totalWidthPx) * totalXRange
            val endX = startX + (layerWidth / totalWidthPx) * totalXRange
            startX..endX
        }
    }

    /**
     * Programmatically sets the visible range of the chart.
     */
    suspend fun setVisibleRange(start: Double, end: Double) {
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
    val scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)

    val initialZoom = remember(initialShowHours) { Zoom.x(initialShowHours * 60.0) }
    val minZoom = remember(initialShowHours) { Zoom.x(24.0 * 60) }
    val maxZoom = remember(initialShowHours) { Zoom.x(initialShowHours * 30) }

    val zoomState = rememberVicoZoomState(
        initialZoom = initialZoom,
        minZoom = minZoom,
        maxZoom = maxZoom
    )
    return remember(scrollState, zoomState) {
        BgHistoryChartState(scrollState, zoomState)
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
            // Layer 0 Model: BG
            lineModel {
                series(x = diagramData.bgXValues, y = diagramData.bgYValues)
            }

            // Layer 1 Model: Impact (Insulin & Carbs)
            lineModel {
                // Series 0: Insulin
                val insX = diagramData.insulinXValues.ifEmpty { listOf(0.0) }
                series(
                    x = insX,
                    y = if (showInsulin && diagramData.insulinXValues.isNotEmpty()) {
                        diagramData.insulinYValues
                    } else {
                        insX.map { 0.0 }
                    }
                )

                // Series 1: Carbs
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

    val rangeProvider = remember(diagramData.minX, diagramData.maxX, diagramData.glucoseUnit) {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 0.0
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                if (diagramData.glucoseUnit == GlucoseUnit.MG_DL) {
                    maxY.coerceAtLeast(200.0) + 20.0
                } else {
                    maxY.coerceAtLeast(11.1) + 1.1
                }
            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.minX
            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.maxX
        }
    }

    val sharedCalendar = remember { Calendar.getInstance() }

    val xAxisValueFormatter = remember(diagramData.baseTimestamp) {
        CartesianValueFormatter { _, x, _ ->
            synchronized(sharedCalendar) {
                sharedCalendar.timeInMillis = diagramData.baseTimestamp + x.toLong() * MS_PER_MINUTE
                String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    sharedCalendar.get(Calendar.HOUR_OF_DAY),
                    sharedCalendar.get(Calendar.MINUTE)
                )
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

    val bgAxisItemPlacer = remember(diagramData.glucoseUnit) {
        object : VerticalAxis.ItemPlacer {
            override fun getLabelValues(context: CartesianDrawingContext, axisHeight: Float, maxLabelHeight: Float, position: Axis.Position.Vertical) =
                getValues(context.ranges.getYRange(position).maxY)
            override fun getWidthMeasurementLabelValues(context: CartesianMeasuringContext, axisHeight: Float, maxLabelHeight: Float, position: Axis.Position.Vertical) =
                getValues(if (diagramData.glucoseUnit == GlucoseUnit.MG_DL) 300.0 else 16.7)
            override fun getHeightMeasurementLabelValues(context: CartesianMeasuringContext, position: Axis.Position.Vertical) =
                getValues(if (diagramData.glucoseUnit == GlucoseUnit.MG_DL) 300.0 else 16.7)
            private fun getValues(maxY: Double): List<Double> {
                val v = mutableListOf<Double>()
                if (diagramData.glucoseUnit == GlucoseUnit.MG_DL) {
                    var c = 0.0
                    while (c <= maxY) { v.add(c); c += 50.0 }
                } else {
                    var c = 0.0
                    while (c <= maxY) { v.add(c); c += 3.0 }
                }
                return v
            }
            override fun getTopLayerMargin(context: CartesianMeasuringContext, verticalLabelPosition: Position.Vertical, maxLabelHeight: Float, maxLineThickness: Float) = 0f
            override fun getBottomLayerMargin(context: CartesianMeasuringContext, verticalLabelPosition: Position.Vertical, maxLabelHeight: Float, maxLineThickness: Float) = 0f
        }
    }

    val impactAxisItemPlacer = remember {
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
                val step = when {
                    maxY <= 1.0 -> 0.2
                    maxY <= 2.0 -> 0.5
                    maxY <= 5.0 -> 1.0
                    maxY <= 10.0 -> 2.0
                    else -> 5.0
                }
                var c = 0.0
                while (c <= maxY + 0.0001) {
                    v.add(c)
                    c += step
                }
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

    val impactAxisValueFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            String.format(Locale.getDefault(), "%.2f", value)
        }
    }

    val ieLabel = stringResource(R.string.history_impact_ie_label)
    val keLabel = stringResource(R.string.history_impact_ke_label)
    val impactLabel = remember(showInsulin, showCarbs, ieLabel, keLabel) {
        listOfNotNull(
            if (showInsulin) "$ieLabel/h" else null,
            if (showCarbs) "$keLabel/h" else null
        ).joinToString(" | ")
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = glucoseUnitLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showInsulin || showCarbs) {
                Text(
                    text = impactLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(bgLine),
                    rangeProvider = rangeProvider,
                    verticalAxisPosition = Axis.Position.Vertical.Start
                ),
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(insulinLine, carbLine),
                    rangeProvider = remember(diagramData.minX, diagramData.maxX) {
                        object : CartesianLayerRangeProvider {
                            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 0.0
                            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = maxY.coerceAtLeast(2.0) * 1.1
                            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.minX
                            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = diagramData.maxX
                        }
                    },
                    verticalAxisPosition = Axis.Position.Vertical.End
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberAxisLabelComponent(style = TextStyle(color = ExtendedTheme.semanticColors.highContrast)),
                    itemPlacer = bgAxisItemPlacer,
                    horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                    line = null
                ),
                endAxis = if (showInsulin || showCarbs) {
                    VerticalAxis.rememberEnd(
                        label = rememberAxisLabelComponent(style = TextStyle(color = ExtendedTheme.semanticColors.highContrast)),
                        itemPlacer = impactAxisItemPlacer,
                        valueFormatter = impactAxisValueFormatter,
                        horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                        line = null,
                        guideline = null
                    )
                } else null,
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberAxisLabelComponent(style = TextStyle(color = ExtendedTheme.semanticColors.highContrast)),
                    valueFormatter = xAxisValueFormatter,
                    itemPlacer = xItemPlacer
                ),
            ),
            modelProducer = modelProducer,
            scrollState = state.scrollState,
            zoomState = state.zoomState,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = onChartClick != null,
                    onClick = { onChartClick?.invoke() }
                ),
        )
    }
}

fun createSampleImpactDiagramData(): HistoryAndImpactDiagramData {
    val readings = createSampleReadings(120, 5)
    val baseTs = readings.first().timestamp.ms

    val sampleMealType = MealType(
        id = "1",
        name = "Normal",
        components = listOf(CarbCurveComponentData(100, Minutes(90))),
        cat = Minutes(180)
    )

    val insulinApplications = listOf(
        InsulinApplication(0, Timestamp(baseTs + 30 * MS_PER_MINUTE), InsulinAmount(5.0), InsulinType("1", "Rapid", Minutes(60.toShort()), Minutes(300.toShort())), InsulinOrigin.Manual, meal = true),
        InsulinApplication(0, Timestamp(baseTs + 280 * MS_PER_MINUTE), InsulinAmount(8.0), InsulinType("1", "Rapid", Minutes(60.toShort()), Minutes(300.toShort())), InsulinOrigin.Manual, meal = true),
        InsulinApplication(0, Timestamp(baseTs + 450 * MS_PER_MINUTE), InsulinAmount(4.0), InsulinType("1", "Rapid", Minutes(60.toShort()), Minutes(300.toShort())), InsulinOrigin.Manual, meal = true)
    )

    val meals = listOf(
        MealEntry(0, Timestamp(baseTs + 20 * MS_PER_MINUTE), 50.0, sampleMealType),
        MealEntry(0, Timestamp(baseTs + 300 * MS_PER_MINUTE), 80.0, sampleMealType),
        MealEntry(0, Timestamp(baseTs + 460 * MS_PER_MINUTE), 40.0, sampleMealType)
    )

    return HistoryAndImpactDiagramData.create(
        readings = readings,
        glucoseUnit = GlucoseUnit.MG_DL,
        insulinApplications = insulinApplications,
        meals = meals,
        dia = Minutes(300),
        peak = Minutes(60)
    )!!.let { it.copy(dataSignature = "impact_${it.dataSignature}") }
}

@Composable
fun HistoryAndImpactChartOrDefault(
    diagramData: HistoryAndImpactDiagramData?,
    modifier: Modifier = Modifier,
    onChartClick: (() -> Unit)? = null,
    state: BgHistoryChartState? = null,
    showInsulin: Boolean = true,
    showCarbs: Boolean = true
) {
    val data = diagramData ?: remember { HistoryAndImpactDiagramData.empty() }
    HistoryAndImpactChart(
        diagramData = data,
        modifier = modifier,
        onChartClick = onChartClick,
        controlledState = state,
        showInsulin = showInsulin,
        showCarbs = showCarbs
    )
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HistoryAndImpactChartPreview() {
    val diagramData = remember { createSampleImpactDiagramData() }
    CompositionLocalProvider(LocalGlucoseUnit provides GlucoseUnit.MG_DL) {
        AppTheme {
            HistoryAndImpactChart(
                diagramData = diagramData,
                modifier = Modifier.height(300.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryAndImpactChartDefaultPreview() {
    CompositionLocalProvider(LocalGlucoseUnit provides GlucoseUnit.MG_DL) {
        AppTheme {
            HistoryAndImpactChartOrDefault(
                diagramData = null,
                modifier = Modifier.height(300.dp)
            )
        }
    }
}