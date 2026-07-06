package de.dh.raaps.common.model

enum class ApsMode {
    /**
     * The system is controlled manually by the user and doesn't show
     * treatment hints.
     */
    Manual,

    /**
     * The APS is working and producing treatment hints but doesn't
     * issue the commands to the pump. Instead, the user gets treatment hints.
     */
    OpenLoop,

    /**
     * The APS completely controls the pump.
     */
    ClosedLoop
}
