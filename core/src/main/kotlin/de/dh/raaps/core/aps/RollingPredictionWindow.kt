package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timeline

class RollingPredictionWindow(
    val predictionWindowHours: Int,
    val timeline: Timeline,
    anchorTick: Tick
) {
    private val capacity = (predictionWindowHours * 60) / timeline.tickDuration.value.toInt()
    // Ring buffer which holds our prediction window
    private val buffer = Array(capacity) { _ -> PredictionTickState() }

    var anchorTick: Tick = Tick.invalid()

    init {
        init(anchorTick)
    }

    fun init(newAnchorTick: Tick = anchorTick) {
        anchorTick = newAnchorTick
        forEach { tick, tickState -> tickState.initializeToTick(tick) }
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

    fun forEach(from: Tick = getFirstTick(), to: Tick = getLastTick(), action: (Tick, PredictionTickState) -> Unit) {
        for (tick in from..to) {
            tryGetTickState(tick)?.let { action(tick, it) }
        }
    }

    suspend fun forEachS(action: suspend (Tick, PredictionTickState) -> Unit) {
        for (tick in getFirstTick()..getLastTick()) {
            tryGetTickState(tick)?.let { action(tick, it) }
        }
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
            if (predicate(state)) {
                return state
            }
        }
        return null
    }

    suspend fun findForwardS(
        startTick: Tick = anchorTick,
        predicate: suspend (PredictionTickState) -> Boolean
    ): PredictionTickState? {
        val maxTick = getLastTick().value
        val start = startTick.value.coerceAtLeast(anchorTick.value)

        for (v in start..maxTick) {
            val state = buffer[bufferIndex(Tick(v))]
            if (predicate(state)) {
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
            if (predicate(state)) {
                return state
            }
        }
        return null
    }

    suspend fun findBackwardS(
        startTick: Tick,
        predicate: suspend (PredictionTickState) -> Boolean
    ): PredictionTickState? {
        val minTick = getFirstTick().value
        val start = startTick.value.coerceAtMost(anchorTick.value + capacity - 1)

        for (v in start downTo minTick) {
            val state = buffer[bufferIndex(Tick(v))]
            if (predicate(state)) {
                return state
            }
        }
        return null
    }

    companion object {
        val TAG = RollingPredictionWindow::class.simpleName
    }
}