package de.dh.raaps.ui.common.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

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
