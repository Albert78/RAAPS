package de.dh.raaps.ui.common

import android.util.Range
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import de.dh.raaps.common.R
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppFormatters(
    val shortDateTime: DateTimeFormatter,
    val shortDate: DateTimeFormatter,
    val longDateTime: DateTimeFormatter,
    val longDate: DateTimeFormatter,
)

// Provider for the formatters
val LocalAppFormatters = staticCompositionLocalOf<AppFormatters> {
    error("No AppFormatters provided")
}

@Composable
fun rememberAppFormatters(): AppFormatters {
    val locale = LocalLocale.current.platformLocale

    val shortDateTimePattern = stringResource(R.string.short_date_time_format)
    val shortDatePattern = stringResource(R.string.short_date_format)
    val longDateTimePattern = stringResource(R.string.long_date_time_format)
    val longDatePattern = stringResource(R.string.long_date_format)

    return remember(locale, shortDateTimePattern, shortDatePattern, longDateTimePattern, longDatePattern) {
        AppFormatters(
            shortDateTime = DateTimeFormatter.ofPattern(shortDateTimePattern, locale),
            shortDate = DateTimeFormatter.ofPattern(shortDatePattern, locale),
            longDateTime = DateTimeFormatter.ofPattern(longDateTimePattern, locale),
            longDate = DateTimeFormatter.ofPattern(longDatePattern, locale)
        )
    }
}

/////////////////////////////////////////////// Time ///////////////////////////////////////////////

fun time(time: LocalTime): String {
    return String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute)
}

/////////////////////////////////////////////// Long date time //////////////////////////////////////

@Composable
fun longDateTime(dateTime: LocalDateTime): String {
    return dateTime.format(LocalAppFormatters.current.longDateTime)
}

@Composable
fun longDateTime(dateTime: LocalDateTime?, default: String = "-"): String {
    return dateTime?.let {
        longDateTime(dateTime)
    } ?: default
}

/////////////////////////////////////////////// Short date time //////////////////////////////////////

@Composable
fun shortDateTime(dateTime: LocalDateTime): String {
    return dateTime.format(LocalAppFormatters.current.shortDateTime)
}

@Composable
fun shortDateTime(dateTime: LocalDateTime?, default: String = "-"): String {
    return dateTime?.let {
        shortDateTime(dateTime)
    } ?: default
}

/////////////////////////////////////////////// Long date //////////////////////////////////////

@Composable
fun longDate(date: LocalDate): String {
    return date.format(LocalAppFormatters.current.longDate)
}

@Composable
fun longDate(date: LocalDate?, default: String = "-"): String {
    return date?.let {
        longDate(date)
    } ?: default
}

@Composable
fun longDate(dateTime: LocalDateTime?, default: String = "-"): String {
    return longDate(dateTime?.toLocalDate(), default)
}

/////////////////////////////////////////////// Short date //////////////////////////////////////

@Composable
fun shortDate(date: LocalDate): String {
    return date.format(LocalAppFormatters.current.shortDate)
}

@Composable
fun shortDate(date: LocalDate?, default: String = "-"): String {
    return date?.let {
        shortDate(date)
    } ?: default
}

/////////////////////////////////////////////// Time ago //////////////////////////////////////

@Composable
fun shortRelativeTimeAgo(diffMs: Long): String {
    val diffSec = diffMs / 1000
    val diffMin = diffMs / 60000
    return when {
        diffSec < 5 -> stringResource(R.string.time_ago_just_now)
        diffSec < 61 -> stringResource(R.string.time_ago_seconds_ago, diffSec)
        diffMin < 1 -> stringResource(R.string.time_ago_just_now)
        diffMin < 91 -> stringResource(R.string.time_ago_minutes_ago, diffMin)
        else -> {
            stringResource(R.string.time_ago_hours_ago, diffMin / 60)
        }
    }
}

@Composable
fun shortRelativeTimeAgo(timestamp: Timestamp): String {
    val diffMs = System.currentTimeMillis() - timestamp.ms
    return shortRelativeTimeAgo(diffMs)
}

/////////////////////////////////////////////// Glucose & Therapy //////////////////////////////////////

@Composable
fun glucoseValue(value: BgValue?, unit: GlucoseUnit, default: String = "-"): String {
    return value?.toString(unit) ?: default
}

@Composable
fun isfValue(value: BgDelta?, unit: GlucoseUnit, default: String = "-"): String {
    return value?.toString(unit) ?: default
}

@Composable
fun crValue(value: Double?, default: String = "-"): String {
    return value?.let { String.format(Locale.getDefault(), "%.1f g/U", it) } ?: default
}

@Composable
fun targetRange(range: Range<BgValue>?, unit: GlucoseUnit, default: String = "-"): String {
    return range?.let { "${it.lower.toString(unit)} - ${it.upper.toString(unit)}" } ?: default
}
