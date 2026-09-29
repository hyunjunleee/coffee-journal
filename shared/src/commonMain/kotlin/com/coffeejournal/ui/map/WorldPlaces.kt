package com.coffeejournal.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.WorldMapData
import com.coffeejournal.domain.rules.CountryLookup
import com.coffeejournal.domain.rules.MapXY
import com.coffeejournal.ui.bean.b.MapPolygon
import com.coffeejournal.ui.bean.b.WorldMapGeometry

/**
 * The web's world map (WorldMapData, Natural Earth 1:110m) is plate carrée: 8/3 viewBox units per degree, 180°W at
 * x = 0 and the equator at y = 267.35, halfway between the two tropics the web drew at 23.5°. Checked against the
 * outlines' extremes (South Korea, Australia, Iceland …) in WorldProjectionTest.
 */
object WorldProjection {
    const val UNITS_PER_DEGREE: Double = 8.0 / 3.0
    /** Midway between WorldMapData.TROPIC_NORTH_Y (204.7) and TROPIC_SOUTH_Y (330). */
    const val EQUATOR_Y: Double = 267.35

    fun toView(lat: Double, lng: Double): MapXY = MapXY((lng + 180.0) * UNITS_PER_DEGREE, EQUATOR_Y - lat * UNITS_PER_DEGREE)
    fun toView(p: GeoPoint): MapXY = toView(p.lat, p.lng)
    fun toGeo(x: Double, y: Double): GeoPoint = GeoPoint((EQUATOR_Y - y) / UNITS_PER_DEGREE, x / UNITS_PER_DEGREE - 180.0)

    val frame: Rect = Rect(WorldMapData.VIEW_X, WorldMapData.VIEW_Y, WorldMapData.VIEW_X + WorldMapData.VIEW_W, WorldMapData.VIEW_Y + WorldMapData.VIEW_H)
    /** The whole drawn world (Antarctica and the far north included), reached by zooming out. */
    val bounds: Rect = Rect(0f, 20f, 960f, 500f)
}

/** Where an overseas roastery is: a country on the world map (or a city-state it does not draw) and its centre. */
data class WorldPlace(val name: String, val mapName: String?, val center: MapXY)

/**
 * Overseas location texts → a country centre on the world map. The web's roastery places come first (its
 * roasteryPoint groups, one country each), then a few more roastery countries, then the producing countries
 * (CountryLookup, the web's lookupCountry), then any Natural Earth country name written in English.
 */
object WorldPlaces {
    private class Known(val ko: String, val mapName: String?, val pattern: Regex, val fixed: GeoPoint? = null)

