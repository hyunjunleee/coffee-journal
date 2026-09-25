package com.coffeejournal.domain.rules

import com.coffeejournal.domain.reference.CoffeeCountries

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
    /** Web normalizeRegionName: exact-match Korean synonyms only. */
    fun normalize(raw: String?): String {
        val t = (raw ?: "").trim()
        return CoffeeCountries.regionSynonyms[t] ?: t
    }

    /** Web regionHierarchy: "Yirgacheffe, Gedeb, Worka" → primary Yirgacheffe, sub "Gedeb, Worka". */
    fun parse(raw: String?): RegionParts {
        val parts = (raw ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return RegionParts("", "", "")
        return RegionParts(normalize(parts.first()), parts.drop(1).joinToString(", "), parts.joinToString(", "))
    }
}
