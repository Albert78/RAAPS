package de.dh.raaps.ui.common.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.common.icons.Icon_Minus
import de.dh.raaps.ui.common.icons.Icon_Plus
import de.dh.raaps.ui.common.relativeTimeMinutes

@Composable
fun TimeStepper(
    currentTime: Timestamp,
    onTimeChange: (Timestamp) -> Unit,
    modifier: Modifier = Modifier,
    stepMinutes: Int = 5,
    style: StepperStyle = StepperDefaults.defaultStyle()
) {
    val now = Timestamp.now()
    val diffMin = kotlin.math.round((currentTime.ms - now.ms) / 60000.0).toInt()
    val relativeText = relativeTimeMinutes(diffMin)

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
            text = relativeText,
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