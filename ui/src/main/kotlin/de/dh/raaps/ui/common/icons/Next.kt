package de.dh.raaps.ui.common.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

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
