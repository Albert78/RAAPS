package de.dh.raaps.common.ui.composables

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.icons.Icon_Scrollview_Arrow_Down
import de.dh.raaps.common.ui.icons.Icon_Scrollview_Arrow_Up

/**
 * Modifier showing arrow up and down overlays over the scrollable content
 * if the child is scrollable in the corresponding direction.
 */
@Composable
fun Modifier.contentScrollIndicator(
    scrollableState: ScrollableState,
    indicatorHeight: Dp = 32.dp,
    iconSize: Dp = 64.dp,
    iconPadding: Dp = 16.dp,
    surfaceAlpha: Float = 0.8f
): Modifier {
    val surfaceColor =
        MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)
    val iconTint =
        MaterialTheme.colorScheme.onSurfaceVariant

    val upPainter = rememberVectorPainter(image = Icon_Scrollview_Arrow_Up)
    val downPainter = rememberVectorPainter(image = Icon_Scrollview_Arrow_Down)

    return this.then(
        Modifier.scrollIndicatorDrawModifier(
            scrollableState = scrollableState,
            surfaceColor = surfaceColor,
            iconTint = iconTint,
            upPainter = upPainter,
            downPainter = downPainter,
            indicatorHeight = indicatorHeight,
            iconSize = iconSize,
            iconPadding = iconPadding
        )
    )
}

private fun Modifier.scrollIndicatorDrawModifier(
    scrollableState: ScrollableState,
    surfaceColor: Color,
    iconTint: Color,
    upPainter: Painter,
    downPainter: Painter,
    indicatorHeight: Dp,
    iconSize: Dp,
    iconPadding: Dp
): Modifier = this.drawWithContent {
    drawContent()

    val canScrollUp = scrollableState.canScrollBackward
    val canScrollDown = scrollableState.canScrollForward

    val indicatorHeightPx = indicatorHeight.toPx()
    val iconSizePx = iconSize.toPx()
    val iconPaddingPx = iconPadding.toPx()

    if (canScrollUp) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(surfaceColor, Color.Transparent),
                startY = 0f,
                endY = indicatorHeightPx
            ),
            size = Size(size.width, indicatorHeightPx)
        )

        with(upPainter) {
            translate(
                left = (this@drawWithContent.size.width - iconSizePx) / 2f,
                top = iconPaddingPx
            ) {
                draw(
                    size = Size(iconSizePx, iconSizePx),
                    colorFilter = ColorFilter.tint(iconTint)
                )
            }
        }
    }

    if (canScrollDown) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, surfaceColor),
                startY = size.height - indicatorHeightPx,
                endY = size.height
            ),
            topLeft = Offset(0f, size.height - indicatorHeightPx),
            size = Size(size.width, indicatorHeightPx)
        )

        with(downPainter) {
            translate(
                left = (this@drawWithContent.size.width - iconSizePx) / 2f,
                top = size.height - iconSizePx - iconPaddingPx
            ) {
                draw(
                    size = Size(iconSizePx, iconSizePx),
                    colorFilter = ColorFilter.tint(iconTint)
                )
            }
        }
    }
}