package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp

/**
 * Stores a limited history of blood glucose readings.
 * Optimized for performance by using a fixed-size array and binary search.
 * Maintains sorted order by timestamp internally.
 */
class RecentBgReadingsHistory(
    val historySize: Minutes
) {
    private val capacity = historySize.value.toInt()
    private val buffer = arrayOfNulls<BgReading>(capacity)
    var size = 0
        private set

    fun clear() {
        size = 0
        buffer.fill(null)
    }

    /**
     * Adds a reading at the correct sorted position.
     * If the capacity is reached, the oldest entries are removed to make room.
     */
    fun add(reading: BgReading) {
        val ts = reading.timestamp.ms

        // Use binary search to find the existing position or the insertion point
        var index = findIndex(ts)

        if (index >= 0) {
            // Already exists, just update the reference
            buffer[index] = reading
            return
        }

        // Not found, index is (-(insertion point) - 1)
        index = -(index + 1)

        if (size >= capacity) {
            // Buffer full. If the new entry is older than our oldest, we ignore it.
            if (index == 0) return

            // Otherwise, make room by shifting everything left (effectively pruning the oldest)
            System.arraycopy(buffer, 1, buffer, 0, capacity - 1)
            size--

            // Re-find the insertion index in the shifted array
            index = -(findIndex(ts) + 1)
        }

        // Shift elements to the right to insert at the correct position
        if (index < size) {
            System.arraycopy(buffer, index, buffer, index + 1, size - index)
        }

        buffer[index] = reading
        size++
    }

    /**
     * Adds multiple readings efficiently.
     */
    fun setAll(newReadings: List<BgReading>) {
        clear()
        for (reading in newReadings) {
            add(reading)
        }
    }

    /**
     * Efficiently removes all readings older than the threshold in a single step.
     */
    fun prune(threshold: Timestamp) {
        val thresholdMs = threshold.ms
        var firstValidIdx = findIndex(thresholdMs)

        if (firstValidIdx < 0) {
            firstValidIdx = -(firstValidIdx + 1)
        }

        if (firstValidIdx > 0) {
            val remaining = size - firstValidIdx
            if (remaining > 0) {
                System.arraycopy(buffer, firstValidIdx, buffer, 0, remaining)
            }
            // Clear out old references for garbage collection
            for (i in remaining until size) {
                buffer[i] = null
            }
            size = remaining
        }
    }

    /**
     * Calculates the average blood glucose value within a given time range.
     * Uses binary search to find the range in O(log n).
     */
    fun avgBgValue(
        start: Timestamp,
        startInclusive: Boolean,
        end: Timestamp,
        endInclusive: Boolean
    ): BgValue? {
        if (size == 0) return null

        var startIdx = findIndex(start.ms)
        if (startIdx < 0) {
            startIdx = -(startIdx + 1)
        } else if (!startInclusive) {
            startIdx++
        }

        var endIdx = findIndex(end.ms)
        if (endIdx < 0) {
            endIdx = -(endIdx + 1) - 1
        } else if (!endInclusive) {
            endIdx--
        }

        if (startIdx > endIdx || startIdx >= size || endIdx < 0) return null

        var sum = 0.0
        var count = 0
        for (i in startIdx..endIdx) {
            val reading = buffer[i] ?: continue
            if (reading.sampleKind == BgSampleKind.Value) {
                sum += reading.value.mgdl
                count++
            }
        }

        return if (count == 0) null else BgValue.fromMgDl((sum / count).toInt())
    }

    /**
     * Returns a filtered BG value by calculating the Parametrized Time-Weighted Moving Average.
     * If there aren't enough valid values in the given window, this method returns `BgValue(0)`, else it
     * returns the PTWMA-smoothed value.
     */
    fun calculatePTWMA(
        weightSlope: Double
    ): BgValue {
        var weightedSum = 0.0
        var weightTotal = 0.0

        if (size == 1) {
            return buffer[0]!!.value
        } else if (size > 0) {
            val windowStart = buffer[0]!!.timestamp
            val windowMs = buffer[size - 1]!!.timestamp - windowStart

            for (i in 0 until size) {
                val reading = buffer[i]
                if (reading != null && reading.sampleKind != BgSampleKind.Invalid) {
                    val weight = weightSlope * (reading.timestamp.ms - windowStart.ms) + (1.0 - weightSlope) * windowMs
                    weightedSum += reading.value.mgdl * weight
                    weightTotal += weight
                }
            }
        }

        if (weightTotal <= 0) {
            return BgValue.INVALID
        }
        return BgValue.fromMgDl((weightedSum / weightTotal).toInt())
    }

    /**
     * Returns all stored readings as a list.
     */
    fun toList(): List<BgReading> {
        val result = ArrayList<BgReading>(size)
        for (i in 0 until size) {
            buffer[i]?.let { result.add(it) }
        }
        return result
    }

    /**
     * Returns all stored readings which contain valid values as a list.
     */
    fun getValidValues(): List<BgValue> {
        val result = ArrayList<BgValue>(size)
        for (i in 0 until size) {
            buffer[i]?.let {
                if (it.sampleKind == BgSampleKind.Value) result.add(it.value)
            }
        }
        return result
    }

    private fun findIndex(timestampMs: Long): Int {
        var low = 0
        var high = size - 1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midVal = buffer[mid]?.timestamp?.ms ?: 0L

            if (midVal < timestampMs)
                low = mid + 1
            else if (midVal > timestampMs)
                high = mid - 1
            else
                return mid // key found
        }
        return -(low + 1) // key not found
    }
}