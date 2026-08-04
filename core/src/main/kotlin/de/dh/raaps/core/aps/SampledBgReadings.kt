package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.Timeline

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
    private val timeline: Timeline,
    private val history: RecentBgReadingsHistory
) {
    private val capacity = history.historySize.value / timeline.tickDuration.value
    private val buffer = ShortArray(capacity)
    var recentTick: Tick = Tick.invalid()
        private set

    /**
     * Samples the entire history.
     * Index 0 corresponds to the current tick (now),
     * index 1 to (now - 1 tick), and so on.
     */
    fun sampleAvgValues() {
        recentTick = timeline.getNowTick()
        for (i in 0 until capacity) {
            val tick = recentTick - i
            val avg = history.avgBgValue(
                start = timeline.timestamp(tick),
                startInclusive = true,
                end = timeline.timestamp(tick + 1),
                endInclusive = false
            )
            buffer[i] = avg?.mgdl ?: 0
        }
    }

    fun calculatePTWMA(decayFactor: Double): BgValue {
        return history.calculatePTWMA(decayFactor)
    }

    fun lastReading(): BgReading? {
        return history.last()
    }

    fun calculateSavitzkyGolayEndBorder3(): BgValue {
        val current0 = buffer[0] // Last value
        val current1 = buffer[1] // Second to last value
        val current2 = buffer[2] // Third to last value
        if (current0 == 0.toShort() || current1 == 0.toShort() || current2 == 0.toShort()) return BgValue.INVALID

        val coeffs = SavitzkyGolayFilterWin5Order2.COEFFS
        // We want the smoothed value for the LAST point, so we cannot apply all 5 coeffs.
        // -> Border treatment for the filter: Center the coeffs at the last entry in the window
        // (at this time we need the smoothed value) and use the last window entry (the most current one)
        // for the last three coeffs. This border treatment seems to be a good compromise.
        val sum = current2 * coeffs[0] + // Center the coeffs at the 3rd value from behind
                current1 * coeffs[1] +
                current0 * (coeffs[2] + coeffs[3] + coeffs[4]) // Use the last entry for the right part of the filter coeffs
        return BgValue.fromMgDl(sum.toInt())
    }

    /**
     * Returns the sampled value at the given tick, if it falls within the sampled history,
     * else `BgValue.INVALID`.
     */
    fun getAt(tick: Tick): BgValue {
        val index = recentTick.value - tick.value

        if (index in 0 until capacity) {
            val mgdl = buffer[index]
            return if (mgdl > 0) BgValue.fromMgDl(mgdl) else BgValue.INVALID
        }
        return BgValue.INVALID
    }
}