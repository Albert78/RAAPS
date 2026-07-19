package de.dh.dapsmockup.ui.mockups.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.composables.Picker
import de.dh.raaps.common.ui.composables.PickerItems
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.NeutralGrey
import de.dh.raaps.common.ui.theme.SoftBlue
import de.dh.raaps.common.ui.theme.SoftRed

@Composable
fun InsulinModificationDialogContent(
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    val pickerSteps = remember { (50 downTo -50 step 5).toList() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Picker(
            items = PickerItems(pickerSteps),
            selectedItem = currentValue,
            onItemSelected = onValueChange,
            label = { v -> if (v == 0) "neutral" else "${if (v > 0) "+" else ""}$v%" },
            itemColor = { v ->
                when {
                    v > 0 -> SoftRed
                    v < 0 -> SoftBlue
                    else -> NeutralGrey
                }
            },
            textStyle = MaterialTheme.typography.titleLarge,
            visibleItemsCount = 5,
            wrapSelectorWheel = false
        )
    }
}

@Composable
fun InsulinModificationDialog(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Insulin-Anpassung") },
        text = {
            InsulinModificationDialogContent(
                currentValue = currentValue,
                onValueChange = onValueChange
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Schließen")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun InsulinModificationDialogPreview() {
    AppTheme {
        Surface {
            InsulinModificationDialogContent(
                currentValue = 10,
                onValueChange = {}
            )
        }
    }
}