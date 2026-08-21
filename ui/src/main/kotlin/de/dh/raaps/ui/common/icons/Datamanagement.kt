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

@Preview(showBackground = true)
@Composable
private fun BackupIconPreview() {
    AppTheme {
        Surface {
            Icon(
                imageVector = Icons.Outlined.Backup,
                contentDescription = "Backup Icon Preview",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RestoreIconPreview() {
    AppTheme {
        Surface {
            Icon(
                imageVector = Icons.Outlined.Restore,
                contentDescription = "Restore Icon Preview",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}