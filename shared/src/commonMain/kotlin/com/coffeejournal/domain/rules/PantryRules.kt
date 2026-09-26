package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.roundToLong

object PantryRules {
    enum class RoastGroup(val label: String, val peakFrom: Int, val peakTo: Int) {
        LIGHT("라이트", 14, 45), MEDIUM("미디엄", 7, 30), DARK("다크", 4, 21)
    }

    /** Roast family for the peak window. Unlike the web, "미디엄 라이트" counts as light. */
    fun roastGroup(roastLevel: String?): RoastGroup {
        val v = (roastLevel ?: "").lowercase()
        return when {
            Regex("미디엄 다크|medium dark|다크|dark").containsMatchIn(v) -> RoastGroup.DARK
            Regex("미디엄 라이트|medium light|라이트|light").containsMatchIn(v) -> RoastGroup.LIGHT
            Regex("미디엄|medium").containsMatchIn(v) -> RoastGroup.MEDIUM
            else -> RoastGroup.LIGHT
        }
    }

    fun peakWindow(item: PantryItem): Pair<LocalDate, LocalDate>? {
        val manualStart = Dates.parseIsoDate(item.peakStart)
        val manualEnd = Dates.parseIsoDate(item.peakEnd)
        if (manualStart != null || manualEnd != null) {
            return Pair(manualStart ?: manualEnd!!, manualEnd ?: manualStart!!)
        }
        val roast = Dates.parseIsoDate(item.roastDate) ?: return null
        val g = roastGroup(item.roastLevel)
        return Pair(Dates.plusDays(roast, g.peakFrom), Dates.plusDays(roast, g.peakTo))
    }

    /** Web pantryDrinkWindowText. */
    fun drinkWindowText(item: PantryItem): String {
        val manualStart = Dates.parseIsoDate(item.peakStart)
        val manualEnd = Dates.parseIsoDate(item.peakEnd)
        if (manualStart != null || manualEnd != null) {
            val s = manualStart?.let { Dates.ymdCompact(it) }
            val e = manualEnd?.let { Dates.ymdCompact(it) }
            return "예상 피크 " + listOfNotNull(s, e).joinToString(" ~ ")
        }
        val roast = Dates.parseIsoDate(item.roastDate) ?: return ""
        val g = roastGroup(item.roastLevel)
        return "예상 피크 ${Dates.ymdCompact(Dates.plusDays(roast, g.peakFrom))} ~ ${Dates.ymdCompact(Dates.plusDays(roast, g.peakTo))} · ${g.label} 기준"
    }

    /** Web pantryPeakStartTimestamp: sort key, +∞ when unknown. */
    fun peakStartMillis(item: PantryItem): Long {
        Dates.parseIsoDate(item.peakStart)?.let { return Dates.startOfDayMillis(it) }
        val roast = Dates.parseIsoDate(item.roastDate) ?: return Long.MAX_VALUE
        return Dates.startOfDayMillis(Dates.plusDays(roast, roastGroup(item.roastLevel).peakFrom))
    }

    /** Web pantryPriceText: "200g · 18,000원 · 100g 환산 9,000원". "NaN"/"Infinity" weights count as missing (web `!(weight > 0)`). */
    fun priceText(weight: String, price: String): String {
        val (w, p) = weightAndPrice(weight, price) ?: return ""
        return "${Prices.trimNumber(w)}g · ${Prices.format(p)}원 · 100g 환산 ${Prices.format(per100(p, w))}원"
    }

    fun unitPriceText(weight: String, price: String): String {
        val (w, p) = weightAndPrice(weight, price) ?: return ""
        return "100g 환산가 · ${Prices.format(per100(p, w))}원"
    }

    /** Web guard `if (!(weight > 0) || !(price >= 0)) return ''` with non-finite numbers treated as missing. */
    private fun weightAndPrice(weight: String, price: String): Pair<Double, Double>? {
        val w = Numbers.parse(weight) ?: return null
        val p = Numbers.parse(Prices.normalize(price)) ?: return null
        if (!(w > 0) || !(p >= 0)) return null
        return w to p
    }

    private fun per100(price: Double, weight: Double): Double = (price * 100 / weight).roundToLong().toDouble()

    /** Remaining grams for a bean: bag weight (default 100) minus brews, custom-blend components and lab blends. */
    fun remainingGrams(beanKey: String, bagWeight: Double?, entries: List<Entry>, blends: List<Blend>): Pair<Double, Double> {
        val bag = Numbers.finite(bagWeight)?.takeIf { it > 0 } ?: 100.0
        var used = 0.0
        for (en in entries) {
            if (!Packages.isBrew(en)) continue
            if (BeanNames.coreBeanName(en.name) == beanKey) used += Numbers.parse(en.dose) ?: 0.0
            if (en.beanMode == com.coffeejournal.domain.model.BeanMode.CUSTOM_BLEND) {
                for (c in en.blendComponents) if (BeanNames.coreBeanName(c.name) == beanKey) used += Numbers.parse(c.grams) ?: 0.0
            }
        }
        for (b in blends) for (c in b.beans) if (BeanNames.coreBeanName(c.name) == beanKey) used += Numbers.parse(c.grams) ?: 0.0
        // many huge-but-finite doses can still add up to ±Infinity; an unknown usage leaves the bag untouched
        val left = (bag - used).takeIf { it.isFinite() } ?: bag
        val remaining = max(0.0, (left * 10).roundToLong() / 10.0)
        return Pair(remaining, bag)
    }

    fun packageLabel(item: PantryItem): String = PackageType.label(Packages.pantryPackageType(item))
}

object Prices {
    /** Web normalizePriceValue: digits only. */
    fun normalize(value: String?): String = (value ?: "").filter { it.isDigit() }

    /** Web formatPriceValue: "18,000". */
    fun formatInput(value: String?): String {
        val digits = normalize(value)
        if (digits.isEmpty()) return ""
        return format(digits.toDouble())
    }

    fun format(value: Double): String {
        val whole = value.toLong()
        val s = whole.toString()
        val sb = StringBuilder()
        for ((i, ch) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(ch)
        }
        return sb.toString()
    }

    fun trimNumber(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
