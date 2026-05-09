package de.dh.raaps.common.model.data

enum class BgSampleKind {
    Value, High, Low, Invalid
}

/**
 * Represents an interpreted bg reading from a blood glucose provider.
 */
data class BgReading(
    val value: BgValue,
    val sampleKind: BgSampleKind,
    override val timestamp: Timestamp
): HistoricalValue