package de.dh.raaps.ui.controls.state

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.common.composables.AppColorBlue
import de.dh.raaps.ui.common.composables.LightGreenA700
import de.dh.raaps.ui.common.composables.Red
import de.dh.raaps.ui.common.composables.Yellow
import de.dh.raaps.ui.common.shortRelativeTimeAgo
import de.dh.raaps.ui.common.theme.AppTheme
import de.dh.raaps.ui.common.theme.ExtendedTheme
import de.dh.raaps.ui.common.glucoseValue
import de.dh.raaps.ui.common.deltaValue
import de.dh.raaps.ui.controls.history.BgTrend
import de.dh.raaps.ui.controls.history.CurrentBgData
import de.dh.raaps.ui.controls.history.CurrentBgUiState
import kotlinx.coroutines.delay

@Composable
fun CurrentBgViewSquare(
    currentBgUiState: CurrentBgUiState,
    modifier: Modifier = Modifier
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

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(6.dp, AppColorBlue.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = bgText,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )

                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )

                if (currentBgValue?.trend != null && currentBgValue.trend != BgTrend.NotComputable) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(trendRotation),
                        tint = ExtendedTheme.semanticColors.highContrast
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
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CurrentBgViewSquarePreview() {
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
                CurrentBgViewSquare(currentBgUiState = state)
            }
        }
    }
}
