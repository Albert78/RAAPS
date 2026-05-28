package de.dh.raaps.plugin.glucose.receiver

import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.Minutes

/**
 * Enum which determines a type of external glucose source.
 */
enum class ExternalSourceType(
    val readingsInterval: BgReadingsInterval,
    val readingsTimeDelay: Minutes
) {
    xDrip1Min(BgReadingsInterval.OneMinute, Minutes(1)),
    xDrip5Min(BgReadingsInterval.FiveMinutes, Minutes(5))
}