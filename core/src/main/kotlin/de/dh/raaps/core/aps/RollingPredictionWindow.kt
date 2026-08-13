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
        for (tick in anchorTick..getLastTick()) {
            tryGetTickState(tick)?.initializeToTick(tick)
        }
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
            // Clear the slots that have become "stale" due to time advancing and prepare them for the future
            val ticksToAdvance = (newAnchorTick.value - anchorTick.value).coerceAtMost(capacity)
            for (i in 0 until ticksToAdvance) {
                val oldTickValue = anchorTick.value + i
                val newFutureTick = Tick(oldTickValue + capacity)
                buffer[bufferIndex(newFutureTick)].initializeToTick(newFutureTick)
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

    suspend fun forEachS(from: Tick = getFirstTick(), to: Tick = getLastTick(), action: suspend (Tick, PredictionTickState) -> Unit) {
        for (tick in from..to) {
            tryGetTickState(tick)?.let { action(tick, it) }
        }
    }

    /**
     * Searches the state buffer forward starting at [startTick] for the first [PredictionTickState],
     * which meets the [predicate] condition.
     * Returns `null` if there is no match in the current prediction window.
     */
    /**
     * Searches the state buffer forward starting at [startTick] for the first [PredictionTickState],
     * which meets the [predicate] condition.
     * Returns `null` if there is no match in the current prediction window.
     */
    suspend fun findForwardS(
        startTick: Tick = anchorTick,
        endTick: Tick? = null,
        predicate: suspend (PredictionTickState) -> Boolean
    ): PredictionTickState? {
        val last = getLastTick().value
        val start = startTick.value.coerceAtLeast(anchorTick.value)
        val end = (endTick?.value ?: last).coerceAtMost(last)

        for (v in start..end) {
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
