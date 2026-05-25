package de.dh.raaps.core.aps

import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.repository.DataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Persists each [BgReading] emitted by the flow to the persistent storage.
 *
 * This function acts as a side effect operator within the pipeline. It uses [onEach]
 * to intercept the stream and perform a database insertion via the [de.dh.raaps.core.repository.DataRepository]
 * without modifying the readings. This allows the data to continue flowing to
 * subsequent processing stages (like sampling or smoothing) unchanged.
 *
 * @param dataRepository The repository responsible for database operations.
 * @param dataProvider The source entity that provided the glucose data.
 * @param sourceSensor The type of sensor from which the reading originated.
 * @return A Flow that emits the same [BgReading] objects after the persistence side effect.
 */
fun Flow<BgReading>.persist(
    dataRepository: DataRepository,
    dataProvider: DataProvider,
    sourceSensor: SensorType
): Flow<BgReading> = onEach { reading ->
    dataRepository.insertDataProviderGlucoseReading(reading, dataProvider, sourceSensor)
}