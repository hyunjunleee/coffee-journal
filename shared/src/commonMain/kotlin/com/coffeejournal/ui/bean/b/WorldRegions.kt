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
    /** From this zoom on (1 = the fitted map) the added dots are drawn as well as the web's. */
    const val DETAIL_SCALE = 2f

    /** A dot on the map; [web] for one of the web's own. */
    data class Dot(val country: CoffeeCountries.Country, val region: CoffeeCountries.Region, val web: Boolean)

    private val added: Map<String, List<CoffeeCountries.Region>> by lazy {
        CoffeeCountries.all.associate { c ->
            val taken = c.regions.map { it.name.lowercase() }.toSet()
            c.en to OriginRegions.all.filter { it.countryEn == c.en }.flatMap { it.regions }
                .filter { p -> p.lat != null && p.lng != null && (listOf(p.en) + p.aliases).none { it.lowercase() in taken } }
                .distinctBy { it.en.lowercase() }
                .map { p ->
                    val v = WorldProjection.toView(p.lat!!, p.lng!!)
                    CoffeeCountries.Region(p.en, v.x.toFloat(), v.y.toFloat())
                }
        }
    }

    /** [c]'s regions, the web's first: what the country's panel lists. */
    fun of(c: CoffeeCountries.Country): List<CoffeeCountries.Region> = c.regions + added[c.en].orEmpty()

    /** Every dot: all the web's, then the added ones in the lists' order. */
    val dots: List<Dot> by lazy {
        CoffeeCountries.all.flatMap { c -> c.regions.map { Dot(c, it, web = true) } } +
            CoffeeCountries.all.flatMap { c -> added[c.en].orEmpty().map { Dot(c, it, web = false) } }
    }

    /** The web's dots, all the fitted map shows. */
    val webDots: List<Dot> by lazy { dots.filter { it.web } }

    /**
     * The dots drawn (and tapped) at the zoom [scale]: at the fitted map only the web's, since a dot for every region
     * would bury Colombia or Rwanda; from [DETAIL_SCALE] on the added ones too, each only where it is at least [minGap]
     * view units from every dot already kept, so dots never pile up (the country's panel still lists them all).
     */
    fun shown(scale: Float, minGap: Float): List<Dot> {
        if (scale < DETAIL_SCALE) return webDots
        val kept = webDots.toMutableList()
        val gap2 = minGap * minGap
        dots.filterNot { it.web }.forEach { d ->
            if (kept.none { k -> val dx = k.region.x - d.region.x; val dy = k.region.y - d.region.y; dx * dx + dy * dy < gap2 }) kept += d
        }
        return kept
    }
}
