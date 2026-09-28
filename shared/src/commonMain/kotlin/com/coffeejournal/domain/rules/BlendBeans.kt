package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Entry
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * The beans of a café blend (카페 블렌드: the roaster's blend, entered as one bean-info block per bean) and the share
 * rules of both kinds of blend. Bean 1 lives in the record's own fields, so everything that reads one bean keeps
 * working; `Entry.blendComponents` holds every bean, bean 1 included, each with its share. A later bean that leaves
 * 로스터리, 로스팅 정도 or 로스팅 날짜 empty has bean 1's ([resolve]); whatever shows or counts a bean reads it resolved.
 */
object BlendBeans {
    /** A café blend record (not a cupping, whose beans are its cupping cards). */
    fun isCafeBlend(entry: Entry): Boolean = !entry.isCupping && entry.beanMode == BeanMode.COMMERCIAL_BLEND

    /** Bean 1 as the record's own fields hold it. */
    fun firstBean(entry: Entry): BlendComponent = BlendComponent(
        roastery = entry.roastery, selection = entry.selection, country = entry.country, region = entry.region,
        farmProducer = entry.farmProducer, washingStation = entry.washingStation, altitude = entry.altitude,
        variety = entry.variety, moisture = entry.moisture, density = entry.density, score = entry.score,
        process = entry.process, processOther = entry.processOther, roast = entry.roast, roastDate = entry.roastDate,
    )

    /**
     * Every bean of a café blend, resolved: bean 1 from the record's fields (they win over the copy in the components,
     * which a later edit of a sibling record may have left behind) with its stored share, then beans 2... A record that
     * is no café blend, or a café blend whose beans were never entered, is its one bean.
     */
    fun beans(entry: Entry): List<BlendComponent> {
        val first = firstBean(entry)
        if (!isCafeBlend(entry) || entry.blendComponents.isEmpty()) return listOf(first)
        val own = first.copy(percent = entry.blendComponents.first().percent)
        return listOf(own) + entry.blendComponents.drop(1).map { resolve(own, it) }
    }

    /** [bean] with 로스터리, 로스팅 정도 and 로스팅 날짜 taken from [first] where it has none of its own. */
    fun resolve(first: BlendComponent, bean: BlendComponent): BlendComponent = bean.copy(
        roastery = bean.roastery.ifBlank { first.roastery },
        roast = bean.roast.ifBlank { first.roast },
        roastDate = bean.roastDate.ifBlank { first.roastDate },
    )

    /** A café blend with more than one bean entered: its beans are shown and counted one by one. */
    fun hasBeans(entry: Entry): Boolean = isCafeBlend(entry) && entry.blendComponents.size > 1

    // ---------- shares ----------

    /** The shares typed so far added up; null while none is typed. "NaN"-like values count as missing. */
    fun percentSum(percents: List<String>): Double? {
        val values = percents.mapNotNull { Numbers.parse(it) }
        return if (values.isEmpty()) null else values.sum()
    }

    /** A sum of shares that makes a whole blend: 100%, give or take thirds typed as 33.3. */
    fun isWhole(sum: Double): Boolean = abs(sum - 100.0) < 0.15

    /** "60", "33.3": a share to one decimal, without a trailing ".0". */
    fun formatPercent(value: Double): String = Prices.trimNumber((value * 10).roundToLong() / 10.0)

    /** "60%" for a typed share, null when none (or no number) is typed. */
    fun percentText(percent: String): String? = Numbers.parse(percent)?.let { "${formatPercent(it)}%" }

    /**
     * Each row's share of a blend mixed by weight, in whole percents that add up to exactly 100 (largest remainder:
     * every share rounded down, then a point more for the largest remainders, the earlier row first on a tie). A row
     * without grams has none, and no row has one while no grams are given.
     */
    fun sharesFromGrams(grams: List<String>): List<Int?> {
        val values = grams.map { g -> Numbers.parse(g)?.takeIf { it > 0 } }
        val total = values.sumOf { it ?: 0.0 }
        if (!(total > 0) || !total.isFinite()) return values.map { null }
        val exact = values.map { v -> v?.let { it / total * 100 } }
        val shares = exact.map { e -> e?.let { floor(it).toInt() } }.toMutableList()
        var left = 100 - shares.sumOf { it ?: 0 }
        for (i in exact.indices.filter { exact[it] != null }.sortedByDescending { exact[it]!! - shares[it]!! }) {
            if (left <= 0) break
            shares[i] = shares[i]!! + 1
            left--
        }
        return shares
    }

    // ---------- names ----------

    /** A bean's short name within its blend: "브라질 Cerrado", else its farm, its name, or "원두 2". */
    fun label(bean: BlendComponent, index: Int): String =
        listOf(bean.country, bean.region).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            .ifEmpty { bean.farmProducer.trim() }.ifEmpty { bean.name.trim() }.ifEmpty { "원두 ${index + 1}" }

    /** "브라질 Cerrado 60% · 에티오피아 40%" for a café blend's beans. */
    fun summary(beans: List<BlendComponent>): String =
        beans.mapIndexed { i, b -> label(b, i) + (percentText(b.percent)?.let { " $it" } ?: "") }.joinToString(" · ")
}
