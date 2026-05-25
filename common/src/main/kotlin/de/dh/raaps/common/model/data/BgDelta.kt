package de.dh.raaps.common.model.data

import java.util.Locale
import kotlin.math.roundToInt

private const val MMOL_TO_MGDL = 18.0182

/**
 * A memory-efficient, type-safe representation of a blood glucose value.
 */
@JvmInline
value class BgDelta(val mgdl: Short): Comparable<BgDelta> {
    val mmol: Double
        get() = (mgdl / MMOL_TO_MGDL * 10.0).roundToInt() / 10.0

    override fun compareTo(other: BgDelta): Int = mgdl.compareTo(other.mgdl)
    operator fun minus(other: BgDelta): BgDelta = fromMgDl(mgdl - other.mgdl)
    operator fun plus(other: BgDelta): BgDelta = fromMgDl(mgdl + other.mgdl)
    operator fun times(other: Double): BgDelta = fromMgDl((mgdl * other).toInt())
    operator fun div(other: BgDelta): Double = mgdl.toDouble() / other.mgdl.toDouble()

    fun toString(glucoseUnit: GlucoseUnit) =
        when (glucoseUnit) {
            GlucoseUnit.MG_DL -> mgdl.toString()
            GlucoseUnit.MMOL -> String.format(Locale.getDefault(), "%.1f", mmol)
        }

    fun toDiff(glucoseUnit: GlucoseUnit): String {
        if (mgdl == 0.toShort()) {
            return "\u00B10" // Plus/minus 0
        }
        return when (glucoseUnit) {
            GlucoseUnit.MG_DL -> String.format(Locale.getDefault(), "%+d", mgdl)
            GlucoseUnit.MMOL -> String.format(Locale.getDefault(), "%+.1f", mmol)
        }
    }

    companion object {
        fun fromMgDl(value: Short): BgDelta {
            return BgDelta(value)
        }

        fun fromMgDl(value: Int): BgDelta {
            return BgDelta(value.toShort())
        }

        fun fromMmol(value: Double): BgDelta {
            return fromMgDl((value * MMOL_TO_MGDL).toInt())
        }
    }
}

operator fun Double.times(delta: BgDelta) = delta * this