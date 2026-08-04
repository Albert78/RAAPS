package de.dh.raaps.core.aps

import de.dh.raaps.common.model.data.Minutes

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

val LOW_WARNING_THRESHOLD = Minutes(25)
val FAST_KE_DEFAULT_PEAK = Minutes(25)