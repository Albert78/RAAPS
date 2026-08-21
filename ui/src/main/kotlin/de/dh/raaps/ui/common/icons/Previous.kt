package de.dh.raaps.ui.common.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.ui.common.theme.AppTheme

public val Icons.Filled.Previous: ImageVector
    get() {
        if (_previous != null) {
            return _previous!!
        }
        _previous = materialIcon(name = "Filled.Previous") {
            materialPath {
                moveTo(16.0f, 5.0f)
                verticalLineToRelative(14.0f)
                lineToRelative(-11.0f, -7.0f)
                close()
            }
        }
        return _previous!!
    }

private var _previous: ImageVector? = null

@Preview(showBackground = true)
@Composable
private fun PreviousIconPreview() {
    AppTheme {
        Surface {
            Icon(
                imageVector = Icons.Filled.Previous,
                contentDescription = "Previous Icon Preview",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}