package de.dh.raaps.common.model.pump

import de.dh.raaps.common.model.data.Timestamp

/**
 * Data class containing a set of actions to be given to the insulin pump.
 */
class PumpActions {
    class Builder {
        fun clearTempBasals() {
            weiter
        }

        fun setTempBasal(basalUnits: Double, from: Timestamp, to: Timestamp) {
            weiter
        }
    }
}