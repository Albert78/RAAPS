package de.dh.raaps.common.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor

interface SteppingStrategy {
    fun stepUp(currentValue: Int): Int
    fun stepDown(currentValue: Int): Int
}

interface ValueDisplayStrategy {
    fun format(value: Int): String
    fun color(value: Int): Color
}

class DefaultSteppingStrategy(private val step: Int = 1) : SteppingStrategy {
    override fun stepUp(currentValue: Int): Int = currentValue + step
    override fun stepDown(currentValue: Int): Int = currentValue - step
}

class ModuloSteppingStrategy(private val step: Int = 5) : SteppingStrategy {
    override fun stepUp(currentValue: Int): Int {
        return (floor(currentValue / step.toDouble()).toInt() + 1) * step
    }

    override fun stepDown(currentValue: Int): Int {
        return (ceil(currentValue / step.toDouble()).toInt() - 1) * step
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
    override fun format(value: Int): String {
        if (value == 0 && neutralLabel != null) return neutralLabel
        val prefix = if (value > 0) positivePrefix else ""
        return "$prefix$value$suffix"
    }

    override fun color(value: Int): Color {
        return when {
            value > 0 -> positiveColor
            value < 0 -> negativeColor
            else -> neutralColor
        }
    }
}

class DefaultValueDisplayStrategy(private val color: Color = Color.Unspecified) : ValueDisplayStrategy {
    override fun format(value: Int): String = value.toString()
    override fun color(value: Int): Color = color
}