package de.dh.raaps.core.aps

class SavitzkyGolayFilterWin5Order2 {
    companion object {
        /**
         * Savitzky-Golay coefficients
         * Window size = 5
         * Polynomial order = 2
         *
         * [-3, 12, 17, 12, -3] / 35
         */
        val COEFFS = doubleArrayOf(
            -3.0 / 35.0,
            12.0 / 35.0,
            17.0 / 35.0,
            12.0 / 35.0,
            -3.0 / 35.0
        )

        /**
         * Calculates smoothed values.
         *
         * Number of output values = number of input values.
         * Border handling uses clamp mode.
         */
        fun filter(values: List<Double>): List<Double> {
            if (values.isEmpty()) return emptyList()

            val result = DoubleArray(values.size)

            for (targetIndex in values.indices) {
                result[targetIndex] = calculateFilteredValue(values, targetIndex)
            }

            return result.toList()
        }

        /**
         * Calculates exactly one filtered output value
         * for the requested target position.
         */
        fun calculateFilteredValue(
            values: List<Double>,
            targetIndex: Int
        ): Double {
            var sum = 0.0

            for (k in COEFFS.indices) {
                val sourceIndex = clamp(targetIndex + k - 2, 0, values.lastIndex)
                sum += values[sourceIndex] * COEFFS[k]
            }

            return sum
        }

        /**
         * Restricts a value to the inclusive range [min, max].
         */
        private fun clamp(value: Int, min: Int, max: Int): Int {
            return when {
                value < min -> min
                value > max -> max
                else -> value
            }
        }
    }
}