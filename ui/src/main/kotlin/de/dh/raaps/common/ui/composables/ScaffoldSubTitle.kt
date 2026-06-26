package de.dh.raaps.common.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScaffoldSubTitle(
    scrollBehavior: TopAppBarScrollBehavior,
    text: String?,
    modifier: Modifier = Modifier
) {
    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val visibilityFactor = 1f - collapsedFraction.coerceIn(0f, 1f)

    // Comments
    Column(
        modifier = modifier
            .padding(start = 8.dp, end = 8.dp)
            .graphicsLayer {
                alpha = visibilityFactor
            }
    ) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        HorizontalDivider()
    }
}