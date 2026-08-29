package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.Minutes

/**
 * Timespan until last BG value when the Core will mark the BG as stale.
 */
val STALE_BG_THRESHOLD = Minutes(12)

const val SWITCH_OFF_ALGORITHM_INVALID_VALUES_THRESHOLD_IN_MINUTES = 15
const val AGGRESSIVENESS_ERROR_CORRECTION = 1.0
const val AGGRESSIVENESS_CARBS_CORRECTION = 1.0

val LOW_WARNING_THRESHOLD = Minutes(25)
val BOLUS_CALCULATOR_LOW_PREDICTION_LOOKAHEAD = Minutes(30)