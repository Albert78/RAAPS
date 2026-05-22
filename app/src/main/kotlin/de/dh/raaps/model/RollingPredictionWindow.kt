package de.dh.raaps.model

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp

class RollingPredictionWindow(
    val predictionWindowHours: Int,
    val tickDuration: Minutes,
    timestamp: Timestamp
) {
    private val capacity = (predictionWindowHours * 60) / tickDuration.value.toInt()
    // Ring buffer which holds our prediction window
    private val buffer = Array(capacity) { _ -> PredictionTickState()}

    var anchorTick: Tick = Tick.invalid()
    val tickSizeMs = tickDuration.value * 60 * 1000

    init {
        init(tick(timestamp))
    }

    fun init(newAnchorTick: Tick = anchorTick) {
        anchorTick = newAnchorTick
        forEach { tick, tickState -> tickState.initializeToTick(tick) }
    }

    fun forEach(action: (Tick, PredictionTickState) -> Unit) {
        for (tick in getFirstTick()..getLastTick()) {
            tryGetTickState(tick)?.let { action(tick, it) }
        }
    }

    fun tick(timestamp: Timestamp): Tick {
        return Tick((timestamp.ms  / tickSizeMs).toInt())
    }

    fun timestamp(tick: Tick): Timestamp {
        return Timestamp(tick.value.toLong() * tickSizeMs)
    }

    fun getNowTick(): Tick {
        return tick(Timestamp.now())
    }

    fun getFirstTick() = anchorTick
    fun getLastTick() = Tick(anchorTick.value + capacity - 1)

    /**
     * Advances the buffer's timeframe to a new point in time.
     */
    fun moveWindowTo(newAnchorTick: Tick): Boolean {
        if (anchorTick == Tick.invalid()) {
            // Empty state
            anchorTick = newAnchorTick
            return true
        } else if (newAnchorTick > anchorTick) {
            // Clear the slots that have become "stale" due to time advancing
            val ticksToClear = (newAnchorTick.value - anchorTick.value).coerceAtMost(capacity)
            for (i in 0 until ticksToClear) {
                val tick = Tick(anchorTick.value + i)
                buffer[bufferIndex(tick)].initializeToTick(tick)
            }
            anchorTick = newAnchorTick
            return true
        } else {
            return false
        }
    }

    /**
     * Tries to get an entry of our state buffer.
     * Only succeeds if the given tick falls within the current prediction window.
     */
    fun tryGetTickState(tick: Tick): PredictionTickState? {
        val maxValidTick = getLastTick().value

        if (tick.value in anchorTick.value..maxValidTick) {
            return buffer[bufferIndex(tick)]
        }
        return null
    }

    private fun bufferIndex(tick: Tick): Int {
        return tick.value % capacity
    }

    /**
     * Searches the state buffer forward starting at [startTick] for the first [PredictionTickState],
     * which meets the [predicate] condition.
     * Returns `null` if there is no match in the current prediction window.
     */
    fun findForward(
        startTick: Tick = anchorTick,
        predicate: (PredictionTickState) -> Boolean
    ): PredictionTickState? {
        val maxTick = getLastTick().value
        val start = startTick.value.coerceAtLeast(anchorTick.value)

        for (v in start..maxTick) {
            val state = buffer[bufferIndex(Tick(v))]
            if (state != null && predicate(state)) {
                return state
            }
        }
        return null
    }

    /**
     * Searches the state buffer backward starting at [startTick] for the first [PredictionTickState],
     * which meets the [predicate] condition.
     * Returns `null` if there is no match in the current prediction window.
     */
    fun findBackward(
        startTick: Tick,
        predicate: (PredictionTickState) -> Boolean
    ): PredictionTickState? {
        val minTick = getFirstTick().value
        val start = startTick.value.coerceAtMost(anchorTick.value + capacity - 1)

        for (v in start downTo minTick) {
            val state = buffer[bufferIndex(Tick(v))]
            if (state != null && predicate(state)) {
                return state
            }
        }
        return null
    }

    companion object {
        val TAG = RollingPredictionWindow::class.simpleName
    }
}