package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.Minutes

/**
 * Timespan until last BG value when the Core will mark the BG as stale.
 */
val STALE_BG_THRESHOLD = Minutes(12)

const val SWITCH_OFF_ALGORITHM_INVALID_VALUES_THRESHOLD_IN_MINUTES = 15
const val AGGRESSIVENESS_ERROR_CORRECTION = 1.0

val LOW_WARNING_THRESHOLD = Minutes(25)
val LOW_RECOVERY_THRESHOLD = Minutes(30)
val BOLUS_CALCULATOR_LOW_PREDICTION_LOOKAHEAD = Minutes(30)

val LOW_BG_SAFETY_MARGIN = BgDelta.fromMgDl(5)
val LOW_CORRECTION_THRESHOLD = BgDelta.fromMgDl(-10)

val HIGH_CORRECTION_THRESHOLD = BgDelta.fromMgDl(10)