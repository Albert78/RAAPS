package de.dh.raaps.common.model.data

/**
 * A memory-efficient representation of a discrete point in time on a fixed grid.
 *
 * A Tick represents the number of elapsed intervals (of a specific duration)
 * since the Unix Epoch (January 1, 1970). This allows for easy comparison
 * and alignment of data points to a consistent time-grid, regardless of
 * their original high-resolution timestamps.
 */
@JvmInline
value class Tick(val value: Int) : Comparable<Tick> {
    operator fun plus(numTicks: Int): Tick = Tick(value + numTicks)
    operator fun minus(numTicks: Int): Tick = Tick(value - numTicks)
    operator fun rangeTo(other: Tick): TickRange = TickRange(this, other)

    override fun compareTo(other: Tick): Int = value.compareTo(other.value)

    companion object {
        fun invalid(): Tick {
            return Tick(-1)
        }
    }
}

/**
 * A range of [Tick]s.
 */
class TickRange(
    override val start: Tick,
    override val endInclusive: Tick
) : Iterable<Tick>, ClosedRange<Tick> {
    override fun iterator(): Iterator<Tick> = TickIterator(start, endInclusive)
}

/**
 * Iterator for [TickRange].
 */
class TickIterator(start: Tick, val endInclusive: Tick) : Iterator<Tick> {
    private var nextValue = start.value

    override fun hasNext(): Boolean = nextValue <= endInclusive.value

    override fun next(): Tick {
        if (!hasNext()) throw NoSuchElementException()
        return Tick(nextValue++)
    }
}