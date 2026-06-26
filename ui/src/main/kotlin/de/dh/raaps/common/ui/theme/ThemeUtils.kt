package de.dh.raaps.common.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.ui.ThemeMode
import de.dh.raaps.common.ui.themeMode

@Composable
fun rememberUseDarkTheme(appPreferencesRepository: AppPreferencesRepository): Boolean {
    val preferences by appPreferencesRepository.cachedPreferences.collectAsState(null)

    // TODO: This causes a flicker because we might return the initial value until the state
    // repository is read completely. Use a splash screen until theme mode ist read?
    return when (preferences.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
}