package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.reference.BeanRangeColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** A continuous stretch of days on which the same bean was brewed (web computeBeanRanges). */
data class BeanRange(val name: String, val start: Long, val end: Long, val count: Int, val colorHex: String)

object CalendarRanges {
    const val GAP_THRESHOLD_MS: Long = 10 * Dates.DAY_MS

    fun compute(entries: List<Entry>): List<BeanRange> {
        val brews = entries.filter { Packages.isBrew(it) && it.name.isNotBlank() }
        val byKey = LinkedHashMap<String, MutableList<Entry>>()
        for (en in brews) byKey.getOrPut(BeanNames.coreBeanName(en.name)) { mutableListOf() }.add(en)
        val ranges = mutableListOf<BeanRange>()
        for ((_, group) in byKey) {
            val displayName = group.first().name.trim()
            val sorted = group.sortedBy { it.createdAt }
            var start = 0L
            var end = 0L
            var count = 0
            for (en in sorted) {
                if (count == 0) {
                    start = en.createdAt; end = en.createdAt; count = 1
                } else if (en.createdAt - end > GAP_THRESHOLD_MS) {
                    ranges += BeanRange(displayName, start, end, count, "")
                    start = en.createdAt; end = en.createdAt; count = 1
                } else {
                    end = en.createdAt; count++
                }
            }
            if (count > 0) ranges += BeanRange(displayName, start, end, count, "")
        }
        val palette = BeanRangeColors.hex
        return ranges.sortedBy { it.start }.mapIndexed { i, r -> r.copy(colorHex = palette[i % palette.size]) }
    }

    /** Ranges intersecting the given day (start of day millis in [zone]). */
    fun rangesOn(ranges: List<BeanRange>, dayStartMillis: Long, zone: TimeZone = Dates.systemZone): List<BeanRange> {
        val dayEnd = dayStartMillis + Dates.DAY_MS - 1
        return ranges.filter { r ->
            val rs = Dates.startOfDayMillis(Dates.toLocalDate(r.start, zone), zone)
            val re = Dates.startOfDayMillis(Dates.toLocalDate(r.end, zone), zone) + Dates.DAY_MS - 1
            rs <= dayEnd && re >= dayStartMillis
        }
    }

    /** Ranges covering the local calendar [day] in [zone]. */
    fun rangesOn(ranges: List<BeanRange>, day: LocalDate, zone: TimeZone = Dates.systemZone): List<BeanRange> =
        ranges.filter { r -> Dates.toLocalDate(r.start, zone) <= day && Dates.toLocalDate(r.end, zone) >= day }
}
