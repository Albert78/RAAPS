package de.dh.raaps.ui.common.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

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
