package de.dh.raaps.ui.controls.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.ConfigurableDisplayStrategy
import de.dh.raaps.common.ui.ModuloSteppingStrategy
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.NeutralGrey
import de.dh.raaps.common.ui.theme.SoftBlue
import de.dh.raaps.common.ui.theme.SoftRed
import de.dh.raaps.ui.R

@Composable
fun TherapyAdjustmentDialogContent(
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    val steppingStrategy = remember { ModuloSteppingStrategy(5) }
    val displayStrategy = ConfigurableDisplayStrategy(
        positiveColor = SoftRed,
        negativeColor = SoftBlue,
        neutralColor = NeutralGrey,
        positivePrefix = "+",
        suffix = "%",
        neutralLabel = stringResource(R.string.aps_control_adjustment_neutral)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.aps_control_adjustment_dialog_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            EditableValueStepper(
                currentValue = currentValue,
                onValueChange = onValueChange,
                steppingStrategy = steppingStrategy,
                displayStrategy = displayStrategy,
                suffix = "%"
            )
        }
    }
}

@Composable
fun TherapyAdjustmentDialog(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(id = R.string.aps_control_adjustment_dialog_title)) },
        text = {
            TherapyAdjustmentDialogContent(
                currentValue = currentValue,
                onValueChange = onValueChange
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = android.R.string.ok))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun TherapyAdjustmentDialogPreview() {
    AppTheme {
        Surface {
            TherapyAdjustmentDialogContent(
                currentValue = 10,
                onValueChange = {}
            )
        }
    }
}