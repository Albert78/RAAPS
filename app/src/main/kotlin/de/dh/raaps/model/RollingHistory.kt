package de.dh.raaps.model

import android.util.Log
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timestamp

data class ApsHistorySnapshot(
    val ticks: List<TickState?>,
    val tickInterval: Minutes
)

class RollingHistory(
    val historyHours: Int = 10,
    val tickDuration: Minutes = Minutes(5)
) {
    private val capacity = (historyHours * 60) / tickDuration.value.toInt()
    // Ring buffer which holds our history window
    private val buffer = arrayOfNulls<TickState>(capacity)

    // The "Present": All data in the buffer is relative to this tick
    var anchorTick: Tick = Tick.invalid()

    fun tick(timestamp: Timestamp): Tick {
        val tickSizeMs = tickDuration.value * 60 * 1000

        return Tick((timestamp.ms  / tickSizeMs).toInt())
    }

    fun getNowTick(): Tick {
        return tick(Timestamp.now())
    }

    fun getFirstTick() = Tick(anchorTick.value - capacity + 1)

    /**
     * Advances the history's timeframe to a new point in time.
     * Use this to move the "window" forward, e.g., every 5 minutes or on new data.
     */
    fun advanceTo(newAnchorTick: Tick): Boolean {
        if (anchorTick == Tick.invalid()) {
            // Empty state
            anchorTick = newAnchorTick
            return true
        } else if (newAnchorTick > anchorTick) {
            // Clear the slots that have become "stale" due to time advancing
            val ticksToClear = (newAnchorTick.value - anchorTick.value).coerceAtMost(capacity)
            for (i in 1..ticksToClear) {
                val tick = Tick(anchorTick.value + i)
                buffer[bufferIndex(tick)] = null
            }
            anchorTick = newAnchorTick
            return true
        } else {
            return false
        }
    }

    /**
     * Tries to get an entry of our state history.
     * Only succeeds if the given tick falls within the current history window.
     */
    fun tryGetApsTickState(tick: Tick): TickState? {
        val minValidTick = getFirstTick().value

        if (tick.value in minValidTick..anchorTick.value) {
            return buffer[bufferIndex(tick)]
        }
        return null
    }

    fun getOrCreateTickState(tick: Tick): TickState? {
        if (tick > anchorTick) {
            advanceTo(tick)
        }
        if (tick.value !in anchorTick.value - capacity + 1..anchorTick.value) {
            return null
        }
        val result = TickState.empty(tick)
        buffer[bufferIndex(tick)] = result
        return result
    }

    private fun bufferIndex(tick: Tick): Int {
        return tick.value % capacity
    }

    fun replaceBufferTickStates(tickStates: List<TickState>) {
        // Clear buffer
        for (i in 0..<capacity) {
            buffer[i] = null
        }

        // Fill given tick states into buffer
        val firstTick = getFirstTick()
        for (loadedState in tickStates) {
            if (loadedState.tick !in firstTick..anchorTick) {
                Log.w(TAG, "Trying to fill tick state into history with invalid tick number: Tick = ${loadedState.tick.value}, FirstTick = ${firstTick.value}, AnchorTick = ${anchorTick.value}")
                continue
            }
            val index = bufferIndex(loadedState.tick)
            if (index in 0..<capacity) {
                buffer[index] = loadedState
            }
        }

        // Slots not covered by the provided values are intentionally left null (e.g. with a fresh DB)
    }

    /**
     * Synchronous access to a chronological snapshot of the current history.
     * The list is ordered from oldest (index 0) to newest.
     */
    fun getSnapshot(): ApsHistorySnapshot {
        val currentAnchor = anchorTick
        if (currentAnchor == Tick.invalid()) {
            return ApsHistorySnapshot(List(capacity) { null }, tickDuration)
        }

        val result = List(capacity) { i ->
            val tickValue = currentAnchor.value - capacity + 1 + i
            if (tickValue >= 0) {
                buffer[bufferIndex(Tick(tickValue))]
            } else {
                null
            }
        }
        return ApsHistorySnapshot(result, tickDuration)
    }

    /**
     * Searches the history backward starting at [startTick] for the first [TickState],
     * which meets the [predicate] condition.
     * Returns `null` if there is no match in the current history window.
     */
    fun findBackward(
        startTick: Tick = anchorTick,
        predicate: (TickState) -> Boolean
    ): TickState? {
        val minTick = getFirstTick().value
        val start = startTick.value.coerceAtMost(anchorTick.value)

        for (v in start downTo minTick) {
            val state = buffer[bufferIndex(Tick(v))]
            if (state != null && predicate(state)) {
                return state
            }
        }
        return null
    }

    companion object {
        val TAG = RollingHistory::class.simpleName
    }
}