package de.dh.pump

/**
 * High-level, vendor-agnostic domain status for command execution across any pump hardware.
 */
enum class PumpStatus {
    OK,
    REJECTED,
    BUSY,
    INVALID_PARAMETER,
    NOT_AUTHORIZED,
    DEVICE_ERROR,
    ILLEGAL_STATE,
    TIMEOUT,
    UNKNOWN;

    val isSuccess: Boolean get() = this == OK
    val isError: Boolean get() = this != OK
}

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
class PumpCommandException(
    val status: PumpStatus,
    val commandName: String,
    val resultCode: Int? = null,
    val vendorMessage: String? = null,
    cause: Throwable? = null,
) : PumpException(
    message = buildString {
        append("Command '$commandName' failed with status $status")
        if (resultCode != null) {
            append(" (raw code: 0x${"%02X".format(resultCode)})")
        }
        if (!vendorMessage.isNullOrBlank()) {
            append(": $vendorMessage")
        }
    },
    cause = cause,
)