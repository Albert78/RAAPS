package de.dh.raaps.common.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Defines how a numeric value should be incremented or decremented.
 *
 * Example: Used in UI controls like number pickers to adjust a value by a defined step.
 */
interface SteppingStrategy {
    /**
     * Calculates the next higher value based on the [currentValue].
     */
    fun stepUp(currentValue: Double): Double

    /**
     * Calculates the next lower value based on the [currentValue].
     */
    fun stepDown(currentValue: Double): Double
}

/**
 * Defines how a numeric value should be formatted for display and which color should be applied to it.
 *
 * Example: Used to display data points where color indicates status (e.g., green for good, red for critical).
 */
interface ValueDisplayStrategy {
    /**
     * Formats the given [value] as a string for display.
     */
    fun format(value: Double): String

    /**
     * Returns the [Color] that should be used to display the given [value].
     */
    fun color(value: Double): Color
}

/**
 * A simple stepping strategy that increments or decrements by a fixed step value.
 *
 * Example:
 * ```kotlin
 * val strategy = DefaultSteppingStrategy(step = 0.5)
 * val next = strategy.stepUp(1.0) // returns 1.5
 * ```
 */
class DefaultSteppingStrategy(private val step: Double = 1.0) : SteppingStrategy {
    /**
     * Increments [currentValue] by the fixed [step] amount.
     */
    override fun stepUp(currentValue: Double): Double = currentValue + step

    /**
     * Decrements [currentValue] by the fixed [step] amount.
     */
    override fun stepDown(currentValue: Double): Double = currentValue - step
}

/**
 * A stepping strategy that snaps the value to the next or previous multiple of the given [step].
 *
 * Example:
 * ```kotlin
 * val strategy = ModuloSteppingStrategy(step = 5.0)
 * val next = strategy.stepUp(7.0) // returns 10.0
 * val prev = strategy.stepDown(7.0) // returns 5.0
 * ```
 */
class ModuloSteppingStrategy(private val step: Double = 5.0) : SteppingStrategy {
    /**
     * Snaps the [currentValue] to the next higher multiple of [step].
     */
    override fun stepUp(currentValue: Double): Double {
        return (floor(currentValue / step).toInt() + 1) * step
    }

    /**
     * Snaps the [currentValue] to the next lower multiple of [step].
     */
    override fun stepDown(currentValue: Double): Double {
        return (ceil(currentValue / step).toInt() - 1) * step
    }
}

/**
 * A highly customizable [ValueDisplayStrategy] that supports different colors for positive,
 * negative, and neutral values, as well as prefixes, suffixes, and custom labels.
 *
 * Example:
 * ```kotlin
 * val strategy = ConfigurableDisplayStrategy(
 *     positiveColor = Color.Green,
 *     negativeColor = Color.Red,
 *     positivePrefix = "+",
 *     suffix = " mg/dL",
 *     neutralLabel = "Steady"
 * )
 * ```
 */
class ConfigurableDisplayStrategy(
    private val positiveColor: Color = Color.Unspecified,
    private val negativeColor: Color = Color.Unspecified,
    private val neutralColor: Color = Color.Unspecified,
    private val positivePrefix: String = "",
    private val suffix: String = "",
    private val neutralLabel: String? = null
) : ValueDisplayStrategy {
    /**
     * Formats the [value] considering [positivePrefix], [suffix], and [neutralLabel].
     * Decimals are only shown if the value is not a whole number.
     */
    override fun format(value: Double): String {
        if (value == 0.0 && neutralLabel != null) return neutralLabel
        val prefix = if (value > 0) positivePrefix else ""
        // Use precision based on whether it has decimals
        val formattedValue = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
        return "$prefix$formattedValue$suffix"
    }

    /**
     * Returns a color based on whether the [value] is positive, negative, or zero.
     */
    override fun color(value: Double): Color {
        return when {
            value > 0 -> positiveColor
            value < 0 -> negativeColor
            else -> neutralColor
        }
    }
}

/**
 * A basic [ValueDisplayStrategy] that provides simple string conversion and a single fixed color.
 *
 * Example:
 * ```kotlin
 * val strategy = DefaultValueDisplayStrategy(color = Color.Black)
 * val text = strategy.format(10.0) // returns "10"
 * ```
 */
class DefaultValueDisplayStrategy(private val color: Color = Color.Unspecified) : ValueDisplayStrategy {
    /**
     * Returns a simple string representation of the [value].
     */
    override fun format(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    /**
     * Returns the fixed [color] provided during initialization.
     */
    override fun color(value: Double): Color = color
}