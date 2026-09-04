package de.dh.pump


/**
 * Base class for all pump-related exceptions.
 */
sealed class PumpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when a technical connection to the pump cannot be established or is lost.
 */
class PumpConnectionException(message: String, cause: Throwable? = null) : PumpException(message, cause)

/**
 * Thrown when the pump receives a command but responds with an error status.
 */
class PumpCommandException(message: String, cause: Throwable? = null) : PumpException(message, cause)