package de.dh.raaps.ui.screens.systemcontrol

import androidx.compose.runtime.Composable

/**
 * Interface for CGM plugins to provide additional UI content in the System Control screen.
 * Plugins that implement this alongside GlucoseSource will have their content displayed.
 */
interface CgmPluginUiProvider {
    /**
     * Composable content to be displayed in the CGM tab of the System Control screen.
     */
    @Composable
    fun CgmControlSection()
}
