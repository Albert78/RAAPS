package de.dh.raaps.common.model

import kotlinx.coroutines.flow.Flow

interface GlucosePlugin {
    val name: String
    val dataProviderType: String
    val readingsInterval: de.dh.raaps.common.model.data.BgReadingsInterval
    val readingsTimeDelay: de.dh.raaps.common.model.data.Minutes
    fun getSensorTypeName(): String
    fun getValues(): Flow<de.dh.raaps.common.model.data.BgReading>

    fun start()
    fun stop()
}