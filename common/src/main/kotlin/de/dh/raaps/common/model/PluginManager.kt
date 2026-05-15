package de.dh.raaps.common.model

interface PluginManager {
    fun checkSelfPermissions(androidPermissions: Collection<String>): Collection<String>
}