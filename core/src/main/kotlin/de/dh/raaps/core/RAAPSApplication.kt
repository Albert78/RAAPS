package de.dh.raaps.core

/**
 * Interface representing the RAAPS application and providing access to its components.
 */
interface RAAPSApplication : RAAPSRegistry {
    fun triggerUpdatesAfterPermissionsChange()
}