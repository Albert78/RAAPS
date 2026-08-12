package de.dh.raaps.ui.screens.systemcontrol

import androidx.compose.runtime.Composable

/**
 * Interface for Pump plugins to provide additional UI content in the System Control screen.
 * Plugins that implement this alongside InsulinPump will have their content displayed.
 */
interface PumpPluginUiProvider {
    /**
     * Composable content to be displayed in the Pump tab of the System Control screen.
     */
    @Composable
    fun PumpControlSection()
}
