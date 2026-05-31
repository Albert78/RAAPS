package de.dh.raaps.core.pump

import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.ToDo
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.DataRepository

/**
 * Represents the pump communication gateway between the APS core and the actual pump plugin.
 * The APS pump model takes pump commands and communicates them to the pump driver of the actual pump.
 * The model also contains the state which was reported by the pump driver, to be used in the
 * core and in the UI.
 */
class ApsPumpModel {
    // ********************************** Info and current state ***********************************
    fun isReady(): Boolean {
        ToDo.toBeImplemented("isReady")
        return true
    }

    // ****************************************** Actions ******************************************

    fun clearTempBasalRates() {
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

    companion object {
        fun create(
            dataRepository: DataRepository,
            appPreferencesRepository: AppPreferencesRepository
        ): ApsPumpModel {
            ToDo.toBeImplemented("ApsPumpModel")
            return ApsPumpModel()
        }
    }

}