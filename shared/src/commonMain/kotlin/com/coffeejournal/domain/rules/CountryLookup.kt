package com.coffeejournal.domain.rules

import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.OriginRegions

object CountryLookup {
    /** Web lookupCountry: synonyms, then substring match on English key or Korean name, insertion order. */
    fun lookup(raw: String?): CoffeeCountries.Country? {
        var q = (raw ?: "").trim().lowercase()
        if (q.isEmpty()) return null
        q = CoffeeCountries.countrySynonyms[q] ?: q
        return CoffeeCountries.all.firstOrNull { c -> q.contains(c.en.lowercase()) || q.contains(c.ko) }
    }

    /** Web formatCountryBilingual. */
    fun bilingual(raw: String?): String {
        val r = (raw ?: "").trim()
        if (r.isEmpty()) return "국가 미상"
        val c = lookup(r) ?: return r
        return "${c.ko}(${c.en})"
    }

    fun zoneOf(en: String): String = CoffeeCountries.zones[en] ?: "기타"
}

data class RegionParts(val primary: String, val sub: String, val full: String)

object RegionHierarchy {
    /**
     * Web normalizeRegionName (exact-match Korean synonyms), then any spelling of a region the bean form lists
     * ([OriginRegions]: "짐마", "Jimma", "Sidama" …) as its one English name, so the map groups them together: the name
     * of the web's map dot for a region that has one ("수마트라" → "Sumatra (Mandheling)"), else the listed English name.
     */
    fun normalize(raw: String?, countryEn: String? = null): String {
        val t = (raw ?: "").trim()
        CoffeeCountries.regionSynonyms[t]?.let { return it }
        val key = t.lowercase()
        // within the record's country only: Honduras's "La Paz" (Marcala) is not Bolivia's La Paz
        val own = countryEn?.let { byCountry[it] }
        if (own != null) return own[key] ?: t
        return spellings[key] ?: t
    }

    /** Per country: every spelling of a listed region (lower case) → its map name. */
    private val byCountry: Map<String, Map<String, String>> by lazy {
        OriginRegions.all.associate { o ->
            val dots = CoffeeCountries.byEn[o.countryEn]?.regions.orEmpty().map { it.name }
            val map = LinkedHashMap<String, String>()
            o.regions.forEach { p ->
                val names = listOf(p.ko, p.en) + p.aliases
                val mapName = dots.firstOrNull { d -> names.any { it.equals(d, ignoreCase = true) } } ?: p.en
                names.forEach { map.getOrPut(it.trim().lowercase()) { mapName } }
            }
            o.countryEn to map
        }
    }

    /** Every spelling of a listed region (lower case) → its map name, for a record without a known country; the first country listing it wins. */
    private val spellings: Map<String, String> by lazy {
        val map = LinkedHashMap<String, String>()
        OriginRegions.all.forEach { o -> byCountry[o.countryEn]?.forEach { (k, v) -> map.getOrPut(k) { v } } }
        map
    }

    /** Web regionHierarchy: "Yirgacheffe, Gedeb, Worka" → primary Yirgacheffe, sub "Gedeb, Worka". */
    fun parse(raw: String?, countryEn: String? = null): RegionParts {
        val parts = (raw ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return RegionParts("", "", "")
        return RegionParts(normalize(parts.first(), countryEn), parts.drop(1).joinToString(", "), parts.joinToString(", "))
    }
}
