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

public val Icons.Filled.Minus: ImageVector
    get() {
        if (_minus != null) {
            return _minus!!
        }
        _minus = materialIcon(name = "Filled.Minus") {
            materialPath {
                moveTo(19.0f, 13.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _minus!!
    }

private var _minus: ImageVector? = null

@Preview(showBackground = true)
@Composable
private fun MinusIconPreview() {
    AppTheme {
        Surface {
            Icon(
                imageVector = Icons.Filled.Minus,
                contentDescription = "Minus Icon Preview",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}