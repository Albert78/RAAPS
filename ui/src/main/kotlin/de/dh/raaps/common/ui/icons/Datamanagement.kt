package de.dh.raaps.common.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Standard-Icon für Backup (Datei-Export).
 * Basierend auf dem FileUpload-Konzept: Pfeil aus einem Speicherbalken heraus.
 */
public val Icons.Outlined.Backup: ImageVector
    get() {
        if (_backup != null) {
            return _backup!!
        }
        _backup = materialIcon(name = "Outlined.Backup") {
            materialPath {
                // Massive Arrow pointing UP
                moveTo(9.0f, 16.0f)
                horizontalLineTo(15.0f)
                verticalLineTo(10.0f)
                horizontalLineTo(19.0f)
                lineTo(12.0f, 3.0f)
                lineTo(5.0f, 10.0f)
                horizontalLineTo(9.0f)
                verticalLineTo(16.0f)
                close()
                // Storage Bar at the bottom
                moveTo(5.0f, 18.0f)
                horizontalLineTo(19.0f)
                verticalLineTo(20.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(18.0f)
                close()
            }
        }
        return _backup!!
    }

/**
 * Standard-Icon für Restore (Datei-Import).
 * Basierend auf dem FileDownload-Konzept: Pfeil hinein in einen Speicherbalken.
 */
public val Icons.Outlined.Restore: ImageVector
    get() {
        if (_restore != null) {
            return _restore!!
        }
        _restore = materialIcon(name = "Outlined.Restore") {
            materialPath {
                // Massive Arrow pointing DOWN
                moveTo(19.0f, 9.0f)
                horizontalLineTo(15.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(9.0f)
                verticalLineTo(9.0f)
                horizontalLineTo(5.0f)
                lineTo(12.0f, 16.0f)
                lineTo(19.0f, 9.0f)
                close()
                // Storage Bar at the bottom
                moveTo(5.0f, 18.0f)
                verticalLineTo(20.0f)
                horizontalLineTo(19.0f)
                verticalLineTo(18.0f)
                horizontalLineTo(5.0f)
                close()
            }
        }
        return _restore!!
    }

private var _backup: ImageVector? = null
private var _restore: ImageVector? = null