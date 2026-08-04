package de.dh.raaps.common.model.data

import java.util.Locale
import kotlin.math.roundToInt

private const val MMOL_TO_MGDL = 18.0182

/**
 * A memory-efficient, type-safe representation of a blood glucose value.
 */
@JvmInline
value class BgValue(val mgdl: Short): Comparable<BgValue> {
    val mmol: Double
        get() = (mgdl / MMOL_TO_MGDL * 10.0).roundToInt() / 10.0

    override fun compareTo(other: BgValue): Int = mgdl.compareTo(other.mgdl)
    operator fun minus(other: BgValue): BgDelta = BgDelta.fromMgDl(mgdl - other.mgdl)
    operator fun minus(other: BgDelta): BgValue = fromMgDl(mgdl - other.mgdl)
    operator fun plus(other: BgDelta): BgValue = fromMgDl(mgdl + other.mgdl)
    operator fun div(other: Double): BgValue = fromMgDl((mgdl / other).toInt())

    fun toString(glucoseUnit: GlucoseUnit) =
        when (glucoseUnit) {
            GlucoseUnit.MG_DL -> mgdl.toString()
            GlucoseUnit.MMOL -> String.format(Locale.getDefault(), "%.1f", mmol)
        }

    fun isValid(): Boolean = mgdl > 0
    fun isInvalid(): Boolean = mgdl == 0.toShort()

    companion object {
        val INVALID = BgValue(0)

        fun fromMgDl(value: Short): BgValue {
            return BgValue(value)
        }

        fun fromMgDl(value: Int): BgValue {
            return BgValue(value.toShort())
        }

        fun fromMmol(value: Double): BgValue {
            return fromMgDl((value * MMOL_TO_MGDL).toInt())
        }
    }
}