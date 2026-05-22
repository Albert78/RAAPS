package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import java.util.NavigableMap
import java.util.TreeMap

/**
 * Small facade for a [TreeMap] which automatically prunes old items.
 */
class BgReadingHistory(
    val historySize: Minutes
) {
    private val items = TreeMap<Timestamp, BgReading>()

    /**
     * Adds a reading to the history. Prunes the history, if needed.
     */
    fun add(reading: BgReading) {
        prune()
        items[reading.timestamp] = reading
    }

    fun setAll(readingsHistory: List<BgReading>) {
        prune()
        for (reading in readingsHistory) {
            items[reading.timestamp] = reading
        }
    }

    fun prune() {
        val pruneThreshold = Timestamp.now().minus(historySize)
        items.headMap(pruneThreshold, false).clear() // Remove complete range of old items
    }

    fun items(): NavigableMap<Timestamp, BgReading> {
        prune()
        return items
    }

    fun avgBgValue(start: Timestamp, startInclusive: Boolean, end: Timestamp, endTickInclusive: Boolean): BgValue? {
        val readings = items()
            .subMap(start, startInclusive, end, endTickInclusive)
            .values

        return if (readings.isEmpty()) null else BgValue.fromMgDl(
            readings
                .sumOf { if (it.sampleKind == BgSampleKind.Value) it.value.mgdl.toInt() else 0 } / readings.size
        )
    }
}