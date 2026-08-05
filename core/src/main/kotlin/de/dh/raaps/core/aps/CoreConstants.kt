package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.Minutes

/**
 * Timespan until last BG value when the Core will mark the BG as stale.
 */
val STALE_BG_THRESHOLD = Minutes(12)

val SWITCH_OFF_ALGORITHM_INVALID_VALUES_THRESHOLD_IN_MINUTES = 15

val LOW_WARNING_THRESHOLD = Minutes(25)
val FAST_KE_DEFAULT_PEAK = Minutes(25)

/**
 * We'll suspend blood sugar corrections until we have less COB then this value.
 */
val SUSPEND_HIGH_CORRECTIONS_ON_HIGH_COB_THRESHOLD = 2.0