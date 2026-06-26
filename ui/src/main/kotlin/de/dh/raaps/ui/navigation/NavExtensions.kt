package de.dh.raaps.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.dh.raaps.common.navigation.FeatureNavGraph

fun combineEntryProviders(vararg graphs: FeatureNavGraph): (NavKey) -> NavEntry<NavKey> {
    return { key ->
        graphs.firstNotNullOfOrNull { it.getEntry(key) } 
            ?: throw IllegalStateException("Unknown route $key")
    }
}