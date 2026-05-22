package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED

enum class BgSampleKind {
    Value, High, Low, Invalid
}

/**
 * Represents an interpreted bg reading from a blood glucose provider.
 */
data class BgReading(
    var id: Long = ID_UNDEFINED,
    val value: BgValue,
    val sampleKind: BgSampleKind,
    override val timestamp: Timestamp
): HistoricalValue