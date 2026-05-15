package de.dh.raaps.common.model

import kotlinx.coroutines.flow.Flow

interface GlucosePlugin: Plugin {
    val name: String
    val dataProviderType: String
    val readingsInterval: de.dh.raaps.common.model.data.BgReadingsInterval

    /**
     * Gets the expected time delay the readings (and readings timestamp)
     * are behind blood glucose.
     */
    val readingsTimeDelay: de.dh.raaps.common.model.data.Minutes
    fun getSensorTypeName(): String
    fun getValues(): Flow<de.dh.raaps.common.model.data.BgReading>

    fun start()
    fun stop()
}