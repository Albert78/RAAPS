package de.dh.raaps.common.ui

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import de.dh.raaps.AppPreferencesRepository

val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

val Preferences?.themeMode: ThemeMode
    get() = this?.get(THEME_MODE_KEY)?.let { ThemeMode.fromValue(it) } ?: ThemeMode.SYSTEM

suspend fun AppPreferencesRepository.setThemeMode(value: ThemeMode) {
    editPreferences { mutablePreferences ->
        mutablePreferences[THEME_MODE_KEY] = value.value
    }
}