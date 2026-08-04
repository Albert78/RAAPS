package de.dh.raaps.common.ui.composables

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.DefaultSteppingStrategy
import de.dh.raaps.common.ui.DefaultValueDisplayStrategy
import de.dh.raaps.common.ui.SteppingStrategy
import de.dh.raaps.common.ui.ValueDisplayStrategy
import de.dh.raaps.common.ui.theme.AppTheme

@Composable
fun EditableValueStepper(
    currentValue: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Double = Double.NEGATIVE_INFINITY,
    maxValue: Double = Double.POSITIVE_INFINITY,
    steppingStrategy: SteppingStrategy = DefaultSteppingStrategy(),
    displayStrategy: ValueDisplayStrategy = DefaultValueDisplayStrategy(),
    suffix: String = "",
    style: StepperStyle = StepperDefaults.defaultStyle()
) {
    var isEditing by remember { mutableStateOf(false) }
    // Track if the field actually gained focus to avoid closing immediately on mount
    var hasGainedFocus by remember { mutableStateOf(false) }

    var textFieldValue by remember(isEditing) {
        val initialText = if (currentValue % 1.0 == 0.0) currentValue.toInt().toString() else currentValue.toString()
        val selection = if (isEditing) TextRange(0, initialText.length) else TextRange.Zero
        mutableStateOf(TextFieldValue(initialText, selection = selection))
    }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Reset focus tracking when editing starts
    LaunchedEffect(isEditing) {
        if (!isEditing) {
            hasGainedFocus = false
        }
    }

    val onStepUp = {
        val nextValue = steppingStrategy.stepUp(currentValue).coerceAtMost(maxValue)
        onValueChange(nextValue)
        isEditing = false
        focusManager.clearFocus()
    }

    val onStepDown = {
        val nextValue = steppingStrategy.stepDown(currentValue).coerceAtLeast(minValue)
        onValueChange(nextValue)
        isEditing = false
        focusManager.clearFocus()
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onStepDown,
            modifier = Modifier.size(style.buttonSize),
            enabled = currentValue > minValue
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(style.buttonSize * 0.5f))
        }

        Spacer(Modifier.width(style.spacing))

        if (isEditing) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    // Immediate update of current value, ignore if it contains non-digits/decimal
                    val cleanedText = newValue.text.replace(',', '.')
                    if (cleanedText.isEmpty() || cleanedText.all { it.isDigit() || it == '.' }) {
                        cleanedText.toDoubleOrNull()?.let { onValueChange(it.coerceIn(minValue, maxValue)) }
                    }
                },
                modifier = Modifier
                    .width(style.valueWidth)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            hasGainedFocus = true
                        } else if (hasGainedFocus) {
                            isEditing = false
                        }
                    },
                textStyle = style.textStyle.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        isEditing = false
                        focusManager.clearFocus()
                    }
                ),
                singleLine = true
            )
        } else {
            if (style.suffixBelowValue) {
                Column(
                    modifier = Modifier
                        .width(style.valueWidth)
                        .clickable {
                            isEditing = true
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayStrategy.format(currentValue),
                        style = style.textStyle.copy(
                            color = displayStrategy.color(currentValue).let {
                                if (it == Color.Unspecified) MaterialTheme.colorScheme.onSurface else it
                            }
                        ),
                        textAlign = TextAlign.Center
                    )
                    if (suffix.isNotEmpty()) {
                        Text(
                            text = suffix.trim(),
                            style = style.suffixStyle,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .width(style.valueWidth)
                        .clickable {
                            isEditing = true
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayStrategy.format(currentValue),
                        style = style.textStyle.copy(
                            color = displayStrategy.color(currentValue).let {
                                if (it == Color.Unspecified) MaterialTheme.colorScheme.onSurface else it
                            }
                        ),
                        textAlign = TextAlign.Center
                    )
                    if (suffix.isNotEmpty()) {
                        Spacer(Modifier.width(style.spacing * 0.25f))
                        Text(
                            text = suffix.trim(),
                            style = style.suffixStyle
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(style.spacing))

        IconButton(
            onClick = onStepUp,
            modifier = Modifier.size(style.buttonSize),
            enabled = currentValue < maxValue
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(style.buttonSize * 0.5f))
        }
    }
}

data class StepperStyle(
    val buttonSize: Dp,
    val spacing: Dp,
    val valueWidth: Dp,
    val textStyle: TextStyle,
    val suffixStyle: TextStyle,
    val suffixBelowValue: Boolean = false
)

object StepperDefaults {
    @Composable
    fun defaultStyle() = StepperStyle(
        buttonSize = 48.dp,
        spacing = 16.dp,
        valueWidth = 120.dp,
        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        suffixStyle = MaterialTheme.typography.titleSmall
    )

    @Composable
    fun compactStyle() = StepperStyle(
        buttonSize = 36.dp,
        spacing = 4.dp,
        valueWidth = 48.dp,
        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        suffixStyle = MaterialTheme.typography.labelSmall,
        suffixBelowValue = true
    )

    @Composable
    fun mediumStyle() = StepperStyle(
        buttonSize = 44.dp,
        spacing = 8.dp,
        valueWidth = 90.dp,
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        suffixStyle = MaterialTheme.typography.bodyMedium
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun EditableValueStepperPreview() {
    var value by remember { mutableStateOf(100.0) }
    AppTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text("Default Style", style = MaterialTheme.typography.labelSmall)
                    EditableValueStepper(
                        currentValue = value,
                        onValueChange = { value = it },
                        minValue = 90.0,
                        maxValue = 110.0,
                        suffix = "mg/dL"
                    )
                }

                Column {
                    Text("Compact Style (Suffix Beside)", style = MaterialTheme.typography.labelSmall)
                    EditableValueStepper(
                        currentValue = value,
                        onValueChange = { value = it },
                        minValue = 90.0,
                        maxValue = 110.0,
                        suffix = "mg/dL",
                        style = StepperDefaults.compactStyle().copy(suffixBelowValue = false, valueWidth = 82.dp)
                    )
                }

                Column {
                    Text("Compact Style (Suffix Below)", style = MaterialTheme.typography.labelSmall)
                    EditableValueStepper(
                        currentValue = value,
                        onValueChange = { value = it },
                        minValue = 90.0,
                        maxValue = 110.0,
                        suffix = "mg/dL",
                        style = StepperDefaults.compactStyle()
                    )
                }
            }
        }
    }
}