package de.dh.raaps.common.model

/**
 * Abstraction of functionality which is maintained independently of the main app, i.e. plugins
 * declare their own needed permissions (and more, if needed later).
 */
interface Plugin {
    val neededPermissions: Collection<String>

    fun initialize(pluginManager: PluginManager)
}