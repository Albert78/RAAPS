package de.dh.raaps.model

import android.util.Log
import de.dh.raaps.common.model.DataProvider
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.SensorType
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.data.DataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs

/**
 * Persists each [BgReading] emitted by the flow to the persistent storage.
 *
 * This function acts as a side effect operator within the pipeline. It uses [onEach]
 * to intercept the stream and perform a database insertion via the [DataRepository]
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

/**
 * Downsamples the data stream by aligning readings to fixed time intervals (ticks).
 *
 * This function divides the timeline into fixed buckets of a specified duration,
 * anchored to midnight at start of the epoch. It ensures that only the first reading
 * encountered within each tick is emitted, while subsequent readings in the same interval
 * are discarded.
 *
 * Attention: This simple sampling function won't work well for timestamps around the tick borders
 * with a bit of jitter. If e.g. the timestamps modulo our tick borders are allmost 0, and the
 * sequence of timestamps are not absolutely equidistant, samples will fall into one or the following
 * tick slot. This will leave some slots empty while others are populated with two values.
 *
 * @param tickInterval The interval size of the returned sample grid in minutes.
 * @return A Flow of Pairs containing:
 *         - [BgReading]: The original BgReading.
 *         - [Tick]: The Tick containing the zero-based index of the sample.
 */
fun Flow<BgReading>.sampleByTick(tickInterval: Minutes): Flow<Pair<BgReading, Tick>> {
    var lastTick = Tick(-1)
    val tickSizeMs: Long = tickInterval.value * 60L * 1000L

    return map { it to Tick((it.timestamp.ms / tickSizeMs).toInt()) }
        .filter { (bg, tick) ->
            if (tick != lastTick) {
                lastTick = tick
                true
            } else {
                Log.i("BG ProcessingPipeline", "Skipping entry in the same tick: $bg, tick=$tick, lastTick=$lastTick")
                false
            }
        }
}

/**
 * Downsamples the data stream by aligning readings to fixed time intervals (ticks) using a stable
 * sampling algorithm.
 *
 * It works like this:
 * - Slot index is computed by simple integer division.
 * - Slot centers are phase-shifted so the first value lies exactly
 *   in the center of its slot.
 * - Jitter is suppressed by stable slot assignment.
 * - Last 10 slot-center errors are averaged.
 * - If average absolute error exceeds threshold, phase is re-initialized
 *   around the current value.
 * - No gap filling: missing slots are simply skipped.
 * - Duplicate slots are suppressed (only first emitted value per slot).
 */
fun Flow<BgReading>.sampleByTickStable(
    tickInterval: Minutes,
    errorWindowSize: Int = 10,
    reinitThresholdMs: Long = 10_000L
): Flow<Pair<BgReading, Tick>> = flow {
    var initialized = false
    var offsetMs = 0L
    var lastTick: Tick? = null

    val tickIntervalMs = tickInterval.inMs()
    val errors = ArrayList<Long>()

    fun centerOn(timestamp: Timestamp) {
        val phase = timestamp.ms % tickIntervalMs
        offsetMs = tickIntervalMs / 2 - phase
        errors.clear()
        initialized = true
    }

    collect { bg ->
        if (!initialized && bg.timestamp.ms != 0L) {
            centerOn(bg.timestamp)
        }

        val tick = Tick(((bg.timestamp.ms + offsetMs) / tickIntervalMs).toInt())

        // Sanity check
        val lTick = lastTick
        if (lTick != null && lTick >= tick) {
            // Skip old value
            return@collect
        }

        val slotCenter = Timestamp(tick.value * tickIntervalMs + tickIntervalMs / 2 - offsetMs)
        val error = bg.timestamp - slotCenter

        errors.addLast(error)
        while (errors.size > errorWindowSize) {
            errors.removeFirst()
        }

        emit(Pair(bg, tick))
        lastTick = tick

        if (errors.size == errorWindowSize) {
            val avgError = errors.sum() / errors.size

            if (abs(avgError) > reinitThresholdMs) {
                centerOn(bg.timestamp)
            }
        }
    }
}