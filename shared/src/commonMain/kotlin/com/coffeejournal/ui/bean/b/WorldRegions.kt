package com.coffeejournal.ui.bean.b

import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.OriginRegions
import com.coffeejournal.ui.map.WorldProjection

/**
 * The region dots of the world map: the web's own (CoffeeCountries, placed by hand) and every other representative
 * region the bean form lists (the 예가체프 · 시다모 level of [OriginRegions], not the places inside them), placed from
 * its centre point with the map's projection. A listed region that is one of the web's dots (by its English name or
 * another spelling) keeps the web's dot. Dots are named in English, as the web's are and as the map groups records.
 */
object WorldRegions {
    private val byCountry: Map<String, List<CoffeeCountries.Region>> by lazy {
        CoffeeCountries.all.associate { c ->
            val own = c.regions
            val taken = own.map { it.name.lowercase() }.toSet()
            val added = OriginRegions.all.filter { it.countryEn == c.en }.flatMap { it.regions }
                .filter { p -> p.lat != null && p.lng != null && (listOf(p.en) + p.aliases).none { it.lowercase() in taken } }
                .distinctBy { it.en.lowercase() }
                .map { p ->
                    val v = WorldProjection.toView(p.lat!!, p.lng!!)
                    CoffeeCountries.Region(p.en, v.x.toFloat(), v.y.toFloat())
                }
            c.en to (own + added)
        }
    }

    /** [c]'s region dots, the web's first. */
    fun of(c: CoffeeCountries.Country): List<CoffeeCountries.Region> = byCountry[c.en] ?: c.regions

    /** Every dot with its country. */
    val all: List<Pair<CoffeeCountries.Country, CoffeeCountries.Region>> by lazy { CoffeeCountries.all.flatMap { c -> of(c).map { c to it } } }
}
