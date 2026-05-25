package de.dh.raaps.core.pump

import de.dh.raaps.common.model.ToDo
import de.dh.raaps.common.model.data.Timestamp

/**
 * Data class containing a set of actions to be executed on the insulin pump.
 */
class PumpActionsBuilder {
    fun clearTempBasals() {
        ToDo.toBeImplemented("clearTempBasals")
    }

    fun setTempBasal(basalUnits: Double, from: Timestamp, to: Timestamp) {
        ToDo.toBeImplemented("setTempBasal")
    }

    fun addInsulinApplication(insulinUnits: Double, at: Timestamp) {
        ToDo.toBeImplemented("addInsulinApplication")
    }

    fun execute(pumpModel: ApsPumpModel) {
        ToDo.toBeImplemented("execute")
    }
}