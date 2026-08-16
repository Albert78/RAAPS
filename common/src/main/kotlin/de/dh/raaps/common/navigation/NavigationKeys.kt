package de.dh.raaps.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable object DashboardRoute : NavKey
@Serializable object HistoryRoute : NavKey
@Serializable object PermissionsRoute : NavKey
@Serializable object PreferencesMainRoute : NavKey
@Serializable object InsulinProfileEditorRoute : NavKey
@Serializable object CurrentTherapySettingsRoute : NavKey
@Serializable object MealBolusRoute : NavKey
@Serializable data class HistoricalMealRoute(val mealId: Long) : NavKey
@Serializable object MealsRoute : NavKey
@Serializable object MealTypesRoute : NavKey
@Serializable data class MealTypeEditorRoute(val mealTypeId: String? = null) : NavKey
@Serializable object BolusHistoryRoute : NavKey
@Serializable object FoodDatabaseRoute : NavKey
@Serializable object BgEditorRoute : NavKey
@Serializable object TherapyAdjustmentRoute : NavKey
@Serializable data class SystemControlRoute(val initialTab: Int = 0) : NavKey
@Serializable object PumpManagementRoute : NavKey
@Serializable object CoreDecisionsRoute : NavKey
@Serializable object AlarmsRoute : NavKey