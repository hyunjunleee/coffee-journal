package com.coffeejournal.domain.rules

import kotlinx.datetime.LocalDate

object DdayRules {
    /** Web currentDdayCount / renderDday: the start day itself is D-1. */
    fun dayCount(startDate: LocalDate, today: LocalDate = Dates.today()): Int = Dates.daysBetween(startDate, today) + 1

    fun label(startDate: LocalDate?, today: LocalDate = Dates.today()): String? {
        startDate ?: return null
        val n = dayCount(startDate, today)
        return "Coffee D-${if (n < 1) 0 else n}"
    }

    /** "N일 기념" for multiples of 30, marked big for multiples of 100. */
    fun milestone(dayCount: Int): Milestone? = when {
        dayCount > 0 && dayCount % 100 == 0 -> Milestone("${dayCount}일 기념 ✦", big = true)
        dayCount > 0 && dayCount % 30 == 0 -> Milestone("${dayCount}일 기념", big = false)
        else -> null
    }

    /** Calendar badge: every 10 days, big every 100 days. */
    fun calendarBadge(daysSinceStart: Int): Milestone? = when {
        daysSinceStart > 0 && daysSinceStart % 100 == 0 -> Milestone("${daysSinceStart}일", big = true)
        daysSinceStart > 0 && daysSinceStart % 10 == 0 -> Milestone("${daysSinceStart}일", big = false)
        else -> null
    }

    data class Milestone(val text: String, val big: Boolean)
}
