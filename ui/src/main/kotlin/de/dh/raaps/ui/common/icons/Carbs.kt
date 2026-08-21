package de.dh.raaps.ui.common.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.ui.common.theme.AppTheme

public val Icons.Filled.Carbs: ImageVector
    get() {
        if (_carbs != null) {
            return _carbs!!
        }
        _carbs = ImageVector.Builder(
            name = "Filled.Carbs",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).group(
            translationX = 4f
        ) {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Fork from Restaurant icon (representing Carbs/Food)
                moveTo(11.0f, 9.0f)
                lineTo(9.0f, 9.0f)
                lineTo(9.0f, 2.0f)
                lineTo(7.0f, 2.0f)
                verticalLineToRelative(7.0f)
                lineTo(5.0f, 9.0f)
                lineTo(5.0f, 2.0f)
                lineTo(3.0f, 2.0f)
                verticalLineToRelative(7.0f)
                curveToRelative(0.0f, 2.12f, 1.66f, 3.84f, 3.75f, 3.97f)
                lineTo(6.75f, 22.0f)
                horizontalLineToRelative(2.5f)
                verticalLineToRelative(-9.03f)
                curveTo(11.34f, 12.84f, 13.0f, 11.12f, 13.0f, 9.0f)
                lineTo(13.0f, 2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.0f)
                close()
            }
        }.build()
        return _carbs!!
    }

private var _carbs: ImageVector? = null

@Preview(showBackground = true)
@Composable
private fun CarbsIconPreview() {
    AppTheme {
        Surface {
            Icon(
                imageVector = Icons.Filled.Carbs,
                contentDescription = "Carbs Icon Preview",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}