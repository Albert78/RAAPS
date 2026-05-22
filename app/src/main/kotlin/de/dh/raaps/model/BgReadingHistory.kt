package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Tick

/**
 * A simplified ring buffer to store a window of BgReadings.
 * The anchorTick represents the youngest (newest) tick in the window.
 */
class BgReadingHistory(
    val historyHours: Int,
    val tickInterval: Minutes
) {
    private val capacity = historyHours * 60 / tickInterval.value

    private val buffer = arrayOfNulls<BgReading>(capacity)

    /**
     * The youngest (newest) tick currently held in the buffer.
     */
    var anchorTick: Tick = Tick.invalid()
        private set

    fun clear() {
        buffer.fill(null)
        anchorTick = Tick.invalid()
    }

    /**
     * Adds or updates a reading at the given tick.
     * If the tick is newer than the current anchorTick, the window advances.
     */
    fun add(tick: Tick, reading: BgReading) {
        if (anchorTick == Tick.invalid()) {
            anchorTick = tick
        } else if (tick > anchorTick) {
            advanceTo(tick)
        }

        // Only store if the tick is within the window [anchorTick - capacity + 1, anchorTick]
        if (tick.value > anchorTick.value - capacity) {
            buffer[bufferIndex(tick)] = reading
        }
    }

    /**
     * Advances the window's end point. Slots that the window moves over are cleared.
     */
    fun advanceTo(newAnchorTick: Tick) {
        if (anchorTick == Tick.invalid()) {
            anchorTick = newAnchorTick
            return
        }

        if (newAnchorTick > anchorTick) {
            val ticksToClear = (newAnchorTick.value - anchorTick.value).coerceAtMost(capacity)
            for (i in 1..ticksToClear) {
                buffer[bufferIndex(Tick(anchorTick.value + i))] = null
            }
            anchorTick = newAnchorTick
        }
    }

    /**
     * Returns all stored readings as a list, ordered by time (tick value).
     */
    fun toList(): List<BgReading> {
        if (anchorTick == Tick.invalid()) return emptyList()

        val result = ArrayList<BgReading>(capacity)
        val startTickValue = anchorTick.value - capacity + 1
        for (i in 0 until capacity) {
            val reading = buffer[bufferIndex(Tick(startTickValue + i))]
            if (reading != null) {
                result.add(reading)
            }
        }
        return result
    }

    private fun bufferIndex(tick: Tick): Int = tick.value % capacity
}