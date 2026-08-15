package de.dh.raaps.ui.common

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

val LocalGlucoseUnit = staticCompositionLocalOf<GlucoseUnit> {
    error("No GlucoseUnit provided")
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

@Composable
fun shortRelativeTimeUntil(diffMs: Long): String {
    val diffSec = diffMs / 1000
    val diffMin = diffMs / 60000
    return when {
        diffSec < 5 -> stringResource(R.string.time_ago_just_now)
        diffSec < 61 -> stringResource(R.string.time_until_seconds, diffSec)
        diffMin < 1 -> stringResource(R.string.time_ago_just_now)
        diffMin < 91 -> stringResource(R.string.time_until_minutes, diffMin)
        else -> {
            stringResource(R.string.time_until_hours, diffMin / 60)
        }
    }
}

@Composable
fun shortRelativeTimeUntil(timestamp: Timestamp): String {
    val diffMs = timestamp.ms - System.currentTimeMillis()
    return shortRelativeTimeUntil(diffMs)
}

@Composable
fun withinTimeDescription(minutes: Int): String {
    if (minutes <= 0) return "sofort"
    val hours = minutes / 60
    val mins = minutes % 60

    val timeStr = when {
        hours == 0 -> stringResource(de.dh.raaps.ui.R.string.duration_minutes_format, mins)
        mins == 0 -> stringResource(de.dh.raaps.ui.R.string.duration_hours_format, hours)
        else -> stringResource(de.dh.raaps.ui.R.string.duration_hours_and_minutes_format, hours, mins)
    }

    return "innerhalb von $timeStr"
}

/////////////////////////////////////////////// Glucose & Therapy //////////////////////////////////////

@Composable
fun glucoseUnitLabel(unit: GlucoseUnit = LocalGlucoseUnit.current): String {
    return when (unit) {
        GlucoseUnit.MG_DL -> stringResource(de.dh.raaps.ui.R.string.glucose_unit_mgdl)
        GlucoseUnit.MMOL -> stringResource(de.dh.raaps.ui.R.string.glucose_unit_mmol)
    }
}

@Composable
fun isfUnitLabel(unit: GlucoseUnit = LocalGlucoseUnit.current): String {
    return when (unit) {
        GlucoseUnit.MG_DL -> stringResource(de.dh.raaps.ui.R.string.unit_mgdl_per_u)
        GlucoseUnit.MMOL -> stringResource(de.dh.raaps.ui.R.string.unit_mmol_per_u)
    }
}

@Composable
fun glucoseValue(
    value: BgValue?,
    unit: GlucoseUnit = LocalGlucoseUnit.current,
    default: String = "-",
    withUnit: Boolean = false
): String {
    val valStr = value?.toString(unit) ?: return default
    return if (withUnit) {
        "$valStr ${glucoseUnitLabel(unit)}"
    } else valStr
}

@Composable
fun isfValue(
    value: BgDelta?,
    unit: GlucoseUnit = LocalGlucoseUnit.current,
    default: String = "-",
    withUnit: Boolean = false
): String {
    val valStr = value?.toString(unit) ?: return default
    return if (withUnit) {
        val unitStr = when (unit) {
            GlucoseUnit.MG_DL -> stringResource(de.dh.raaps.ui.R.string.unit_mgdl_per_u)
            GlucoseUnit.MMOL -> stringResource(de.dh.raaps.ui.R.string.unit_mmol_per_u)
        }
        "$valStr $unitStr"
    } else valStr
}

@Composable
fun deltaValue(
    value: BgDelta?,
    unit: GlucoseUnit = LocalGlucoseUnit.current,
    default: String = "-",
    withUnit: Boolean = false
): String {
    val valStr = value?.toDiff(unit) ?: return default
    return if (withUnit) {
        val unitStr = when (unit) {
            GlucoseUnit.MG_DL -> stringResource(de.dh.raaps.ui.R.string.glucose_unit_mgdl)
            GlucoseUnit.MMOL -> stringResource(de.dh.raaps.ui.R.string.glucose_unit_mmol)
        }
        "$valStr $unitStr"
    } else valStr
}

@Composable
fun crValue(value: Double?, default: String = "-", withUnit: Boolean = true): String {
    return value?.let {
        val valStr = String.format(Locale.getDefault(), "%.1f", it)
        if (withUnit) "$valStr " + stringResource(de.dh.raaps.ui.R.string.unit_g_per_u)
        else valStr
    } ?: default
}

@Composable
fun insulinUnitLabel(): String {
    return stringResource(de.dh.raaps.ui.R.string.history_impact_ie_label)
}

@Composable
fun insulinValue(value: Double?, default: String = "-", withUnit: Boolean = true, signed: Boolean = false): String {
    return value?.let {
        val format = if (signed) "%+.2f" else "%.2f"
        val valStr = String.format(Locale.getDefault(), format, it)
        if (withUnit) "$valStr ${insulinUnitLabel()}"
        else valStr
    } ?: default
}

@Composable
fun carbsKeUnitLabel(): String {
    return stringResource(de.dh.raaps.ui.R.string.history_impact_ke_label)
}

@Composable
fun carbsKeValue(value: Double?, default: String = "-", withUnit: Boolean = true, signed: Boolean = false): String {
    return value?.let {
        val format = if (signed) "%+.1f" else "%.1f"
        val valStr = String.format(Locale.getDefault(), format, it)
        if (withUnit) "$valStr ${carbsKeUnitLabel()}"
        else valStr
    } ?: default
}

@Composable
fun carbsGramsUnitLabel(): String {
    return "g" // Not in strings.xml as standalone, but used in formats
}

@Composable
fun carbsGramsValue(value: Double?, default: String = "-", withUnit: Boolean = true, signed: Boolean = false): String {
    return value?.let {
        val format = if (signed) "%+.0f" else "%.0f"
        val valStr = String.format(Locale.getDefault(), format, it)
        if (withUnit) "$valStr ${carbsGramsUnitLabel()}"
        else valStr
    } ?: default
}
