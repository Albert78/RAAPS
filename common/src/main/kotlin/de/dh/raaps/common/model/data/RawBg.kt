package de.dh.raaps.common.model.data

/**
 * Represents a raw blood glucose reading in a form which is often provided by CGM systems;
 * The value represents either a valid value, or a high value (e.g. if it's 401) or a low value
 * (e.g. if it's 39) or an invalid value.
 * The interpretation of this raw value will convert it to a [BgReading] and must be done before we
 * can work with it in the APS algorithms.
 */
data class RawBg(
    val value: BgValue,
    override val timestamp: Timestamp
): HistoricalValue