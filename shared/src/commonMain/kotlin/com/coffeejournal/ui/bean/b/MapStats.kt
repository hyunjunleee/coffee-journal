package com.coffeejournal.ui.bean.b

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.rules.CountryLookup
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.RegionHierarchy
import com.coffeejournal.ui.bean.KoreanOrder

data class RegionStat(val label: String, val count: Int, val subs: Map<String, Int>)

/** Web mapCountryStats entry: cups per country and primary regions keyed by lower-case name. */
data class CountryStat(val cups: Int, val regions: Map<String, RegionStat>)

data class TotalCups(val brew: Int, val cafe: Int, val cupping: Int) { val total: Int get() = brew + cafe + cupping }

/** Farm inside a region: visits plus the sub-region strings that appeared with it. */
data class FarmVisits(val farm: String, val visits: List<BeanRecord>, val subs: List<String>)

data class RegionGroup(val region: String, val total: Int, val farms: List<FarmVisits>)

/** Web visitRowsHtml: home brews collapsed into one range line, cafe/cupping visits listed one by one. */
data class VisitSummary(val brewRange: String, val brewCount: Int, val processes: List<String>, val varieties: List<String>, val others: List<BeanRecord>)

data class CountryListEntry(val country: CoffeeCountries.Country, val records: List<BeanRecord>, val latest: String, val tags: List<String>, val regions: List<Pair<String, List<BeanRecord>>>)

data class ZoneSection(val zone: String, val english: String, val countries: List<CountryListEntry>)

/** Pure aggregation behind the coffee map (web renderCoffeeMap). */
object MapStats {
    const val UNKNOWN_REGION = "지역 미상"
    const val UNKNOWN_FARM = "(농장 미상)"

    fun countryOf(record: BeanRecord): CoffeeCountries.Country? =
        CountryLookup.lookup(record.country) ?: CountryLookup.lookup(record.name)

    fun compute(records: List<BeanRecord>): Map<String, CountryStat> {
        val cups = LinkedHashMap<String, Int>()
        val regions = LinkedHashMap<String, LinkedHashMap<String, Triple<String, Int, MutableMap<String, Int>>>>()
        records.forEach { r ->
            val c = countryOf(r) ?: return@forEach
            cups[c.en] = (cups[c.en] ?: 0) + 1
            val h = RegionHierarchy.parse(r.region)
            if (h.primary.isNotEmpty()) {
                val map = regions.getOrPut(c.en) { LinkedHashMap() }
                val key = h.primary.lowercase()
                val prev = map[key] ?: Triple(h.primary, 0, mutableMapOf())
                if (h.full.isNotEmpty()) prev.third[h.full] = (prev.third[h.full] ?: 0) + 1
                map[key] = Triple(prev.first, prev.second + 1, prev.third)
            }
        }
        return cups.mapValues { (en, n) ->
            CountryStat(n, regions[en]?.mapValues { (_, t) -> RegionStat(t.first, t.second, t.third) } ?: emptyMap())
        }
    }

    fun recordsByCountry(records: List<BeanRecord>): Map<String, List<BeanRecord>> {
        val out = LinkedHashMap<String, MutableList<BeanRecord>>()
        records.forEach { r -> countryOf(r)?.let { out.getOrPut(it.en) { mutableListOf() }.add(r) } }
        return out
    }

    fun regionTried(stats: Map<String, CountryStat>, en: String, region: String): Boolean =
        stats[en]?.regions?.containsKey(region.lowercase()) == true

    /** Web renderTotalCups: brews and cafes count once, a cupping counts its beans (at least one). */
    fun totalCups(entries: List<Entry>): TotalCups = TotalCups(
        brew = entries.count { it.category.ifBlank { Category.BEAN } == Category.BEAN },
        cafe = entries.count { it.category == Category.CAFE },
        cupping = entries.filter { it.category == Category.CUPPING }.sumOf { maxOf(1, it.cuppingBeans.size) },
    )

    private fun regionOrder(a: String, b: String): Int = when {
        a == UNKNOWN_REGION -> 1
        b == UNKNOWN_REGION -> -1
        else -> KoreanOrder.compare(a, b)
    }

    /** Country panel: primary region → farm → visits, unknown region last. */
    fun groupByRegionThenFarm(records: List<BeanRecord>): List<RegionGroup> {
        val byRegion = LinkedHashMap<String, LinkedHashMap<String, MutableList<BeanRecord>>>()
        records.forEach { r ->
            val region = RegionHierarchy.parse(r.region).primary.ifEmpty { UNKNOWN_REGION }
            val farm = r.farmProducer.trim().ifEmpty { UNKNOWN_FARM }
            byRegion.getOrPut(region) { LinkedHashMap() }.getOrPut(farm) { mutableListOf() }.add(r)
        }
        return byRegion.entries.sortedWith { a, b -> regionOrder(a.key, b.key) }.map { (region, farms) ->
            RegionGroup(region, farms.values.sumOf { it.size }, farms.map { (f, v) -> FarmVisits(f, v, emptyList()) })
        }
    }

