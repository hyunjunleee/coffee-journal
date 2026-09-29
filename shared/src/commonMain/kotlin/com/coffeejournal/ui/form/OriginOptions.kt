package com.coffeejournal.ui.form

import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.OriginRegions
import com.coffeejournal.domain.rules.CountryLookup
import com.coffeejournal.domain.rules.RegionText

/**
 * One choice of a [PresetField] list: [value] goes into the field, [note] (the English name, or where it is) shows
 * faintly beside it, and typing matches [value], [note] and the other spellings in [keys].
 */
data class Preset(val value: String, val note: String = "", val keys: List<String> = emptyList()) {
    fun matches(query: String): Boolean {
        val q = normal(query)
        return q.isEmpty() || (listOf(value, note) + keys).any { normal(it).contains(q) }
    }

    /** Whether [text] is this choice written some way it knows (the field then shows every choice, not just this one). */
    fun names(text: String): Boolean = normal(text).let { t -> t.isNotEmpty() && (listOf(value, note) + keys).any { normal(it) == t } }

    private companion object {
        fun normal(s: String) = s.lowercase().filterNot { it.isWhitespace() || it == '·' || it == '-' || it == '\'' }
    }
}

/**
 * The 국가 · 지역 · 세부 지역 lists of the bean forms ([OriginRegions], and [CoffeeCountries] for the countries). 지역 lists
 * the country's regions (every country's when the country is not one of them); 세부 지역 lists the places inside the
 * chosen region, a place inside a place written as its path ("벤사 › 코코세").
 */
object OriginOptions {
    val countries: List<Preset> by lazy { CoffeeCountries.all.map { Preset(it.ko, it.en) } }

    fun regions(country: String): List<Preset> {
        val origin = originOf(country)
        if (origin != null) return origin.regions.map { Preset(it.ko, it.en, it.aliases) }
        // a country without compiled regions keeps the map's regions; an unknown one offers all, each with its country
        CountryLookup.lookup(country)?.let { c -> return c.regions.map { Preset(it.name) } }
        return OriginRegions.all.flatMap { o ->
            val ko = CoffeeCountries.byEn[o.countryEn]?.ko ?: o.countryEn
            o.regions.map { Preset(it.ko, "${it.en} · $ko", it.aliases) }
        }
    }

    fun subRegions(country: String, region: String): List<Preset> {
        val place = placeOf(country, region) ?: return emptyList()
        return paths(place.subs, prefixKo = "", prefixEn = "")
    }

    /** The compiled region [region] names (its Korean or English name or another spelling), in [country] when known. */
    fun placeOf(country: String, region: String): OriginRegions.Place? {
        val r = region.trim()
        if (r.isEmpty()) return null
        // a known country looks only at its own regions; no country (or an unknown one) at every country's
        val known = CountryLookup.lookup(country)
        val candidates = if (known != null) OriginRegions.all.filter { it.countryEn == known.en } else OriginRegions.all
        return candidates.firstNotNullOfOrNull { o -> o.regions.firstOrNull { p -> Preset(p.ko, p.en, p.aliases).names(r) } }
    }

    private fun originOf(country: String): OriginRegions.Origin? {
        val c = CountryLookup.lookup(country) ?: return null
        return OriginRegions.all.firstOrNull { it.countryEn == c.en }
    }

    private fun paths(places: List<OriginRegions.Place>, prefixKo: String, prefixEn: String): List<Preset> = places.flatMap { p ->
        val ko = prefixKo + p.ko
        val en = prefixEn + p.en
        listOf(Preset(ko, en, p.aliases)) + paths(p.subs, ko + RegionText.SHOWN, en + RegionText.SHOWN)
    }
}
