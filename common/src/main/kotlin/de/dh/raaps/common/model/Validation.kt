package de.dh.raaps.common.model

import kotlin.math.abs

/**
 * Validation utilities for metabolic and therapy parameters.
 */

@Suppress("NOTHING_TO_INLINE")
inline fun validateIsf(value: Double) {
    require(value in ISF_MIN..ISF_MAX) { "ISF must be in range [$ISF_MIN, $ISF_MAX], but was $value" }
}

@Suppress("NOTHING_TO_INLINE")
inline fun validateCr(value: Double) {
    require(value in CR_MIN..CR_MAX) { "CR must be in range [$CR_MIN, $CR_MAX], but was $value" }
}

@Suppress("NOTHING_TO_INLINE")
inline fun validateInsulin(value: Double) {
    require(abs(value) <= BOLUS_MAX) { "Insulin amount must be <= $BOLUS_MAX, but was $value" }
}

@Suppress("NOTHING_TO_INLINE")
inline fun validateCarbs(value: Double) {
    require(abs(value) <= CARBS_GRAMS_MAX) { "Carbs must be <= $CARBS_GRAMS_MAX, but was $value" }
}

@Suppress("NOTHING_TO_INLINE")
inline fun validateBgDelta(value: Double) {
    require(abs(value) <= BG_DELTA_MAX) { "BG delta must be <= $BG_DELTA_MAX, but was $value" }
}