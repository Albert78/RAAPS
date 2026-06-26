package de.dh.raaps.common.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

fun combineEntryProviders(vararg graphs: FeatureNavGraph): (NavKey) -> NavEntry<NavKey> {
    return { key ->
        graphs.firstNotNullOfOrNull { it.getEntry(key) } 
            ?: throw IllegalStateException("Unknown route $key")
    }
}