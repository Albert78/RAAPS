package de.dh.raaps.common.model

enum class ApsMode {
    /**
     * The system is suspended, only manual interaction with the pump is possible.
     */
    Suspend,

    /**
     * The APS only delivers default basal, no other treatments are issued.
     */
    BasalOnly,

    /**
     * The APS completely controls the pump.
     */
    AutoCorrection
}