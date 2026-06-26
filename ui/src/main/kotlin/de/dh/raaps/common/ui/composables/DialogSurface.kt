package de.dh.raaps.common.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun DialogSurface(content: @Composable (() -> Unit)) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
        content = content
    )
}