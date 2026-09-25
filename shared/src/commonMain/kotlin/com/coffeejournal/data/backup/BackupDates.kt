package com.coffeejournal.data.backup

import com.coffeejournal.domain.rules.Dates
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * ISO-8601 instant strings for the backup envelope (`exportedAt`). This is the only place outside
 * domain/rules/Dates.kt that touches Instant; candidate for promotion into Dates.
 */
@OptIn(ExperimentalTime::class)
internal object BackupDates {
    fun iso8601(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis).toString()

    /** "2026-09-25T05:23:43.817Z" (or an offset form) → epoch millis; also accepts date-only / local date-time. */
    fun parseIso8601(text: String?): Long? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        runCatching { Instant.parse(t).toEpochMilliseconds() }.getOrNull()?.let { return it }
        Dates.parseDateTimeInput(t.take(16))?.let { return it }
        return Dates.parseIsoDate(t)?.let { Dates.startOfDayMillis(it) }
    }

    /** "2026.9.25 14:23" in the device time zone, or null. */
    fun localLabel(text: String?): String? {
        val millis = parseIso8601(text) ?: return null
        val dt = Dates.toLocalDateTime(millis)
        return "${Dates.ymdCompact(millis)} ${Dates.pad2(dt.hour)}:${Dates.pad2(dt.minute)}"
    }
}
