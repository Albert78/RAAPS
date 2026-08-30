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
import de.dh.raaps.ui.R
import androidx.compose.material.icons.filled.Warning
import de.dh.raaps.core.aps.CoreState
import de.dh.raaps.ui.common.icons.CarbsBlood
import de.dh.raaps.ui.common.icons.InsulinBlood
import de.dh.raaps.ui.common.composables.AppColorBlue
import de.dh.raaps.ui.common.composables.LightGreenA700
import de.dh.raaps.ui.common.composables.Red
import de.dh.raaps.ui.common.composables.Yellow
import de.dh.raaps.ui.common.shortRelativeTimeAgo
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.common.glucoseValue
import de.dh.raaps.ui.common.deltaValue
import de.dh.raaps.ui.common.glucoseUnitLabel
import de.dh.raaps.ui.common.insulinValue
import de.dh.raaps.ui.common.carbsGramsValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CurrentStateView(
    currentBgUiState: CurrentBgUiState,
    iob: InsulinAmount,
    cob: Double,
    modifier: Modifier = Modifier,
    carbsVisible: Boolean = true,
    onCarbsToggle: (Boolean) -> Unit = {},
    insulinVisible: Boolean = true,
    onInsulinToggle: (Boolean) -> Unit = {},
    onSystemClick: () -> Unit = {}
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
                delay((next10sBoundary - currentDiff).milliseconds)
            }
        }
    }

    val timeAgoText = if (diffMs != null) shortRelativeTimeAgo(diffMs!!) else ""

    val bgText = glucoseValue(currentBgValue?.bgValue, default = "?")

    val deltaText = deltaValue(currentBgValue?.delta, default = "")

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

    val cobText = carbsGramsValue(cob)
    val iobText = insulinValue(iob.iu)

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
                        text = glucoseUnitLabel(),
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
                    verticalAlignment = Alignment.Top
                ) {
                    // Carbs Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCarbsToggle(!carbsVisible) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CarbsBlood,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (carbsVisible) Color(0xFFFFC107) else Color.Gray.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.current_state_carbs_label_short),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = cobText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (carbsVisible) {
                            HorizontalDivider(
                                modifier = Modifier.width(16.dp).padding(top = 2.dp),
                                thickness = 2.dp,
                                color = Color(0xFFFFC107)
                            )
                        }
                    }

                    // Insulin Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onInsulinToggle(!insulinVisible) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.InsulinBlood,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (insulinVisible) Color(0xFFF44336) else Color.Gray.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.current_state_insulin_label_short),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = iobText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (insulinVisible) {
                            HorizontalDivider(
                                modifier = Modifier.width(16.dp).padding(top = 2.dp),
                                thickness = 2.dp,
                                color = Color(0xFFF44336)
                            )
                        }
                    }

                    // System Column
                    val coreState = currentBgUiState.coreState
                    val (statusColor, hasIssues) = when (coreState) {
                        is CoreState.Active -> {
                            if (currentBgUiState.apsIssues.isEmpty()) {
                                LightGreenA700 to false
                            } else {
                                Red to true
                            }
                        }
                        else -> Color.Gray to false
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSystemClick() }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = statusColor
                                ) {}
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.current_state_algorithm_label_short),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        val labelRes = if (coreState is CoreState.Active) R.string.label_active else R.string.label_inactive
                        if (hasIssues) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).padding(vertical = 2.dp),
                                tint = Red
                            )
                        } else {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
            bgValue = BgValue.fromMgDl(125),
            delta = BgDelta.fromMgDl(+15),
            trend = BgTrend.FortyFiveUp,
            timestamp = Timestamp.now(),
        ),
        coreState = CoreState.Active(issues = emptySet()),
        apsIssues = emptySet()
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