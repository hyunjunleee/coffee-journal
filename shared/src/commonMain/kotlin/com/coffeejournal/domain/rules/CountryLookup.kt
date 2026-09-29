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
    fun normalize(raw: String?): String {
        val t = (raw ?: "").trim()
        return CoffeeCountries.regionSynonyms[t] ?: spellings[t.lowercase()] ?: t
    }

    /** Every spelling of a listed region (lower case) → its map name; the first country listing it wins. */
    private val spellings: Map<String, String> by lazy {
        val map = LinkedHashMap<String, String>()
        OriginRegions.all.forEach { o ->
            val dots = CoffeeCountries.byEn[o.countryEn]?.regions.orEmpty().map { it.name }
            o.regions.forEach { p ->
                val names = listOf(p.ko, p.en) + p.aliases
                val mapName = dots.firstOrNull { d -> names.any { it.equals(d, ignoreCase = true) } } ?: p.en
                names.forEach { map.getOrPut(it.trim().lowercase()) { mapName } }
            }
        }
        map
    }

    /** Web regionHierarchy: "Yirgacheffe, Gedeb, Worka" → primary Yirgacheffe, sub "Gedeb, Worka". */
    fun parse(raw: String?): RegionParts {
        val parts = (raw ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return RegionParts("", "", "")
        return RegionParts(normalize(parts.first()), parts.drop(1).joinToString(", "), parts.joinToString(", "))
    }
}
