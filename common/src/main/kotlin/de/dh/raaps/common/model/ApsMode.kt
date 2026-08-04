package de.dh.raaps.common.model

enum class ApsMode {
    /**
     * The APS completely controls the pump.
     */
    AutoCorrection,

    /**
     * The APS only delivers default basal, no other treatments are issued.
     */
    BasalOnly,

    /**
     * The system is suspended, only manual interaction with the pump is possible.
     */
    Suspend
}