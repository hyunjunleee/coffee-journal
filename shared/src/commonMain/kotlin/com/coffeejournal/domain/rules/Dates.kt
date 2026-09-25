@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.coffeejournal.domain.rules

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import kotlinx.datetime.isoDayNumber
import kotlin.time.Clock
import kotlin.time.Instant

/** All date/time conversions go through here so the rest of the code never touches Instant directly. */
object Dates {
    const val DAY_MS: Long = 86_400_000L

    private val zone: TimeZone get() = TimeZone.currentSystemDefault()

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    fun today(): LocalDate = Clock.System.now().toLocalDateTime(zone).date

    fun toLocalDateTime(epochMillis: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)

    fun toLocalDate(epochMillis: Long): LocalDate = toLocalDateTime(epochMillis).date

    fun startOfDayMillis(date: LocalDate): Long = date.atStartOfDayIn(zone).toEpochMilliseconds()

    fun toMillis(dateTime: LocalDateTime): Long = dateTime.toInstant(zone).toEpochMilliseconds()

    fun toMillis(date: LocalDate, hour: Int, minute: Int): Long = toMillis(date.atTime(hour, minute))

    /** Parses "YYYY-MM-DD"; null when blank or malformed. */
    fun parseIsoDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value.trim().take(10)) }.getOrNull()
    }

    fun isoDate(date: LocalDate): String = date.toString()

    fun plusDays(date: LocalDate, days: Int): LocalDate = date.plus(DatePeriod(days = days))

    fun daysBetween(from: LocalDate, to: LocalDate): Int = from.daysUntil(to)

    /** "2026.7.15" — the web's compact format. */
    fun ymdCompact(epochMillis: Long): String {
        val d = toLocalDate(epochMillis)
        return "${d.year}.${d.month.number}.${d.day}"
    }

    fun ymdCompact(date: LocalDate): String = "${date.year}.${date.month.number}.${date.day}"

    /** "2026.07.15" — the web's entry card format. */
    fun ymdPadded(epochMillis: Long): String {
        val d = toLocalDate(epochMillis)
        return "${d.year}.${pad2(d.month.number)}.${pad2(d.day)}"
    }

    /** "7.15" without the year. */
    fun md(epochMillis: Long): String {
        val d = toLocalDate(epochMillis)
        return "${d.month.number}.${d.day}"
    }

    /** "7.15 08:30". */
    fun mdHm(epochMillis: Long): String {
        val dt = toLocalDateTime(epochMillis)
        return "${dt.month.number}.${dt.day} ${pad2(dt.hour)}:${pad2(dt.minute)}"
    }

    /** "2026년 9월 21일". */
    fun koreanLong(date: LocalDate): String = "${date.year}년 ${date.month.number}월 ${date.day}일"

    /** "2026.9.21.(월)". */
    fun ymdWithWeekday(date: LocalDate): String =
        "${date.year}.${date.month.number}.${date.day}.(${weekdayKo(date.dayOfWeek)})"

    fun weekdayKo(dow: DayOfWeek): String = when (dow) {
        DayOfWeek.MONDAY -> "월"; DayOfWeek.TUESDAY -> "화"; DayOfWeek.WEDNESDAY -> "수"
        DayOfWeek.THURSDAY -> "목"; DayOfWeek.FRIDAY -> "금"; DayOfWeek.SATURDAY -> "토"
        else -> "일"
    }

    /** Sunday = 0 … Saturday = 6 (web calendar layout). */
    fun sundayFirstIndex(dow: DayOfWeek): Int = dow.isoDayNumber % 7

    fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()

    /** "YYYY-MM-DDTHH:MM" style value for the record form. */
    fun dateTimeInput(epochMillis: Long): String {
        val dt = toLocalDateTime(epochMillis)
        return "${dt.year}-${pad2(dt.month.number)}-${pad2(dt.day)}T${pad2(dt.hour)}:${pad2(dt.minute)}"
    }

    fun parseDateTimeInput(value: String): Long? {
        val v = value.trim()
        if (v.isBlank()) return null
        return runCatching { toMillis(LocalDateTime.parse(v)) }.getOrNull()
    }

    /**
     * Web formatRoastDateWithYear (script3.js 2516-2524): "07. 01" → "2026. 7. 1" using the record year; the month and
     * day go through `Number(...)`, so leading zeros are dropped. A missing createdAt falls back to now, like the web.
     */
    fun roastDateWithYear(value: String, createdAt: Long): String {
        val raw = value.trim()
        if (raw.isEmpty() || Regex("^\\d{4}\\D").containsMatchIn(raw)) return raw
        val m = Regex("^(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})\\.?$").find(raw) ?: return raw
        val month = m.groupValues[1].toIntOrNull() ?: return raw
        val day = m.groupValues[2].toIntOrNull() ?: return raw
        val year = toLocalDate(createdAt.takeIf { it != 0L } ?: nowMillis()).year
        return "$year. $month. $day"
    }
}
