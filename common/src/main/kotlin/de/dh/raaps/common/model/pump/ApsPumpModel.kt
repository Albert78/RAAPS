package de.dh.raaps.common.model.pump

import de.dh.raaps.AppPreferencesRepository

/**
 * Represents the pump communication gateway for the APS core.
 * The APS pump model takes pump commands and communicates them to the pump driver of the actual pump.
 * The model also contains the state which was reported by the pump driver, to be used in the
 * core and in the UI.
 */
class ApsPumpModel {
    companion object {
        fun create(
            dataRepository: DataRepository,
            appPreferencesRepository: AppPreferencesRepository
        ) {
            weiter
        }
    }

}