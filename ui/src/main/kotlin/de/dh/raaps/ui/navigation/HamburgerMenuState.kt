package de.dh.raaps.ui.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

val LocalHamburgerAlpha = compositionLocalOf<MutableState<Float>> {
    mutableStateOf(1f)
}