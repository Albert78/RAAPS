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

public val Icons.Filled.InsulinBlood: ImageVector
    get() {
        if (_insulinBlood != null) {
            return _insulinBlood!!
        }
        _insulinBlood = ImageVector.Builder(
            name = "Filled.InsulinBlood",
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
                // Syringe Part from Vaccines icon
                moveTo(11.0f, 5.5f)
                horizontalLineTo(8.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(0.5f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-3.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(3.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                verticalLineTo(15.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(4.0f)
                lineToRelative(2.0f, 1.5f)
                verticalLineTo(17.0f)
                horizontalLineToRelative(1.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(7.5f)
                horizontalLineToRelative(1.0f)
                verticalLineTo(5.5f)
                close()
                moveTo(9.0f, 15.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(7.5f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(15.0f)
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
                // Drop Part (overlapping)
                moveTo(12.0f, 2.0f)
                curveToRelative(-5.33f, 4.55f, -8.0f, 8.48f, -8.0f, 11.8f)
                curveToRelative(0.0f, 4.98f, 3.8f, 8.2f, 8.0f, 8.2f)
                reflectiveCurveToRelative(8.0f, -3.22f, 8.0f, -8.2f)
                curveTo(20.0f, 10.48f, 17.33f, 6.55f, 12.0f, 2.0f)
                close()
            }
        }.build()
        return _insulinBlood!!
    }

private var _insulinBlood: ImageVector? = null

@Preview(showBackground = true)
@Composable
private fun InsulinBloodIconPreview() {
    AppTheme {
        Surface {
            Icon(
                imageVector = Icons.Filled.InsulinBlood,
                contentDescription = "Insulin Blood Icon Preview",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}