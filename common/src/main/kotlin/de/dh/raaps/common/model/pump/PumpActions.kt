package de.dh.raaps.common.model.pump

import de.dh.raaps.common.model.data.Timestamp

/**
 * Data class containing a set of actions to be executed on the insulin pump.
 */
class PumpActionsBuilder {
    fun clearTempBasals() {
        weiter
    }

    fun setTempBasal(basalUnits: Double, from: Timestamp, to: Timestamp) {
        weiter
    }

    fun addInsulinApplication(insulinUnits: Double, at: Timestamp) {
        weiter
    }

    fun execute(pumpModel: ApsPumpModel) {
        weiter
    }
}