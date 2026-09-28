package com.coffeejournal.ui.extract.stats

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.reference.Varieties
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.domain.rules.BrewMath
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Numbers
import com.coffeejournal.domain.rules.Packages
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.bean.a.VarietyStats
import com.coffeejournal.ui.bean.b.MapStats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

enum class StatsPeriod(val label: String) { MONTH("이번 달"), QUARTER("3개월"), YEAR("올해"), ALL("전체") }

/** One bar: cups brewed at home and had at cafés in a day or a month. */
data class CupBar(val label: String, val brew: Int, val cafe: Int) {
    val total: Int get() = brew + cafe
}

data class Ranked(val name: String, val count: Int)

/** A score on the timeline; [cva] marks a CVA affective score (drawn apart from SCA 2004 totals). */
data class ScorePoint(val createdAt: Long, val score: Double, val cva: Boolean)

data class ScatterPoint(val x: Double, val y: Double, val cva: Boolean)

data class Spending(
    /** Won spent on beans: bag price ÷ bag weight × dose, summed over the brews whose price per gram is known. */
    val beans: Double,
    val brewsPriced: Int,
    val brewsUnpriced: Int,
    /** Won paid at cafés (한 잔 가격). */
    val cafe: Double,
    val cafesPriced: Int,
)

data class StatsSummary(
    val period: StatsPeriod,
    val from: LocalDate?,
    val to: LocalDate,
    val brewCount: Int,
    val cafeCount: Int,
    val cuppingCount: Int,
    /** Daily bars for 이번 달, monthly bars otherwise. */
    val bars: List<CupBar>,
    val daily: Boolean,
    val gramsUsed: Double,
    val spending: Spending,
    val origins: List<Ranked>,
    val processes: List<Ranked>,
    val varieties: List<Ranked>,
    val roasteries: List<Ranked>,
    val scores: List<ScorePoint>,
    val ratioVsScore: List<ScatterPoint>,
    val tempVsScore: List<ScatterPoint>,
) {
    val cups: Int get() = brewCount + cafeCount
    val isEmpty: Boolean get() = brewCount == 0 && cafeCount == 0 && cuppingCount == 0
}

/** The 통계 screen's numbers (feature-plan-v2 §2.4), computed on the device from the records and the pantry. */
object StatsCalc {
    const val TOP = 5

    /** First day of the period ([StatsPeriod.ALL]: the first record's day, null without records) up to [today]. */
    fun range(period: StatsPeriod, today: LocalDate, entries: List<Entry>): Pair<LocalDate?, LocalDate> {
        val monthStart = LocalDate(today.year, today.month, 1)
        val from = when (period) {
            StatsPeriod.MONTH -> monthStart
            StatsPeriod.QUARTER -> monthStart.minus(DatePeriod(months = 2))
            StatsPeriod.YEAR -> LocalDate(today.year, 1, 1)
            StatsPeriod.ALL -> entries.minOfOrNull { it.createdAt }?.let { Dates.toLocalDate(it) }?.let { LocalDate(it.year, it.month, 1) }
        }
        return from to today
    }

