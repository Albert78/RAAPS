package de.dh.raaps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.dh.raaps.common.model.data.GlucoseUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val Preferences?.userDeclinedPermissions: Boolean
    get() = this?.get(USER_DECLINED_PERMISSIONS_KEY) ?: false

suspend fun AppPreferencesRepository.setUserDeclinedPermissions(value: Boolean) {
    editPreferences { mutablePreferences ->
        mutablePreferences[USER_DECLINED_PERMISSIONS_KEY] = value
    }
}

val Preferences?.glucoseUnit: GlucoseUnit
    get() = this?.get(GLUCOSE_UNIT_KEY)?.let { GlucoseUnit.valueOf(it) } ?: GlucoseUnit.MG_DL

suspend fun AppPreferencesRepository.setGlucoseUnit(value: GlucoseUnit) {
    editPreferences { mutablePreferences ->
        mutablePreferences[GLUCOSE_UNIT_KEY] = value.name
    }
}

val USER_DECLINED_PERMISSIONS_KEY = booleanPreferencesKey("user_declined_permissions")
val GLUCOSE_UNIT_KEY = stringPreferencesKey("glucose_unit")

class AppPreferencesRepository(private val context: Context, private val scope: CoroutineScope) {
    /**
     * Gets the eagerly loaded state of the preferences as StateFlow, i.e. it can be queried
     * without the use of a suspend function, but the value will initially be {@ null} until
     * the preferences are loaded.
     */
    val cachedPreferences: StateFlow<Preferences?> = context.dataStore.data
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val glucoseUnit: StateFlow<GlucoseUnit> = cachedPreferences
        .map { it.glucoseUnit }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = GlucoseUnit.MG_DL
        )

    /**
     * Gets the current preferences as a suspend function.
     */
    suspend fun getPreferences(): Preferences {
        return cachedPreferences.filterNotNull().first()
    }

    suspend fun editPreferences(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { mutablePreferences ->
            block(mutablePreferences)
        }
    }
}
