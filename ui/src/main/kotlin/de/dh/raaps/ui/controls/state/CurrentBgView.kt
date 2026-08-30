package de.dh.raaps.ui.controls.state

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.common.composables.AppColorBlue
import de.dh.raaps.ui.common.composables.LightGreenA700
import de.dh.raaps.ui.common.composables.Red
import de.dh.raaps.ui.common.composables.Yellow
import de.dh.raaps.ui.common.deltaValue
import de.dh.raaps.ui.common.glucoseValue
import de.dh.raaps.ui.common.shortRelativeTimeAgo
import de.dh.raaps.ui.common.theme.AppTheme
import kotlinx.coroutines.delay

@Composable
fun CurrentBgView(
    centerText: String,
    textColor: Color,
    textBold: Boolean,
    deltaText: String?,
    timestamp: Timestamp?,
    trendAngle: Float?,
    modifier: Modifier = Modifier
) {
    // diffMs is null if timestamp is null
    var diffMs by remember(timestamp) {
        mutableStateOf(timestamp?.let { System.currentTimeMillis() - it.ms })
    }
    LaunchedEffect(timestamp) {
        timestamp?.let { ts ->
            while (true) {
                val now = System.currentTimeMillis()
                val currentDiff = now - ts.ms
                diffMs = currentDiff

                // Wait until the next 10s boundary relative to the BG timestamp
                val next10sBoundary = ((currentDiff / 10000) + 1) * 10000
                delay(next10sBoundary - currentDiff)
            }
        }
    }

    Box(
        modifier = modifier
            .size(150.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val mainRadius = size.width * 0.4f
            val strokeWidth = 6.dp.toPx()

            // Background: Pale Blue Ring
            drawCircle(
                color = AppColorBlue.copy(alpha = 0.3f),
                radius = mainRadius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // Trend indicator
            if (trendAngle != null) {
                // Highlight Arc around the trend
                drawArc(
                    color = AppColorBlue,
                    startAngle = trendAngle - 20f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = Offset(center.x - mainRadius, center.y - mainRadius),
                    size = Size(mainRadius * 2, mainRadius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Trend Arrow
                rotate(degrees = trendAngle, pivot = center) {
                    val arrowPath = Path().apply {
                        val xBase = center.x + mainRadius
                        val yBase = center.y
                        val length = 6.dp.toPx()
                        val width = 15.dp.toPx()

                        moveTo(xBase, yBase - width / 2)
                        lineTo(xBase + length, yBase)
                        lineTo(xBase, yBase + width / 2)
                    }
                    drawPath(
                        path = arrowPath,
                        color = AppColorBlue,
                        style = Stroke(
                            width = 8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Column(
            modifier = Modifier.offset(y = (-6).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-6).dp)
        ) {
            Text(
                text = deltaText ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                text = centerText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 48.sp,
                    fontWeight = if (textBold) FontWeight.Bold else FontWeight.Light,
                    lineHeight = 48.sp
                ),
                color = textColor
            )
            val timeAgoText = if (diffMs != null) shortRelativeTimeAgo(diffMs!!) else ""
            Text(
                text = timeAgoText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun CurrentBgView(
    currentBgUiState: CurrentBgUiState,
    modifier: Modifier = Modifier
) {
    val currentBgValue = currentBgUiState.currentBgValue

    val centerText = glucoseValue(currentBgValue?.bgValue, default = "?")

    val textColor =
        if (currentBgValue == null) {
            Color.DarkGray
        } else if (currentBgValue.isValueOld) {
            Color.DarkGray
        } else when {
            currentBgValue.bgValue.mgdl < 50 -> Red
            currentBgValue.bgValue.mgdl < 70 -> Yellow
            currentBgValue.bgValue.mgdl < 180 -> LightGreenA700
            currentBgValue.bgValue.mgdl < 250 -> Yellow
            else -> Red
        }

    val textLight = currentBgValue?.isValueOld ?: false

    val deltaText = deltaValue(currentBgValue?.delta, default = "")

    val trendAngle =
        when (currentBgValue?.trend) {
            null -> null
            BgTrend.DoubleUp -> -60f
            BgTrend.SingleUp -> -45f
            BgTrend.FortyFiveUp -> -25f
            BgTrend.Flat -> 0f
            BgTrend.FortyFiveDown -> 25f
            BgTrend.SingleDown -> 45f
            BgTrend.DoubleDown -> 60f
            BgTrend.NotComputable -> 0f
        }

    CurrentBgView(
        centerText,
        textColor,
        !textLight,
        deltaText,
        currentBgValue?.timestamp,
        trendAngle,
        modifier
    )
}

fun createSampleGoodBgUiState(): CurrentBgUiState {
    return CurrentBgUiState(
        isLoading = false,
        isError = false,
        CurrentBgData(
            bgValue = BgValue.fromMgDl(125),
            delta = BgDelta.fromMgDl(+10),
            trend = BgTrend.FortyFiveUp,
            timestamp = Timestamp.now().minusMinutes(90)
        )
    )
}

@Preview(showBackground = true)
@Composable
fun BgInvalidViewPreview() {
    AppTheme {
        CurrentBgView(
            currentBgUiState = CurrentBgUiState(
                isLoading = false,
                isError = false,
                currentBgValue = CurrentBgData.invalid()
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BgOldViewPreview() {
    AppTheme {
        CurrentBgView(
            currentBgUiState = CurrentBgUiState(
                isLoading = false,
                isError = false,
                currentBgValue = CurrentBgData.oldValue(
                    bgValue = BgValue.fromMgDl(110),
                    timestamp = Timestamp.now().minusHours(3)
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BgVeryHighViewPreview() {
    AppTheme {
        CurrentBgView(
            currentBgUiState = CurrentBgUiState(
                isLoading = false,
                isError = false,
                currentBgValue = CurrentBgData.valid(
                    bgValue = BgValue.fromMgDl(325),
                    delta = BgDelta.fromMgDl(+20),
                    trend = BgTrend.DoubleUp,
                    timestamp = Timestamp.now().minusMinutes(3)
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BgHighViewPreview() {
    AppTheme {
        CurrentBgView(
            currentBgUiState = CurrentBgUiState(
                isLoading = false,
                isError = false,
                currentBgValue = CurrentBgData.valid(
                    bgValue = BgValue.fromMgDl(225),
                    delta = BgDelta.fromMgDl(+15),
                    trend = BgTrend.SingleUp,
                    timestamp = Timestamp.now().minusSeconds(20)
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BgGoodUpViewPreview() {
    AppTheme {
        CurrentBgView(
            currentBgUiState = createSampleGoodBgUiState()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BgGoodFlatViewPreview() {
    AppTheme {
        CurrentBgView(
            currentBgUiState = CurrentBgUiState(
                isLoading = false,
                isError = false,
                currentBgValue = CurrentBgData.valid(
                    bgValue = BgValue.fromMgDl(125),
                    delta = BgDelta.fromMgDl(+0),
                    trend = BgTrend.Flat,
                    timestamp = Timestamp.now(),
                    glucoseUnit = GlucoseUnit.MG_DL
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BgLowViewPreview() {
    AppTheme {
        CurrentBgView(
            currentBgUiState = CurrentBgUiState(
                isLoading = false,
                isError = false,
                currentBgValue = CurrentBgData.valid(
                    bgValue = BgValue.fromMgDl(60),
                    delta = BgDelta.fromMgDl(-5),
                    trend = BgTrend.FortyFiveDown,
                    timestamp = Timestamp.now(),
                    glucoseUnit = GlucoseUnit.MG_DL
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BgVeryLowViewPreview() {
    AppTheme {
        CurrentBgView(
            currentBgUiState = CurrentBgUiState(
                isLoading = false,
                isError = false,
                currentBgValue = CurrentBgData.valid(
                    bgValue = BgValue.fromMgDl(45),
                    delta = BgDelta.fromMgDl(-10),
                    trend = BgTrend.SingleDown,
                    timestamp = Timestamp.now(),
                    glucoseUnit = GlucoseUnit.MG_DL
                )
            )
        )
    }
}