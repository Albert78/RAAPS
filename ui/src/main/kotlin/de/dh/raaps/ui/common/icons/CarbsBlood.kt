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

public val Icons.Filled.CarbsBlood: ImageVector
    get() {
        if (_carbsBlood != null) {
            return _carbsBlood!!
        }
        _carbsBlood = ImageVector.Builder(
            name = "Filled.CarbsBlood",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).group(
            translationX = -2f
        ) {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Fork Part from Restaurant icon
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
        }.group(
            translationX = 3f,
            translationY = 3f,
            pivotX = 12f,
            pivotY = 12f,
            scaleX = 0.8f,
            scaleY = 0.8f
        ) {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Drop Part (based on WaterDrop)
                moveTo(12.0f, 2.0f)
                curveToRelative(-5.33f, 4.55f, -8.0f, 8.48f, -8.0f, 11.8f)
                curveToRelative(0.0f, 4.98f, 3.8f, 8.2f, 8.0f, 8.2f)
                reflectiveCurveToRelative(8.0f, -3.22f, 8.0f, -8.2f)
                curveTo(20.0f, 10.48f, 17.33f, 6.55f, 12.0f, 2.0f)
                close()
            }
        }.build()
        return _carbsBlood!!
    }

private var _carbsBlood: ImageVector? = null

@Preview(showBackground = true)
@Composable
private fun CarbsBloodIconPreview() {
    AppTheme {
        Surface {
            Icon(
                imageVector = Icons.Filled.CarbsBlood,
                contentDescription = "Carbs Blood Icon Preview",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}