    /** Region-dot panel: records of one primary region grouped by farm, most visits first. */
    fun farmsForRegion(countryRecords: List<BeanRecord>, region: String): List<FarmVisits> {
        val key = region.lowercase()
        val byFarm = LinkedHashMap<String, Pair<MutableList<BeanRecord>, LinkedHashSet<String>>>()
        countryRecords.forEach { r ->
            val h = RegionHierarchy.parse(r.region)
            if (h.primary.lowercase() != key) return@forEach
            val farm = r.farmProducer.trim().ifEmpty { UNKNOWN_FARM }
            val slot = byFarm.getOrPut(farm) { Pair(mutableListOf(), LinkedHashSet()) }
            slot.first.add(r)
            if (h.sub.isNotEmpty()) slot.second.add(h.sub)
        }
        return byFarm.map { (f, p) -> FarmVisits(f, p.first, p.second.toList()) }.sortedByDescending { it.visits.size }
    }

    fun visitSummary(visits: List<BeanRecord>): VisitSummary {
        val brews = visits.filter { it.category.ifBlank { Category.BEAN } == Category.BEAN }.sortedBy { it.createdAt }
        val others = visits.filter { it.category.ifBlank { Category.BEAN } != Category.BEAN }.sortedByDescending { it.createdAt }
        val range = if (brews.isEmpty()) "" else {
            val first = Dates.ymdCompact(brews.first().createdAt); val last = Dates.ymdCompact(brews.last().createdAt)
            if (first == last) first else "$first ~ $last"
        }
        return VisitSummary(
            brewRange = range, brewCount = brews.size,
            processes = brews.map { it.process.trim() }.filter { it.isNotEmpty() }.distinct(),
            varieties = brews.map { it.variety.trim() }.filter { it.isNotEmpty() }.distinct(),
            others = others,
        )
    }

    /** Web visitLineHtml: "2026.7.15 · 장소 · 가공 · 품종" (the category badge is drawn separately). */
    fun visitLine(r: BeanRecord): String = listOf(Dates.ymdCompact(r.createdAt), r.place.trim(), r.process.trim(), r.variety.trim())
        .filter { it.isNotEmpty() }.joinToString(" · ")

    fun kindLabel(r: BeanRecord): String = when (r.category) { Category.CUPPING -> "커핑"; Category.CAFE -> "카페"; else -> "직접 내림" }

    fun countryEntry(country: CoffeeCountries.Country, records: List<BeanRecord>): CountryListEntry {
        val sorted = records.sortedByDescending { it.createdAt }
        val tags = sorted.flatMap { listOf(RegionHierarchy.parse(it.region).primary, it.variety, it.process) }
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8)
        val byRegion = LinkedHashMap<String, MutableList<BeanRecord>>()
        sorted.forEach { r -> byRegion.getOrPut(RegionHierarchy.parse(r.region).primary.ifEmpty { UNKNOWN_REGION }) { mutableListOf() }.add(r) }
        val regions = byRegion.entries.sortedWith { a, b -> regionOrder(a.key, b.key) }.map { it.key to it.value.toList() }
        return CountryListEntry(country, sorted, sorted.firstOrNull()?.let { Dates.md(it.createdAt) } ?: "", tags, regions)
    }

    /** Visited countries per zone, most coffees first then Korean name. */
    /**
     * The world map's TalkBack label: which countries are coloured as tasted, and where the same information can be
     * reached without the canvas (the country list and the producers still to try, both below the map).
     */
    fun mapDescription(visited: Set<String>): String {
        val names = visited.map { en -> CoffeeCountries.byEn[en]?.ko ?: en }.sorted()
        return if (names.isEmpty()) "커피 지도. 아직 마셔본 나라가 없어요. 커피 생산국은 지도 아래 '경험할 생산국' 목록에 있어요."
        else "커피 지도. 마셔본 나라 ${names.size}곳: ${names.joinToString(", ")}. 나라별 기록은 지도 아래 '경험해본 산지' 목록에 있어요."
    }

    fun zoneSections(byCountry: Map<String, List<BeanRecord>>): List<ZoneSection> =
        CoffeeCountries.zoneOrder.mapNotNull { zone ->
            val countries = byCountry.keys.filter { CountryLookup.zoneOf(it) == zone }
                .mapNotNull { en -> CoffeeCountries.byEn[en]?.let { countryEntry(it, byCountry[en].orEmpty()) } }
                .sortedWith(compareByDescending<CountryListEntry> { it.records.size }.thenBy { it.country.ko })
            if (countries.isEmpty()) null else ZoneSection(zone, CoffeeCountries.zoneEnglish[zone] ?: zone, countries)
        }

    /** Producing countries not yet tasted, grouped by zone in zone order. */
    fun untriedByZone(visited: Set<String>): List<Pair<String, List<CoffeeCountries.Country>>> =
        (CoffeeCountries.zoneOrder + "기타").mapNotNull { zone ->
            val list = CoffeeCountries.all.filter { it.en !in visited && CountryLookup.zoneOf(it.en) == zone }.sortedBy { it.ko }
            if (list.isEmpty()) null else (CoffeeCountries.zoneEnglish[zone] ?: zone) to list
        }
}
