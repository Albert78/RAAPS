package de.dh.raaps.ui.common.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.ui.common.theme.AppTheme

/**
 * Ein maßgeschneidertes Spritzen-Icon im Material-Stil, um 45 Grad gedreht.
 */
val Icons.Outlined.Syringe: ImageVector
    get() {
        if (_syringe != null) {
            return _syringe!!
        }
        _syringe = materialIcon(name = "Outlined.Syringe") {
            group(
                rotate = 45f,
                pivotX = 12f,
                pivotY = 12f
            ) {
                materialPath {
                    // Plunger Handle (Griff oben)
                    moveTo(8f, 1f)
                    horizontalLineTo(16f)
                    verticalLineTo(3f)
                    horizontalLineTo(8f)
                    close()

                    // Plunger Rod (Stange)
                    moveTo(11.25f, 3f)
                    horizontalLineTo(12.75f)
                    verticalLineTo(7f)
                    horizontalLineTo(11.25f)
                    close()

                    // Flanges (Fingerauflage)
                    moveTo(7f, 7f)
                    horizontalLineTo(17f)
                    verticalLineTo(9f)
                    horizontalLineTo(7f)
                    close()

                    // Cylinder Body (Spritzenkörper)
                    moveTo(9f, 9f)
                    horizontalLineTo(15f)
                    verticalLineTo(18f)
                    horizontalLineTo(9f)
                    close()

                    // Tip (Ansatzstück)
                    moveTo(11f, 18f)
                    horizontalLineTo(13f)
                    verticalLineTo(20f)
                    horizontalLineTo(11f)
                    close()

                    // Needle (Nadel)
                    moveTo(11.75f, 20f)
                    horizontalLineTo(12.25f)
                    verticalLineTo(24f)
                    horizontalLineTo(11.75f)
                    close()
                }
            }
        }
        return _syringe!!
    }

private var _syringe: ImageVector? = null

@Preview(showBackground = true)
@Composable
fun SyringePreview() {
    AppTheme {
        Surface {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Syringe,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
