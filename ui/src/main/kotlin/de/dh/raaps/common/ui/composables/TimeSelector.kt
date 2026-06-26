package de.dh.raaps.common.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.util.Locale

/**
 * A custom time selector that displays the hour and opens a scrollable picker dialog.
 * Uses the generic WheelPicker as a base.
 */
@Composable
fun TimeHourSelector(
    hour: Int,
    enabled: Boolean,
    minHour: Int,
    maxHour: Int,
    onHourChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hours = remember(minHour, maxHour) { (minHour..maxHour).toList() }

    WheelPicker(
        value = hour,
        options = hours,
        onValueSelected = onHourChanged,
        labelProvider = { h -> String.format(Locale.getDefault(), "%02d:00", h) },
        enabled = enabled,
        modifier = modifier
    )
}