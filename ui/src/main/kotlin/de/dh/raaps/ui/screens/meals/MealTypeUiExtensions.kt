package de.dh.raaps.ui.screens.meals

import androidx.compose.ui.graphics.vector.ImageVector
import de.dh.raaps.common.model.ID_MEAL_FAST
import de.dh.raaps.common.model.ID_MEAL_HIGH_FAT
import de.dh.raaps.common.model.ID_MEAL_SLOW
import de.dh.raaps.common.model.ID_MEAL_STANDARD
import de.dh.raaps.common.model.MealType
import de.dh.raaps.ui.common.icons.Icon_Meal_Fast
import de.dh.raaps.ui.common.icons.Icon_Meal_High_Fat
import de.dh.raaps.ui.common.icons.Icon_Meal_Slow
import de.dh.raaps.ui.common.icons.Icon_Meal_Standard

/**
 * Returns the corresponding icon for a [MealType] based on its ID.
 * Returns [Icon_Meal_Standard] as fallback.
 */
fun MealType.getIcon(): ImageVector {
    return when (id) {
        ID_MEAL_FAST -> Icon_Meal_Fast
        ID_MEAL_STANDARD -> Icon_Meal_Standard
        ID_MEAL_HIGH_FAT -> Icon_Meal_High_Fat
        ID_MEAL_SLOW -> Icon_Meal_Slow
        else -> Icon_Meal_Standard
    }
}
