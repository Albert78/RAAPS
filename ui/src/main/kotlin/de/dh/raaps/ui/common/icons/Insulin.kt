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

public val Icons.Filled.Insulin: ImageVector
    get() {
        if (_insulin != null) {
            return _insulin!!
        }
        _insulin = ImageVector.Builder(
            name = "Filled.Insulin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).group(
            translationX = 5.5f
        ) {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Syringe from Vaccines icon (representing Insulin)
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
        }.build()
        return _insulin!!
    }

private var _insulin: ImageVector? = null

@Preview(showBackground = true)
@Composable
private fun InsulinIconPreview() {
    AppTheme {
        Surface {
            Icon(
                imageVector = Icons.Filled.Insulin,
                contentDescription = "Insulin Icon Preview",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}