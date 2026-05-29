package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.Minutes
import kotlinx.coroutines.flow.Flow

interface GlucoseSource {
    val name: String
    val dataProviderType: String
    val readingsInterval: BgReadingsInterval

    /**
     * Gets the expected time delay the readings (and readings timestamp)
     * are behind blood glucose.
     */
    val readingsTimeDelay: Minutes
    fun getSensorTypeName(): String
    fun getValues(): Flow<BgReading>

    fun start()
    fun stop()
}