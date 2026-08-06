package de.dh.raaps.plugin.simbody.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.ID_MEAL_STANDARD
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.plugin.simbody.BodyModel
import de.dh.raaps.ui.controls.meal.FoodTypeSelector
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SimBodyEatMealDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, MealType) -> Unit
) {
    val mealTypes = BodyModel.SIM_MEAL_TYPES
    var carbs by remember { mutableDoubleStateOf(0.0) }
    var selectedType by remember { mutableStateOf(mealTypes.find { it.id == ID_MEAL_STANDARD } ?: mealTypes.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mahlzeit essen") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Kohlenhydrate (g):", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                EditableValueStepper(
                    currentValue = carbs,
                    onValueChange = { carbs = it },
                    minValue = 0.0,
                    maxValue = 200.0,
                    suffix = "g",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("Essenstyp:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FoodTypeSelector(
                    mealTypes = mealTypes,
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (carbs > 0) onConfirm(carbs, selectedType)
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SimBodyEatMealDialogPreview() {
    AppTheme {
        SimBodyEatMealDialog(
            onDismiss = {},
            onConfirm = { _, _ -> }
        )
    }
}