package de.dh.raaps.common.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor

interface SteppingStrategy {
    fun stepUp(currentValue: Double): Double
    fun stepDown(currentValue: Double): Double
}

interface ValueDisplayStrategy {
    fun format(value: Double): String
    fun color(value: Double): Color
}

class DefaultSteppingStrategy(private val step: Double = 1.0) : SteppingStrategy {
    override fun stepUp(currentValue: Double): Double = currentValue + step
    override fun stepDown(currentValue: Double): Double = currentValue - step
}

class ModuloSteppingStrategy(private val step: Double = 5.0) : SteppingStrategy {
    override fun stepUp(currentValue: Double): Double {
        return (floor(currentValue / step).toInt() + 1) * step
    }

    override fun stepDown(currentValue: Double): Double {
        return (ceil(currentValue / step).toInt() - 1) * step
    }
}

class ConfigurableDisplayStrategy(
    private val positiveColor: Color = Color.Unspecified,
    private val negativeColor: Color = Color.Unspecified,
    private val neutralColor: Color = Color.Unspecified,
    private val positivePrefix: String = "",
    private val suffix: String = "",
    private val neutralLabel: String? = null
) : ValueDisplayStrategy {
    override fun format(value: Double): String {
        if (value == 0.0 && neutralLabel != null) return neutralLabel
        val prefix = if (value > 0) positivePrefix else ""
        // Use precision based on whether it has decimals
        val formattedValue = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
        return "$prefix$formattedValue$suffix"
    }

    override fun color(value: Double): Color {
        return when {
            value > 0 -> positiveColor
            value < 0 -> negativeColor
            else -> neutralColor
        }
    }
}

class DefaultValueDisplayStrategy(private val color: Color = Color.Unspecified) : ValueDisplayStrategy {
    override fun format(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
    override fun color(value: Double): Color = color
}