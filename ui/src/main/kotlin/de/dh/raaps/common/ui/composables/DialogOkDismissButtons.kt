package de.dh.raaps.common.ui.composables

import android.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.theme.AppTheme

@Composable
fun DialogOkDismissButtons(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    okButtonText: String = stringResource(id = R.string.ok),
    cancelButtonText: String = stringResource(id = R.string.cancel),
    isOkEnabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Spacer(
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onDismiss,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(cancelButtonText)
        }
        Spacer(modifier = Modifier.Companion.padding(start = 8.dp))
        TextButton(
            onClick = onConfirm,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(okButtonText)
        }
    }
}

@Composable
fun DialogOkButton(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    okButtonText: String = stringResource(id = R.string.ok),
    isOkEnabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Spacer(
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onConfirm,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(okButtonText)
        }
    }
}

@Preview
@Composable
fun DialogOkDismissButtonsPreview() {
    AppTheme {
        Surface {
            DialogOkDismissButtons(
                onConfirm = {},
                onDismiss = {}
            )
        }
    }
}

@Preview
@Composable
fun DialogOkButtonPreview() {
    AppTheme {
        Surface {
            DialogOkButton(
                onConfirm = {}
            )
        }
    }
}