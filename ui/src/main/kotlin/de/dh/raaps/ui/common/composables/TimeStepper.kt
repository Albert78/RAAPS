package de.dh.raaps.ui.common.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.common.icons.Icon_Minus
import de.dh.raaps.ui.common.icons.Icon_Plus
import de.dh.raaps.ui.common.relativeTimeMinutes
import de.dh.raaps.ui.common.theme.AppTheme
import kotlin.math.round
import de.dh.raaps.common.R as CommonR

@Composable
fun TimeStepper(
    currentTime: Timestamp,
    onTimeChange: (Timestamp) -> Unit,
    modifier: Modifier = Modifier,
    stepMinutes: Int = 5,
    baseTime: Timestamp = Timestamp.now(),
    showPreposition: Boolean = true,
    forceSign: Boolean = false,
    style: StepperStyle = TimeStepperDefaults.defaultStyle()
) {
    val diffMin = round((currentTime.ms - baseTime.ms) / 60000.0).toInt()

    val displayText = if (showPreposition) {
        relativeTimeMinutes(diffMin)
    } else {
        if (diffMin == 0) {
            stringResource(CommonR.string.duration_minutes_zero_format)
        } else if (forceSign) {
            stringResource(CommonR.string.duration_minutes_signed_format, diffMin)
        } else {
            stringResource(CommonR.string.duration_minutes_format, diffMin)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = { onTimeChange(currentTime - Minutes(stepMinutes.toShort())) },
            modifier = Modifier.size(style.buttonSize)
        ) {
            Icon(Icon_Minus, contentDescription = null, modifier = Modifier.size(style.buttonSize * 0.5f))
        }

        Spacer(Modifier.width(style.spacing))

        Text(
            text = displayText,
            style = style.textStyle,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(style.valueWidth)
        )

        Spacer(Modifier.width(style.spacing))

        IconButton(
            onClick = { onTimeChange(currentTime + Minutes(stepMinutes.toShort())) },
            modifier = Modifier.size(style.buttonSize)
        ) {
            Icon(Icon_Plus, contentDescription = null, modifier = Modifier.size(style.buttonSize * 0.5f))
        }
    }
}

object TimeStepperDefaults {
    @Composable
    fun defaultStyle() = StepperDefaults.defaultStyle().copy(
        spacing = 4.dp,
        valueWidth = 144.dp
    )

    @Composable
    fun mediumStyle() = StepperDefaults.mediumStyle().copy(
        spacing = 4.dp,
        valueWidth = 130.dp
    )

    @Composable
    fun smallStyle() = StepperDefaults.smallStyle().copy(
        spacing = 2.dp,
        valueWidth = 114.dp
    )

    @Composable
    fun compactStyle() = StepperDefaults.compactStyle().copy(
        spacing = 2.dp,
        valueWidth = 84.dp
    )
}

@Preview(showBackground = true)
@Composable
fun TimeStepperPreview() {
    val now = Timestamp.now()
    var timeNow by remember { mutableStateOf(now) }
    var timePast by remember { mutableStateOf(Timestamp(now.ms - 90 * 60 * 1000)) }
    var timeFuture by remember { mutableStateOf(Timestamp(now.ms + 90 * 60 * 1000)) }

    AppTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Default Style", style = MaterialTheme.typography.labelSmall)
                    TimeStepper(
                        currentTime = timeNow,
                        onTimeChange = { timeNow = it }
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Small Style (Past)", style = MaterialTheme.typography.labelSmall)
                    TimeStepper(
                        currentTime = timePast,
                        onTimeChange = { timePast = it },
                        style = TimeStepperDefaults.smallStyle()
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Medium Style (Future)", style = MaterialTheme.typography.labelSmall)
                    TimeStepper(
                        currentTime = timeFuture,
                        onTimeChange = { timeFuture = it },
                        style = TimeStepperDefaults.mediumStyle()
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Compact Style", style = MaterialTheme.typography.labelSmall)
                    TimeStepper(
                        currentTime = timeNow,
                        onTimeChange = { timeNow = it },
                        style = TimeStepperDefaults.compactStyle()
                    )
                }
            }
        }
    }
}