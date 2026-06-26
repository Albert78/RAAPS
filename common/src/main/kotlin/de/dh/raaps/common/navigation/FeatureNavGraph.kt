package de.dh.raaps.common.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

interface FeatureNavGraph {
    fun getEntry(key: NavKey): NavEntry<NavKey>?

    @Composable
    fun DashboardExtension() {
        // Default: no extension
    }
}