package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick

/**
 * Provides a sampled view of the blood glucose history, aligned to discrete time ticks.
 * The buffer size is determined by the history size and the tick interval.
 *
 * To create the sample buffer, call [sampleAvgValues].
 * The class does not automatically re-calculate the sample buffer.
 * We intentionally do not implement the [sampleAvgValues] function as factory function in a companion
 * object to avoid creating another object on the heap. With this architecture, we can just re-use
 * this sample buffer instance for each tick calculation.
 */
class SampledBgReadings(
    private val timeline: ApsTimeline,
    private val history: RecentBgReadingsHistory
) {
    private val capacity = history.historySize.value / timeline.tickDuration.value
    private val buffer = ShortArray(capacity)

    /**
     * Samples the entire history.
     * Index 0 corresponds to the current tick (now),
     * index 1 to (now - 1 tick), and so on.
     */
    fun sampleAvgValues() {
        val nowTick = timeline.getNowTick()
        for (i in 0 until capacity) {
            val tick = nowTick - i
            val avg = history.avgBgValue(
                start = timeline.timestamp(tick),
                startInclusive = true,
                end = timeline.timestamp(tick + 1),
                endInclusive = false
            )
            buffer[i] = avg?.mgdl ?: 0
        }
    }

    /**
     * Returns the sampled value at the given tick, if it falls within the sampled history.
     */
    fun getAt(tick: Tick): BgValue? {
        val nowTick = timeline.getNowTick()
        val index = nowTick.value - tick.value

        if (index in 0 until capacity) {
            val mgdl = buffer[index]
            return if (mgdl > 0) BgValue.fromMgDl(mgdl) else null
        }
        return null
    }
}