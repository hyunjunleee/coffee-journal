package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import kotlinx.datetime.LocalDate

/** What the daily check can remind about (feature plan v2 §3). */
enum class ReminderKind { PEAK, LOW_STOCK, DDAY }

/**
 * One notification the daily check would post. [key] names the event itself (a bag and its peak date, a supply
 * running low, a D-day milestone), so a reminder that was already sent is never sent again.
 */
data class Reminder(val key: String, val kind: ReminderKind, val title: String, val body: String)

/**
 * A bean the home "마시는 중" card shows, with the grams the card says are left ([remainingLine] is the card's own
 * "잔여량 42g/200g"). [id] names the supply: "pantry:<item id>" for an opened bag, "bean:<core name>" for the
 * most recent bean when no bag is open.
 */
data class BeanStock(
    val id: String,
    val beanKey: String,
    val name: String,
    val remainingGrams: Double,
    val bagGrams: Double,
    val remainingLine: String,
)

/** The reminder kinds the user keeps on (알림 설정). */
data class ReminderKinds(val peak: Boolean = true, val lowStock: Boolean = true, val dday: Boolean = true) {
    fun allows(kind: ReminderKind): Boolean = when (kind) {
        ReminderKind.PEAK -> peak
        ReminderKind.LOW_STOCK -> lowStock
        ReminderKind.DDAY -> dday
    }
}

/**
 * The reminders due on a day, as a pure function of the journal. The rules reuse what the home tab shows, so a
 * notification never says something the app does not: the pantry's expected peak ([PantryRules.peakWindow]), the
 * 마시는 중 card's remaining grams (passed in as [BeanStock]) and the D-day pill's milestones ([DdayRules]).
 */
object Reminders {
    /** One cup when the bean has no record with a dose yet. */
    const val FALLBACK_CUP_GRAMS = 15.0

    /** Running low: this many usual cups or fewer are left. */
    const val LOW_STOCK_CUPS = 2

    /**
     * Everything due [today] for the kinds switched on in [kinds], minus the keys in [sent]. [inUse] is what the
     * 마시는 중 card shows (ReminderInputs.inUse builds it from the home card's own derivation).
     */
    fun due(
        pantry: List<PantryItem>,
        entries: List<Entry>,
        inUse: List<BeanStock>,
        ddayStart: LocalDate?,
        today: LocalDate,
        kinds: ReminderKinds,
        sent: Set<String> = emptySet(),
    ): List<Reminder> {
        val all = buildList {
            if (kinds.peak) addAll(peakStarts(pantry, today))
            if (kinds.lowStock) addAll(lowStock(inUse, entries))
            if (kinds.dday) ddayMilestone(ddayStart, today)?.let(::add)
        }
        return all.distinctBy { it.key }.filter { it.key !in sent }
    }

    /** ① A pantry bag (opened or not) whose expected peak window starts [today]; oldest bag first. */
    fun peakStarts(pantry: List<PantryItem>, today: LocalDate): List<Reminder> = pantry
        .sortedBy { it.createdAt }
        .mapNotNull { item ->
            val start = PantryRules.peakWindow(item)?.first ?: return@mapNotNull null
            if (start != today) return@mapNotNull null
            Reminder(
                key = "peak:${item.id}:$start",
                kind = ReminderKind.PEAK,
                title = "피크 시작 · ${item.name.trim().ifBlank { "이름 없음" }}",
                body = "오늘부터 마시기 좋은 때예요. ${PantryRules.drinkWindowText(item)}",
            )
        }

    /**
     * ② A bean in use with [LOW_STOCK_CUPS] usual cups or fewer left, where a cup is the median dose of the bean's
     * records ([cupGrams]).
     */
    fun lowStock(inUse: List<BeanStock>, entries: List<Entry>): List<Reminder> = inUse.mapNotNull { stock ->
        if (!(stock.bagGrams > 0)) return@mapNotNull null
        val cup = cupGrams(stock.beanKey, entries)
        if (stock.remainingGrams > cup * LOW_STOCK_CUPS) return@mapNotNull null
        Reminder(
            key = "low:${stock.id}",
            kind = ReminderKind.LOW_STOCK,
            title = "원두 소진 임박 · ${stock.name}",
            body = "${stock.remainingLine} · ${cupsLeftText(stock.remainingGrams, cup)}",
        )
    }

    /** ③ Today is a Coffee D-day milestone (the home pill's "N일 기념"). */
    fun ddayMilestone(start: LocalDate?, today: LocalDate): Reminder? {
        start ?: return null
        val day = DdayRules.dayCount(start, today)
        val milestone = DdayRules.milestone(day) ?: return null
        val label = DdayRules.label(start, today) ?: return null
        return Reminder(
            key = "dday:$start:$day",
            kind = ReminderKind.DDAY,
            title = "$label · ${milestone.text}",
            body = "커피 처음 마신 날부터 ${day}일째 되는 날이에요.",
        )
    }

    /** The usual dose of a bean: the median dose of its brew records, or [FALLBACK_CUP_GRAMS] without one. */
    fun cupGrams(beanKey: String, entries: List<Entry>): Double {
        val doses = entries.asSequence()
            .filter { Packages.isBrew(it) && BeanNames.coreBeanName(it.name) == beanKey }
            .mapNotNull { en -> Numbers.parse(en.dose)?.takeIf { it > 0 } }
            .sorted()
            .toList()
        if (doses.isEmpty()) return FALLBACK_CUP_GRAMS
        val mid = doses.size / 2
        return if (doses.size % 2 == 1) doses[mid] else (doses[mid - 1] + doses[mid]) / 2
    }

    /** "평소 15g 기준 1잔 남았어요", or that not even one is left. */
    fun cupsLeftText(remaining: Double, cup: Double): String {
        val usual = "평소 ${Prices.trimNumber(cup)}g 기준"
        val cups = (remaining / cup).toInt()
        return when {
            remaining <= 0.0 -> "다 마셨어요. 다음 원두를 준비해 두세요."
            cups < 1 -> "$usual 한 잔이 안 되게 남았어요."
            else -> "$usual ${cups}잔 남았어요."
        }
    }
}