    fun compute(entries: List<Entry>, pantry: List<PantryItem>, period: StatsPeriod, today: LocalDate): StatsSummary {
        val (from, to) = range(period, today, entries)
        val inPeriod = entries.filter { e ->
            val d = Dates.toLocalDate(e.createdAt)
            (from == null || d >= from) && (period == StatsPeriod.ALL || d <= to)
        }
        val brews = inPeriod.filter { Packages.isBrew(it) }
        val cafes = inPeriod.filter { it.isCafe }
        val daily = period == StatsPeriod.MONTH
        val bars = if (daily) dailyBars(brews, cafes, today) else monthlyBars(brews, cafes, from, maxOf(to, lastMonthOf(inPeriod) ?: to))
        val grams = Numbers.finite(brews.sumOf { Numbers.parse(it.dose)?.takeIf { d -> d > 0 } ?: 0.0 }) ?: 0.0

        val records = BeanRecords.flatten(inPeriod)
        // origins, processes and varieties count every bean of a café blend; roasteries count the record once
        val beans = BeanRecords.flatten(inPeriod, blendBeans = true)
        val scored = (brews + cafes).mapNotNull { e -> scoreOf(e)?.let { (s, cva) -> Triple(e, s, cva) } }.sortedBy { it.first.createdAt }
        return StatsSummary(
            period = period, from = from, to = to,
            brewCount = brews.size, cafeCount = cafes.size, cuppingCount = inPeriod.count { it.isCupping },
            bars = bars, daily = daily, gramsUsed = grams,
            spending = spending(brews, cafes, entries, pantry),
            origins = top(beans.mapNotNull { r -> MapStats.countryOf(r)?.ko }),
            processes = top(beans.map { processLabel(it.process, it.processOther) }.filter { it.isNotBlank() }),
            varieties = topVarieties(beans.flatMap { BeanNames.splitVarietyValues(it.variety) }),
            roasteries = top(records.map { r -> r.roastery.trim().ifBlank { BeanNames.parseNameRoastery(r.name).trim() } }.filter { it.isNotBlank() }),
            scores = scored.map { (e, s, cva) -> ScorePoint(e.createdAt, s, cva) },
            ratioVsScore = scored.filter { Packages.isBrew(it.first) }.mapNotNull { (e, s, cva) ->
                BrewMath.ratio(Numbers.parse(e.dose), Numbers.parse(e.water))?.let { ScatterPoint(it, s, cva) }
            },
            tempVsScore = scored.filter { Packages.isBrew(it.first) }.mapNotNull { (e, s, cva) ->
                Numbers.parse(e.temp)?.takeIf { it > 0 }?.let { ScatterPoint(it, s, cva) }
            },
        )
    }

    /** The record's SCA 2004 total, else its CVA score (flagged); null when neither was scored. */
    fun scoreOf(e: Entry): Pair<Double, Boolean>? {
        ScaScoring.effectiveTotal(e.attributes)?.let { return it to false }
        CvaScoring.scoreOf(e)?.let { return it to true }
        return null
    }

    private fun lastMonthOf(entries: List<Entry>): LocalDate? = entries.maxOfOrNull { it.createdAt }?.let { Dates.toLocalDate(it) }

    fun dailyBars(brews: List<Entry>, cafes: List<Entry>, today: LocalDate): List<CupBar> {
        val start = LocalDate(today.year, today.month, 1)
        val days = generateSequence(start) { it.plus(DatePeriod(days = 1)) }.takeWhile { it.month == start.month }.toList()
        val b = brews.groupingBy { Dates.toLocalDate(it.createdAt) }.eachCount()
        val c = cafes.groupingBy { Dates.toLocalDate(it.createdAt) }.eachCount()
        return days.map { d -> CupBar("${d.day}", b[d] ?: 0, c[d] ?: 0) }
    }

    fun monthlyBars(brews: List<Entry>, cafes: List<Entry>, from: LocalDate?, to: LocalDate): List<CupBar> {
        val first = from ?: return emptyList()
        var m = LocalDate(first.year, first.month, 1)
        val last = LocalDate(to.year, to.month, 1)
        fun key(e: Entry) = Dates.toLocalDate(e.createdAt).let { it.year * 12 + it.month.number - 1 }
        val b = brews.groupingBy(::key).eachCount()
        val c = cafes.groupingBy(::key).eachCount()
        val out = mutableListOf<CupBar>()
        // "9월" within one year; across years the first bar and every January carry the year ("25.12", "26.1")
        val spansYears = first.year != to.year
        while (m <= last) {
            val k = m.year * 12 + m.month.number - 1
            val label = if (spansYears && (m.month.number == 1 || out.isEmpty())) "${m.year % 100}.${m.month.number}" else "${m.month.number}월"
            out += CupBar(label, b[k] ?: 0, c[k] ?: 0)
            m = m.plus(DatePeriod(months = 1))
        }
        return out
    }

