package de.dh.raaps.core.system

import de.dh.raaps.core.RAAPSRegistry

/**
 * Interface to be implemented by the Application class to provide access to the registry.
 */
interface RegistryProvider {
    val registry: RAAPSRegistry
}