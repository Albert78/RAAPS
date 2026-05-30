package de.dh.raaps.common.model

/**
 * Abstraction of functionality which is maintained independently of the main app.
 * Plugins declare their own needed permissions (and more, if needed later, we'll see).
 *
 * The main idea is that plugin developers can develop their functionality completely
 * independently of the main app and of the core.
 * Plugins are **not** loaded dynamically at runtime; they are static components of an assembled app.
 * The set of active plugins is determined by build flavors.
 * The flavor injector is responsible for the setup of the system.
 *
 * Classes may implement [Plugin] alongside other functional interfaces like [GlucoseSource].
 */
interface Plugin {
    val name: String
    val neededPermissions: Collection<String>

    fun initialize(pluginManager: PluginManager)
}