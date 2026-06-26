package de.dh.raaps.common.ui.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.theme.AppTheme

// Menu and header icons
val Menu_More = Icons.Default.MoreVert
val Menu_Delete = Icons.Default.Delete
val Icon_Screen_Back = Icons.AutoMirrored.Filled.ArrowBack
val Icon_Screen_Close = Icons.Default.Clear

val Icon_Add = Icons.Outlined.Add
val Icon_Edit = Icons.Outlined.Edit
val Icon_Info = Icons.Outlined.Info
val Icon_Clear = Icons.Outlined.Clear
val Icon_Error = Icons.Outlined.Error
val Icon_Config = Icons.Outlined.Config_Outline
val Icon_Delete = Icons.Outlined.Delete
val Icon_Archive = Icons.Outlined.Archive
val Icon_Warning = Icons.Outlined.Warning
val Icon_Arrow_Up = Icons.Outlined.ArrowUpward
val Icon_Check_No = Icons.Outlined.Close
val Icon_Comments = Icons.AutoMirrored.Outlined.Comment
val Icon_Settings = Icons.Outlined.Settings
val Icon_Check_Yes = Icons.Outlined.Check
val Icon_Arrow_Down = Icons.Outlined.ArrowDownward
val Icon_Alarm_Snooze = Icons.Outlined.Snooze
val Icon_Theme_Light_Dark = Icons.Outlined.Theme_Light_Dark
val Icon_Scrollview_Arrow_Up = Icons.Outlined.KeyboardArrowUp
val Icon_Scrollview_Arrow_Down = Icons.Outlined.KeyboardArrowDown
val Icon_Ui = Icons.Outlined.Palette
val Icon_Backup = Icons.Outlined.Backup
val Icon_Restore = Icons.Outlined.Restore

private data class IconPreview(
    val name: String,
    val imageVector: ImageVector
)

private val iconsForPreview = listOf(
    IconPreview("Menu_More", Menu_More),
    IconPreview("Menu_Delete", Menu_Delete),
    IconPreview("Icon_Screen_Back", Icon_Screen_Back),
    IconPreview("Icon_Screen_Close", Icon_Screen_Close),
    IconPreview("Add", Icon_Add),
    IconPreview("Edit", Icon_Edit),
    IconPreview("Info", Icon_Info),
    IconPreview("Clear", Icon_Clear),
    IconPreview("Error", Icon_Error),
    IconPreview("Config", Icon_Config),
    IconPreview("Delete", Icon_Delete),
    IconPreview("Archive", Icon_Archive),
    IconPreview("Warning", Icon_Warning),
    IconPreview("Arrow_Up", Icon_Arrow_Up),
    IconPreview("Check_No", Icon_Check_No),
    IconPreview("Comments", Icon_Comments),
    IconPreview("Settings", Icon_Settings),
    IconPreview("Check_Yes", Icon_Check_Yes),
    IconPreview("Arrow_Down", Icon_Arrow_Down),
    IconPreview("Alarm_Snooze", Icon_Alarm_Snooze),
    IconPreview("Theme_Light_Dark", Icon_Theme_Light_Dark),
    IconPreview("Scrollview_Arrow_Up", Icon_Scrollview_Arrow_Up),
    IconPreview("Scrollview_Arrow_Down", Icon_Scrollview_Arrow_Down),
    IconPreview("Ui", Icon_Ui),
    IconPreview("Backup", Icon_Backup),
    IconPreview("Restore", Icon_Restore)
)

@Preview(showBackground = true, widthDp = 320, heightDp = 1400, name = "Icon Catalog")
@Composable
fun IconCatalogPreview() {
    AppTheme {
        Surface {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text("Icon", modifier = Modifier.weight(1f))
                        Text("Name", modifier = Modifier.weight(3f))
                    }
                }
                items(iconsForPreview) { preview ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = preview.imageVector,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .weight(1f)
                        )
                        Text(
                            preview.name, modifier = Modifier
                                .weight(3f)
                                .padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}