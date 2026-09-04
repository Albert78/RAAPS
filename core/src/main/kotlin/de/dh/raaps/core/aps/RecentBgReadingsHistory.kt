package de.dh.raaps.core.aps

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

        return if (count == 0) null else BgValue.fromMgDl(sum / count)
    }

    /**
     * Returns a filtered BG value by calculating the Parametrized Time-Weighted Moving Average.
     * Newer readings have higher weight (1.0), and older readings have lower weight (1.0 - decayFactor * ageRatio).
     * If there are no valid values, returns `BgValue.INVALID`.
     */
    fun calculatePTWMA(
        decayFactor: Double
    ): BgValue {
        val validReadings = (0 until size)
            .mapNotNull { buffer[it] }
            .filter { it.sampleKind == BgSampleKind.Value }

        if (validReadings.isEmpty()) {
            return BgValue.INVALID
        }
        if (validReadings.size == 1) {
            return validReadings.first().value
        }

        val latestTimestamp = validReadings.last().timestamp.ms
        val oldestTimestamp = validReadings.first().timestamp.ms
        val windowMs = (latestTimestamp - oldestTimestamp).toDouble()

        var weightedSum = 0.0
        var weightTotal = 0.0

        for (reading in validReadings) {
            val ageMs = (latestTimestamp - reading.timestamp.ms).toDouble().coerceAtLeast(0.0)
            val ageRatio = if (windowMs > 0.0) (ageMs / windowMs).coerceIn(0.0, 1.0) else 0.0
            // Weight decreases linearly from 1.0 (newest reading) down to (1.0 - decayFactor) (oldest reading)
            val weight = (1.0 - (decayFactor * ageRatio)).coerceAtLeast(0.01)
            weightedSum += reading.value.mgdl * weight
            weightTotal += weight
        }

        if (weightTotal <= 0.0) {
            return BgValue.INVALID
        }
        return BgValue.fromMgDl(weightedSum / weightTotal)
    }

    fun last(): BgReading? {
        return if (size > 0) buffer[size - 1] else null
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