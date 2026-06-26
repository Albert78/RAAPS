package de.dh.raaps.core

import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.core.aps.APS
import de.dh.raaps.core.repository.GlucoseRepository
import de.dh.raaps.core.repository.TherapyRepository
import de.dh.raaps.core.repository.TreatmentRepository

interface RAAPSApplication {
    val appPreferencesRepository: AppPreferencesRepository
    val pluginManager: PluginManager
    val glucoseRepository: GlucoseRepository
    val therapyRepository: TherapyRepository
    val treatmentRepository: TreatmentRepository
    val aps: APS

    fun triggerUpdatesAfterPermissionsChange()
}