    /** Most frequent first, ties in first-seen order; at most [TOP]. */
    fun top(values: List<String>, limit: Int = TOP): List<Ranked> {
        val counts = LinkedHashMap<String, Int>()
        values.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        return counts.entries.sortedByDescending { it.value }.take(limit).map { Ranked(it.key, it.value) }
    }

    /** Varieties grouped the 품종 view's way (Heirloom numbers folded, "게이샤 (Geisha)" labels). */
    private fun topVarieties(raw: List<String>): List<Ranked> {
        val labels = LinkedHashMap<String, String>()
        val keys = raw.mapNotNull { r ->
            val k = VarietyStats.key(r).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            labels.getOrPut(k) { Varieties.displayNames[k] ?: VarietyStats.koreanLabel(r) }
            k
        }
        return top(keys).map { Ranked(labels[it.name] ?: it.name, it.count) }
    }

    /** "허니(더블 퍼멘티드)" counts as 허니; 기타 shows what was written. */
    fun processLabel(process: String, processOther: String): String {
        val p = process.trim()
        if (p.isEmpty()) return ""
        if (p == "기타") return processOther.trim().ifBlank { "기타" }
        return BeanNames.parseFarmProducer(p).farm.trim().ifBlank { p }
    }

    /**
     * Spending: a brew costs price ÷ bag weight × dose. The price per gram comes from the record itself, else from an
     * earlier record of the same bean (its first registration carries the bag), else from a pantry bag of that name.
     * A custom blend costs the sum of its components that way. Café records add their 한 잔 가격.
     */
    fun spending(brews: List<Entry>, cafes: List<Entry>, allEntries: List<Entry>, pantry: List<PantryItem>): Spending {
        val perGram = HashMap<String, Double?>()
        fun unit(key: String): Double? = perGram.getOrPut(key) {
            allEntries.filter { Packages.isBrew(it) && BeanNames.coreBeanName(it.name) == key }.sortedBy { it.createdAt }
                .firstNotNullOfOrNull { unitPrice(it.price, it.bagWeight) }
                ?: pantry.filter { BeanNames.coreBeanName(it.name) == key }.firstNotNullOfOrNull { unitPrice(it.price, it.weight) }
        }
        var beans = 0.0
        var priced = 0
        var unpriced = 0
        for (e in brews) {
            val cost: Double? = if (e.beanMode == BeanMode.CUSTOM_BLEND && e.blendComponents.isNotEmpty()) {
                val parts = e.blendComponents.map { c -> Numbers.parse(c.grams)?.let { g -> unit(BeanNames.coreBeanName(c.name))?.let { it * g } } }
                if (parts.all { it == null }) null else parts.sumOf { it ?: 0.0 }
            } else {
                val dose = Numbers.parse(e.dose)?.takeIf { it > 0 }
                val u = unitPrice(e.price, e.bagWeight) ?: unit(BeanNames.coreBeanName(e.name))
                if (dose == null || u == null) null else u * dose
            }
            if (cost != null && cost.isFinite()) { beans += cost; priced++ } else unpriced++
        }
        val cafePrices = cafes.mapNotNull { Numbers.parse(Prices.normalize(it.price))?.takeIf { p -> p > 0 } }
        return Spending(beans, priced, unpriced, cafePrices.sum(), cafePrices.size)
    }

    /** Won per gram from a bag's price and weight (the rules of the 100g 환산가: weight > 0, price ≥ 0). */
    fun unitPrice(price: String, weight: String): Double? {
        val w = Numbers.parse(weight)?.takeIf { it > 0 } ?: return null
        val digits = Prices.normalize(price).takeIf { it.isNotEmpty() } ?: return null
        val p = Numbers.parse(digits)?.takeIf { it >= 0 } ?: return null
        return Numbers.finite(p / w)
    }

    fun won(v: Double): String = Prices.format(kotlin.math.round(v)) + "원"
}
