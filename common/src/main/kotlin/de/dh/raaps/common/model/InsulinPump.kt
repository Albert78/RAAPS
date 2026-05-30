package de.dh.raaps.common.model

/**
 * Interface for an insulin delivery device.
 * Manages the connection to the physical pump hardware and handles insulin delivery operations.
 */
interface InsulinPump {
    val name: String
    // TODO

    fun start()
    fun stop()
}