package de.dh.raaps.common.ui.composables

import android.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.dh.raaps.common.ui.theme.AppTheme

@Composable
fun DialogDismissButton(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
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
            Text(stringResource(id = R.string.cancel))
        }
    }
}

@Preview
@Composable
fun DialogDismissButtonPreview() {
    AppTheme {
        Surface {
            DialogDismissButton(
                onDismiss = {}
            )
        }
    }
}