package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.Minutes

const val INSULIN_EPSILON = 0.01

/**
 * Maximum timespan between "now" and a BG reading where we consider the bg reading
 * as the current/recent bg value. If a manual reading id being announced for an earlier time
 * (like 10 minutes go or so), we don't consider the bg as "recent" anymore.
 */
val RECENT_BG_THRESHOLD = Minutes(1)

/**
 * Maximum timespan a BG reading is allowed to be in the future from "now", else we'll reject the reading.
 */
val EARLY_BG_GUARD = Minutes(1)

/**
 * Timespan until last BG value when the Core will mark the BG as stale.
 */
val STALE_BG_THRESHOLD = Minutes(12)

val MAX_BG_DEVIATION_FOR_KEEP_PREDICTION = BgDelta(10)