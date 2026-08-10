package de.dh.raaps.ui.controls.state

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.ui.composables.AppColorBlue
import de.dh.raaps.common.ui.composables.LightGreenA700
import de.dh.raaps.common.ui.composables.Red
import de.dh.raaps.common.ui.composables.Yellow
import de.dh.raaps.common.ui.shortRelativeTimeAgo
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.history.BgTrend
import de.dh.raaps.ui.controls.history.CurrentBgData
import de.dh.raaps.ui.controls.history.CurrentBgUiState
import kotlinx.coroutines.delay

@Composable
fun CurrentStateView(
    currentBgUiState: CurrentBgUiState,
    iob: InsulinAmount,
    cob: Double,
    modifier: Modifier = Modifier,
    carbsVisible: Boolean = true,
    onCarbsToggle: (Boolean) -> Unit = {},
    insulinVisible: Boolean = true,
    onInsulinToggle: (Boolean) -> Unit = {}
) {
    val currentBgValue = currentBgUiState.currentBgValue
    val timestamp = currentBgValue?.timestamp

    var diffMs by remember(timestamp) {
        mutableStateOf(timestamp?.let { System.currentTimeMillis() - it.ms })
    }
    LaunchedEffect(timestamp) {
        timestamp?.let { ts ->
            while (true) {
                val now = System.currentTimeMillis()
                val currentDiff = now - ts.ms
                diffMs = currentDiff
                val next10sBoundary = ((currentDiff / 10000) + 1) * 10000
                delay(next10sBoundary - currentDiff)
            }
        }
    }

    val timeAgoText = if (diffMs != null) shortRelativeTimeAgo(diffMs!!) else ""

    val bgText = when (val bg = currentBgValue?.bgValue) {
        null -> "?"
        else -> bg.toString(currentBgValue.glucoseUnit)
    }

    val deltaText = currentBgValue?.delta?.toDiff(currentBgValue.glucoseUnit) ?: ""

    val textColor = if (currentBgValue == null || (currentBgValue.isValueOld)) {
        Color.Gray
    } else when {
        currentBgValue.bgValue.mgdl < 70 -> Red
        currentBgValue.bgValue.mgdl < 180 -> LightGreenA700
        else -> Yellow
    }

    val trendRotation = when (currentBgValue?.trend) {
        BgTrend.DoubleUp -> -90f
        BgTrend.SingleUp -> -90f
        BgTrend.FortyFiveUp -> -45f
        BgTrend.Flat -> 0f
        BgTrend.FortyFiveDown -> 45f
        BgTrend.SingleDown -> 90f
        BgTrend.DoubleDown -> 90f
        else -> 0f
    }

    val cobFormat = stringResource(R.string.cob_format)
    val iobFormat = stringResource(R.string.iob_format)
    val cobText = remember(cob, cobFormat) { cobFormat.format(cob) }
    val iobText = remember(iob, iobFormat) { iobFormat.format(iob.iu) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(2.dp, AppColorBlue.copy(alpha = 0.3f))
    ) {
        Box {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = bgText,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                    Text(
                        text = stringResource(R.string.glucose_unit_mgdl),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )
                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (currentBgValue?.trend != null && currentBgValue.trend != BgTrend.NotComputable) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(trendRotation),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (timeAgoText.isNotEmpty()) {
                    Text(
                        text = timeAgoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 1.dp,
                    color = Color.Gray.copy(alpha = 0.3f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.approx_prefix),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = cobText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.active_carbs_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Surface(
                            modifier = Modifier.size(18.dp).clickable { onCarbsToggle(!carbsVisible) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (carbsVisible) Color(0xFFFFC107) else Color.Gray.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                        ) {}
                    }

                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = Color.Gray.copy(alpha = 0.3f)
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.approx_prefix),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = iobText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.active_insulin_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Surface(
                            modifier = Modifier.size(18.dp).clickable { onInsulinToggle(!insulinVisible) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (insulinVisible) Color(0xFFF44336) else Color.Gray.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                        ) {}
                    }
                }
            }

            NextReadingIcon(
                diffMs = diffMs,
                readingsTimeDelayMs = currentBgUiState.readingsTimeDelay.inMs(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun NextReadingIcon(
    diffMs: Long?,
    readingsTimeDelayMs: Long,
    modifier: Modifier = Modifier
) {
    if (diffMs == null) return

    val isHourglass = diffMs > readingsTimeDelayMs + 20000

    val tintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    if (isHourglass) {
        Icon(
            imageVector = Icons.Default.HourglassEmpty,
            contentDescription = null,
            modifier = modifier.size(16.dp),
            tint = tintColor
        )
    } else {
        val progress = (diffMs.toFloat() / readingsTimeDelayMs.toFloat()).coerceIn(0f, 1f)
        Canvas(modifier = modifier.size(16.dp)) {
            // Background circle (subtle)
            drawCircle(color = backgroundColor)
            // Progress arc (remaining time, prominent, shrinking clockwise)
            drawArc(
                color = tintColor,
                startAngle = -90f + (progress * 360f),
                sweepAngle = (1f - progress) * 360f,
                useCenter = true
            )
            // Outline
            drawCircle(color = borderColor, style = Stroke(width = 1.dp.toPx()))
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CurrentStateViewPreview() {
    val state = CurrentBgUiState(
        isLoading = false,
        isError = false,
        currentBgValue = CurrentBgData.valid(
            bgValue = BgValue(125),
            delta = BgDelta(+15),
            trend = BgTrend.FortyFiveUp,
            timestamp = Timestamp.now(),
        )
    )
    AppTheme {
        Surface {
            Box(Modifier.padding(16.dp)) {
                CurrentStateView(
                    currentBgUiState = state,
                    iob = InsulinAmount(1.57),
                    cob = 12.0
                )
            }
        }
    }
}
