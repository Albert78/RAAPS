package de.dh.raaps.common.model.data

import java.util.Locale
import kotlin.math.roundToInt

private const val MMOL_TO_MGDL = 18.0182

/**
 * A memory-efficient, type-safe representation of a blood glucose value.
 */
@JvmInline
value class BgValue(val scaled: UShort): Comparable<BgValue> {
    val mgdl: Double
        get() = scaled.toInt() / 100.0

    val mgdlInt: Int
        get() = (scaled.toInt() + 50) / 100

    val mmol: Double
        get() = (mgdl / MMOL_TO_MGDL * 10.0).roundToInt() / 10.0

    override fun compareTo(other: BgValue): Int = scaled.compareTo(other.scaled)
    operator fun minus(other: BgValue): BgDelta = BgDelta.fromMgDlScaled(scaled.toInt() - other.scaled.toInt())
    operator fun minus(other: BgDelta): BgValue = fromMgDlScaled(scaled.toInt() - other.scaled.toInt())
    operator fun plus(other: BgDelta): BgValue = fromMgDlScaled(scaled.toInt() + other.scaled.toInt())
    operator fun div(other: Double): BgValue = fromMgDl(mgdl / other)

    fun isValid(): Boolean = scaled > 0u
    fun isInvalid(): Boolean = !isValid()

    fun toString(glucoseUnit: GlucoseUnit) =
        when (glucoseUnit) {
            GlucoseUnit.MG_DL -> mgdlInt.toString()
            GlucoseUnit.MMOL -> String.format(Locale.getDefault(), "%.1f", mmol)
        }

    override fun toString(): String {
        return toString(GlucoseUnit.MG_DL)
    }

    companion object {
        val INVALID = BgValue(0u)

        fun fromMgDl(value: Short): BgValue = fromMgDl(value.toInt())

        fun fromMgDl(value: Int): BgValue {
            return BgValue((value * 100).coerceIn(0, UShort.MAX_VALUE.toInt()).toUShort())
        }

        fun fromMgDl(value: Double): BgValue {
            return BgValue((value * 100).roundToInt().coerceIn(0, UShort.MAX_VALUE.toInt()).toUShort())
        }

        fun fromMgDlScaled(scaled: Int): BgValue {
            return BgValue(scaled.coerceIn(0, UShort.MAX_VALUE.toInt()).toUShort())
        }

        fun fromMmol(value: Double): BgValue {
            return fromMgDl(value * MMOL_TO_MGDL)
        }
    }
}