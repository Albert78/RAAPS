package de.dh.raaps.common.ui.composables

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.theme.AppTheme

fun smallPaddingValues(): PaddingValues = PaddingValues(
    start = 16.dp,
    top = 8.dp,
    end = 16.dp,
    bottom = 8.dp,
)

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = smallPaddingValues(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.primary
        ),
        enabled = enabled,
        shape = RoundedCornerShape(5.dp),
    ) {
        content()
    }
}

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = smallPaddingValues(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSecondary,
            containerColor = MaterialTheme.colorScheme.secondary
        ),
        enabled = enabled,
        shape = RoundedCornerShape(5.dp)
    ) {
        content()
    }
}

@Composable
fun NormalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    content: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = smallPaddingValues(),
        colors = colors,
        enabled = enabled,
        shape = RoundedCornerShape(5.dp)
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PrimaryButtonPreview() {
    AppTheme {
        Surface() {
            PrimaryButton(
                modifier = Modifier.padding(8.dp),
                onClick = {}
            ) {
                Text(text = "Primary Button")
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SecondaryButtonPreview() {
    AppTheme {
        Surface() {
            SecondaryButton(
                modifier = Modifier.padding(8.dp),
                onClick = {}
            ) {
                Text(text = "Secondary Button")
            }
        }
    }
}

@Preview(showBackground = false)
@Preview(showBackground = false, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OutlinedButtonPreview() {
    AppTheme {
        Surface() {
            NormalButton(
                modifier = Modifier.padding(8.dp),
                onClick = {}
            ) {
                Text(text = "Normal Button")
            }
        }
    }
}