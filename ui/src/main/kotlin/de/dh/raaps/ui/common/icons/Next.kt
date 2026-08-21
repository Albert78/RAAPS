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

public val Icons.Filled.Next: ImageVector
    get() {
        if (_next != null) {
            return _next!!
        }
        _next = materialIcon(name = "Filled.Next") {
            materialPath {
                moveTo(8.0f, 5.0f)
                verticalLineToRelative(14.0f)
                lineToRelative(11.0f, -7.0f)
                close()
            }
        }
        return _next!!
    }

private var _next: ImageVector? = null

@Preview(showBackground = true)
@Composable
private fun NextIconPreview() {
    AppTheme {
        Surface {
            Icon(
                imageVector = Icons.Filled.Next,
                contentDescription = "Next Icon Preview",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}