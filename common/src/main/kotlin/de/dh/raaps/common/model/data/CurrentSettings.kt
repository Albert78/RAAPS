package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.ID_UNDEFINED

/**
 * Data model for the current system settings.
 * This is separate from therapy-specific configuration.
 */
data class CurrentSettings(
    var id: Long = ID_UNDEFINED,
    val apsMode: ApsMode = ApsMode.Suspend
)