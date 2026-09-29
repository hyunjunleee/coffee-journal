package com.coffeejournal.ui.bean.b

import androidx.compose.ui.geometry.Offset
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.OriginRegions
import com.coffeejournal.ui.map.WorldMapInsets
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The region dots of the world map: the web's own (CoffeeCountries, placed by hand) and every other representative
 * region the bean form lists (the 예가체프 · 시다모 level of [OriginRegions], not the places inside them), placed from
 * its centre point with the map's projection (Hawaii's in its inset, [WorldMapInsets]). A listed region that is one of
 * the web's dots (by its English name or another spelling) keeps the web's dot. Dots are named in English, as the web's
 * are and as the map groups records; zoomed in, the map writes their Korean names ([label]).
 */
object WorldRegions {
    /** From this zoom on (1 = the fitted map) the added dots are drawn as well as the web's. */
    const val DETAIL_SCALE = 2f

    /**
     * From this zoom on, a dot too close to another is moved to the nearest free spot beside it (a line joins it to
     * where it belongs) instead of being left out, so every region has its dot at the deepest zoom. The move is then
     * at most two dot gaps, about 70 km on the ground.
     */
    const val SPREAD_SCALE = 30f

    /**
     * A dot on the map; [web] for one of the web's own. [trueAt] is where it belongs when [shown] moved it off a
     * neighbour (its [region] then carries the drawn position).
     */
    data class Dot(val country: CoffeeCountries.Country, val region: CoffeeCountries.Region, val web: Boolean, val trueAt: Offset? = null) {
        /** The same region as [other], wherever each is drawn. */
        fun sameAs(other: Dot?): Boolean = other != null && other.country.en == country.en && other.region.name == region.name
    }

    private val added: Map<String, List<CoffeeCountries.Region>> by lazy {
        CoffeeCountries.all.associate { c ->
            val taken = c.regions.map { it.name.lowercase() }.toSet()
            c.en to OriginRegions.all.filter { it.countryEn == c.en }.flatMap { it.regions }
                .filter { p -> p.lat != null && p.lng != null && (listOf(p.en) + p.aliases).none { it.lowercase() in taken } }
                .distinctBy { it.en.lowercase() }
                .map { p ->
                    val v = WorldMapInsets.toView(p.lat!!, p.lng!!)
                    CoffeeCountries.Region(p.en, v.x.toFloat(), v.y.toFloat())
                }
        }
    }

    /** From this zoom on the map writes each dot's name next to it, where there is room. */
    const val LABEL_SCALE = 3f

    private val koreanNames: Map<String, String> by lazy {
        buildMap {
            OriginRegions.all.forEach { o ->
                o.regions.forEach { p -> (listOf(p.en) + p.aliases).forEach { getOrPut("${o.countryEn}|${it.lowercase()}") { p.ko } } }
            }
        }
    }

    /** The name written next to [d]: the region's Korean name as the bean form lists it, else its English name. */
    fun label(d: Dot): String = koreanNames["${d.country.en}|${d.region.name.lowercase()}"] ?: d.region.name

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
     * view units from every dot already kept, so dots never pile up. Below [SPREAD_SCALE] a dot with no room is left out
     * (the country's panel still lists it); from there on it is moved beside its place, away from the neighbour in
     * its way, to the first spot with room (one gap out, else two).
     */
    fun shown(scale: Float, minGap: Float): List<Dot> {
        if (scale < DETAIL_SCALE) return webDots
        val kept = webDots.toMutableList()
        val gap2 = minGap * minGap
        fun nearest(x: Float, y: Float): Dot? = kept.minByOrNull { k -> val dx = k.region.x - x; val dy = k.region.y - y; dx * dx + dy * dy }
        fun free(x: Float, y: Float): Boolean = kept.none { k -> val dx = k.region.x - x; val dy = k.region.y - y; dx * dx + dy * dy < gap2 }
        dots.filterNot { it.web }.forEach { d ->
            val r = d.region
            if (free(r.x, r.y)) { kept += d; return@forEach }
            if (scale < SPREAD_SCALE) return@forEach
            val n = nearest(r.x, r.y)!!.region
            val away = if (n.x == r.x && n.y == r.y) 0.0 else atan2((r.y - n.y).toDouble(), (r.x - n.x).toDouble())
            // straight away from the neighbour first, then turning either way in 30° steps
            val spot = (1..2).asSequence().flatMap { ring ->
                (0 until 12).asSequence().map { k ->
                    val turn = ((k + 1) / 2) * (if (k % 2 == 1) 1 else -1) * PI / 6
                    Offset(r.x + (ring * minGap * cos(away + turn)).toFloat(), r.y + (ring * minGap * sin(away + turn)).toFloat())
                }
            }.firstOrNull { free(it.x, it.y) } ?: return@forEach
            kept += d.copy(region = r.copy(x = spot.x, y = spot.y), trueAt = Offset(r.x, r.y))
        }
        return kept
    }
}