    private fun re(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    private val known: List<Known> = listOf(
        Known("미국", "United States of America", re("미국|\\busa\\b|u\\.s\\.a|united states|new york|뉴욕|portland|포틀랜드|california|캘리포니아|seattle|시애틀|san francisco|샌프란시스코|los angeles|chicago|시카고")),
        Known("캐나다", "Canada", re("캐나다|canada|vancouver|밴쿠버|toronto|토론토|montreal|몬트리올")),
        Known("멕시코", "Mexico", re("멕시코|mexico")),
        Known("스페인", "Spain", re("스페인|spain|barcelona|바르셀로나|madrid|마드리드")),
        Known("영국", "United Kingdom", re("영국|united kingdom|\\buk\\b|england|london|런던|scotland|edinburgh|에든버러")),
        Known("프랑스", "France", re("프랑스|france|paris|파리")),
        Known("덴마크", "Denmark", re("덴마크|denmark|copenhagen|코펜하겐")),
        Known("노르웨이", "Norway", re("노르웨이|norway|oslo|오슬로")),
        Known("독일", "Germany", re("독일|germany|berlin|베를린|hamburg|함부르크|munich|뮌헨")),
        Known("이탈리아", "Italy", re("이탈리아|italy|milan|밀라노|rome|로마")),
        Known("일본", "Japan", re("일본|japan|tokyo|도쿄|osaka|오사카|kyoto|교토|fukuoka|후쿠오카")),
        Known("대만", "Taiwan", re("대만|taiwan|taipei|타이베이")),
        Known("중국", "China", re("중국|china|shanghai|상하이|beijing|베이징")),
        Known("호주", "Australia", re("호주|australia|melbourne|멜버른|sydney|시드니")),
        Known("뉴질랜드", "New Zealand", re("뉴질랜드|new zealand|auckland|오클랜드|wellington")),
        Known("태국", "Thailand", re("태국|thailand|bangkok|방콕|chiang mai|치앙마이")),
        Known("싱가포르", null, re("싱가포르|singapore"), GeoPoint(1.3521, 103.8198)),
        Known("홍콩", null, re("홍콩|hong kong"), GeoPoint(22.3193, 114.1694)),
        Known("스웨덴", "Sweden", re("스웨덴|sweden|stockholm|스톡홀름")),
        Known("핀란드", "Finland", re("핀란드|finland|helsinki|헬싱키")),
        Known("네덜란드", "Netherlands", re("네덜란드|netherlands|amsterdam|암스테르담")),
        Known("벨기에", "Belgium", re("벨기에|belgium|brussels|브뤼셀")),
        Known("스위스", "Switzerland", re("스위스|switzerland|zurich|취리히")),
        Known("오스트리아", "Austria", re("오스트리아|austria|vienna|비엔나|wien")),
        Known("아이슬란드", "Iceland", re("아이슬란드|iceland|reykjavik")),
        Known("아일랜드", "Ireland", re("아일랜드|ireland|dublin|더블린")),
        Known("포르투갈", "Portugal", re("포르투갈|portugal|lisbon|리스본|porto")),
        Known("체코", "Czechia", re("체코|czech|prague|프라하")),
        Known("폴란드", "Poland", re("폴란드|poland|warsaw|바르샤바")),
        Known("그리스", "Greece", re("그리스|greece|athens|아테네")),
        Known("튀르키예", "Turkey", re("튀르키예|터키|turkey|türkiye|istanbul|이스탄불")),
        Known("아랍에미리트", "United Arab Emirates", re("아랍에미리트|\\buae\\b|united arab emirates|dubai|두바이")),
        Known("러시아", "Russia", re("러시아|russia|moscow|모스크바")),
        Known("말레이시아", "Malaysia", re("말레이시아|malaysia|kuala lumpur|쿠알라룸푸르")),
        Known("몽골", "Mongolia", re("몽골|mongolia")),
    )

    private val polygons: List<MapPolygon> by lazy { WorldMapGeometry.parseAll() }

    /** Every country's centre, computed once (175 outlines; lazy is synchronized, derivations run off the main thread). */
    private val centers: Map<String, MapXY> by lazy {
        polygons.mapNotNull { poly -> poly.rings.maxByOrNull { ringArea(it) }?.let { poly.name to interiorPoint(it) } }.toMap()
    }

    /** Korean name of a map country when the app knows one (roastery countries and the coffee producers). */
    fun koreanName(mapName: String): String? =
        known.firstOrNull { it.mapName == mapName }?.ko ?: CoffeeCountries.byEn[mapName]?.ko

    /** The name a location field gets for a point picked in [mapName]: Korean when known, else the map's English. */
    fun displayName(mapName: String): String = koreanName(mapName) ?: mapName

    fun match(text: String): WorldPlace? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val k = known.firstOrNull { it.pattern.containsMatchIn(t) }
        val kc = k?.fixed?.let { WorldProjection.toView(it) } ?: k?.mapName?.let { center(it) }
        if (k != null && kc != null) return WorldPlace(k.ko, k.mapName, kc)
        CountryLookup.lookup(t)?.let { c -> center(c.en)?.let { return WorldPlace(c.ko, c.en, it) } }
        val lower = t.lowercase()
        polygons.map { it.name }.sortedByDescending { it.length }.firstOrNull { name ->
            Regex("(^|[^\\p{L}])" + Regex.escape(name.lowercase()) + "($|[^\\p{L}])").containsMatchIn(lower)
        }?.let { name -> center(name)?.let { return WorldPlace(displayName(name), name, it) } }
        return null
    }

    /** The country drawn at a viewBox point, if any (the smallest one, so enclaves win). */
    fun countryAt(p: MapXY): String? = WorldMapGeometry.hitCountry(polygons, Offset(p.x.toFloat(), p.y.toFloat()))?.name

    /**
     * An inside point of the country's largest ring: the middle of the widest horizontal run through it, tried on a
     * few lines across the ring (a centroid can fall outside a curved country such as Norway or Chile).
     */
    fun center(mapName: String): MapXY? = centers[mapName]

    private fun ringArea(r: List<Offset>): Float {
        var a = 0f
        for (i in r.indices) { val p = r[i]; val q = r[(i + 1) % r.size]; a += p.x * q.y - q.x * p.y }
        return kotlin.math.abs(a) / 2f
    }

    internal fun interiorPoint(ring: List<Offset>): MapXY {
        val top = ring.minOf { it.y }
        val bottom = ring.maxOf { it.y }
        var best: MapXY? = null
        var bestWidth = -1f
        for (k in 1..9) {
            val y = top + (bottom - top) * k / 10f
            val xs = ArrayList<Float>()
            for (i in ring.indices) {
                val a = ring[i]; val b = ring[(i + 1) % ring.size]
                if ((a.y > y) != (b.y > y)) xs += a.x + (y - a.y) * (b.x - a.x) / (b.y - a.y)
            }
            xs.sort()
            var j = 0
            while (j + 1 < xs.size) {
                val w = xs[j + 1] - xs[j]
                // the middle band is preferred a little, so a country's centre does not drift to a wide edge
                val weighted = w * (1f - kotlin.math.abs(k - 5) * 0.06f)
                if (weighted > bestWidth) { bestWidth = weighted; best = MapXY(((xs[j] + xs[j + 1]) / 2f).toDouble(), y.toDouble()) }
                j += 2
            }
        }
        return best ?: MapXY(((ring.minOf { it.x } + ring.maxOf { it.x }) / 2f).toDouble(), ((top + bottom) / 2f).toDouble())
    }
}
