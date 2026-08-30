package de.dh.raaps.common.model.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MMOL_TO_MGDL = 18.0182

/**
 * A memory-efficient, type-safe representation of a blood glucose value.
 */
@JvmInline
value class BgDelta(val scaled: Short): Comparable<BgDelta> {
    val mgdl: Double
        get() = scaled.toInt() / 100.0

    val mgdlInt: Int
        get() = (if (scaled >= 0) scaled + 50 else scaled - 50) / 100

    val mmol: Double
        get() = (mgdl / MMOL_TO_MGDL * 10.0).roundToInt() / 10.0

    val abs: BgDelta
        get() = BgDelta(abs(scaled.toInt()).toShort())

    override operator fun compareTo(other: BgDelta): Int = scaled.compareTo(other.scaled)
    operator fun unaryMinus(): BgDelta = fromMgDlScaled(-scaled.toInt())
    operator fun minus(other: BgDelta): BgDelta = fromMgDlScaled(scaled.toInt() - other.scaled.toInt())
    operator fun plus(other: BgDelta): BgDelta = fromMgDlScaled(scaled.toInt() + other.scaled.toInt())
    operator fun times(other: Double): BgDelta = fromMgDl(mgdl * other)
    operator fun div(other: Double): BgDelta = fromMgDl(mgdl / other)
    operator fun div(other: Int): BgDelta = fromMgDl(mgdl / other.toDouble())
    operator fun div(other: BgDelta): Double = scaled.toDouble() / other.scaled.toDouble()

    fun toString(glucoseUnit: GlucoseUnit) =
        when (glucoseUnit) {
            GlucoseUnit.MG_DL -> mgdlInt.toString()
            GlucoseUnit.MMOL -> String.format(Locale.getDefault(), "%.1f", mmol)
        }

    fun toDiff(glucoseUnit: GlucoseUnit): String {
        if (scaled == 0.toShort()) {
            return "\u00B10" // Plus/minus 0
        }
        return when (glucoseUnit) {
            GlucoseUnit.MG_DL -> String.format(Locale.getDefault(), "%+d", mgdlInt)
            GlucoseUnit.MMOL -> String.format(Locale.getDefault(), "%+.1f", mmol)
        }
    }

    companion object {
        val ZERO: BgDelta = BgDelta.fromMgDl(0)

        fun fromMgDl(value: Short): BgDelta = fromMgDl(value.toInt())

        fun fromMgDl(value: Int): BgDelta {
            return BgDelta((value * 100).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }

        fun fromMgDl(value: Double): BgDelta {
            return BgDelta((value * 100).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }

        fun fromMgDlScaled(scaled: Int): BgDelta {
            return BgDelta(scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }

        fun fromMmol(value: Double): BgDelta {
            return fromMgDl(value * MMOL_TO_MGDL)
        }
    }
}

operator fun Double.times(delta: BgDelta) = delta * this