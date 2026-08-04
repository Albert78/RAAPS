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