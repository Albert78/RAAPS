package de.dh.raaps.core.pump

/**
 * Normalized state of pump-related issues that prevent the system from working correctly.
 */
enum class PumpIssue {
    /**
     * The pump connection is missing, the system is not able to do its work.
     * The work will be continued when the connection is available again.
     */
    ConnectionMissing,

    /**
     * The pump is connected but in a state where it cannot deliver insulin (e.g. suspended,
     * battery empty, reservoir empty, hardware error).
     */
    Inoperative,

    /**
     * Any other issue that prevents the pump communication from working.
     */
    Other
}