package de.dh.raaps.common.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.ui.DefaultSteppingStrategy
import de.dh.raaps.common.ui.DefaultValueDisplayStrategy
import de.dh.raaps.common.ui.SteppingStrategy
import de.dh.raaps.common.ui.ValueDisplayStrategy
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun EditableValueStepper(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    steppingStrategy: SteppingStrategy = DefaultSteppingStrategy(),
    displayStrategy: ValueDisplayStrategy = DefaultValueDisplayStrategy(),
    suffix: String = ""
) {
    var isEditing by remember { mutableStateOf(false) }
    // Track if the field actually gained focus to avoid closing immediately on mount
    var hasGainedFocus by remember { mutableStateOf(false) }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(currentValue.toString()))
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
        val nextValue = steppingStrategy.stepUp(currentValue)
        onValueChange(nextValue)
        isEditing = false
        focusManager.clearFocus()
    }

    val onStepDown = {
        val nextValue = steppingStrategy.stepDown(currentValue)
        onValueChange(nextValue)
        isEditing = false
        focusManager.clearFocus()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onStepDown,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }

        Spacer(Modifier.width(16.dp))

        if (isEditing) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Row(
                modifier = Modifier.width(120.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        // Immediate update of current value
                        newValue.text.toIntOrNull()?.let { onValueChange(it) }
                    },
                    modifier = Modifier
                        .width(80.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                hasGainedFocus = true
                            } else if (hasGainedFocus) {
                                isEditing = false
                            }
                        },
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
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
                if (suffix.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(suffix, style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            Text(
                text = displayStrategy.format(currentValue),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = displayStrategy.color(currentValue).let {
                        if (it == Color.Unspecified) MaterialTheme.colorScheme.onSurface else it
                    }
                ),
                modifier = Modifier
                    .width(120.dp)
                    .clickable {
                        isEditing = true
                        val text = currentValue.toString()
                        textFieldValue = TextFieldValue(
                            text = text,
                            selection = TextRange(0, text.length)
                        )
                    },
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.width(16.dp))

        IconButton(
            onClick = onStepUp,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}