package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.reference.KoreaMapData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** A point in map units: the Korea map's units ([KoreaProjection]) or the world map's viewBox units. */
data class MapXY(val x: Double, val y: Double)

/**
 * The Korea map's projection (the same one tools/korea-map/build_korea_map.py used on the boundaries):
 * equirectangular around 36°N, x = (lng − 124.5)·cos 36°·10000, y = (39 − lat)·10000. One unit is about 11 m in
 * both directions, so distances in units are distances on the ground.
 */
object KoreaProjection {
    private val COS36: Double = cos(36.0 * PI / 180.0)

    /** Kilometres per map unit (one ten-thousandth of a degree of latitude). */
    const val KM_PER_UNIT: Double = 111.32 / KoreaMapData.SCALE

    fun toMap(lat: Double, lng: Double): MapXY =
        MapXY((lng - KoreaMapData.LNG0) * COS36 * KoreaMapData.SCALE, (KoreaMapData.LAT0 - lat) * KoreaMapData.SCALE)

    fun toMap(p: GeoPoint): MapXY = toMap(p.lat, p.lng)

    fun toGeo(x: Double, y: Double): GeoPoint =
        GeoPoint(KoreaMapData.LAT0 - y / KoreaMapData.SCALE, x / (COS36 * KoreaMapData.SCALE) + KoreaMapData.LNG0)

    /** A generous box around South Korea (Baengnyeongdo to Dokdo, Marado to Goseong): the 국내 map shows these. */
    fun inKoreaBox(p: GeoPoint): Boolean = p.lat in 32.8..39.0 && p.lng in 124.0..132.2
}

/** Decoded rings (x0, y0, x1, y1, … in map units) of one area; holes are rings too (even-odd). */
class Outline(val rings: List<IntArray>) {
    val minX: Int
    val minY: Int
    val maxX: Int
    val maxY: Int

    init {
        var a = Int.MAX_VALUE; var b = Int.MAX_VALUE; var c = Int.MIN_VALUE; var d = Int.MIN_VALUE
        rings.forEach { r ->
            var i = 0
            while (i + 1 < r.size) {
                if (r[i] < a) a = r[i]; if (r[i] > c) c = r[i]
                if (r[i + 1] < b) b = r[i + 1]; if (r[i + 1] > d) d = r[i + 1]
                i += 2
            }
        }
        minX = a; minY = b; maxX = c; maxY = d
    }

    val area: Long get() = (maxX - minX).toLong() * (maxY - minY)

    fun boxContains(x: Double, y: Double, slack: Double = 0.0): Boolean =
        x >= minX - slack && x <= maxX + slack && y >= minY - slack && y <= maxY + slack

    /** Even-odd ray casting over every ring. */
    fun contains(x: Double, y: Double): Boolean {
        if (!boxContains(x, y)) return false
        var inside = false
        rings.forEach { r ->
            val n = r.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = r[2 * i].toDouble(); val yi = r[2 * i + 1].toDouble()
                val xj = r[2 * j].toDouble(); val yj = r[2 * j + 1].toDouble()
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }

    /** Distance (map units) from the point to the nearest edge. */
    fun distanceToEdge(x: Double, y: Double): Double {
        var best = Double.MAX_VALUE
        rings.forEach { r ->
            val n = r.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val ax = r[2 * j].toDouble(); val ay = r[2 * j + 1].toDouble()
                val bx = r[2 * i].toDouble(); val by = r[2 * i + 1].toDouble()
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0.0, 1.0)
                val px = ax + t * dx - x; val py = ay + t * dy - y
                val d2 = px * px + py * py
                if (d2 < best) best = d2
                j = i
            }
        }
        return sqrt(best)
    }
}

/** The generated outlines, decoded once and kept (a few tens of thousands of points). */
object KoreaShapes {
    class Province(val info: KoreaMapData.Province, val outline: Outline) {
        val code: String get() = info.code
        val labelPoint: MapXY get() = MapXY(info.labelX.toDouble(), info.labelY.toDouble())
    }

    class District(val provinceCode: String, val info: KoreaMapData.District, val outline: Outline) {
        val code: String get() = info.code
        val labelPoint: MapXY get() = MapXY(info.labelX.toDouble(), info.labelY.toDouble())
    }

    val provinces: List<Province> by lazy { KoreaMapData.provinces.map { Province(it, Outline(KoreaMapData.decodeRings(it.outline))) } }

    // lazy is synchronized: derivations run on background dispatchers while the map draws on the main thread
    private val districtLists: Map<String, Lazy<List<District>>> = KoreaMapData.provinces.associate { p ->
        p.code to lazy { KoreaMapData.districts(p.code).map { District(p.code, it, Outline(KoreaMapData.decodeRings(it.outline))) } }
    }

    /** The 시·군·구 of one 시·도 (empty for an unknown code). */
    fun districts(provinceCode: String): List<District> = districtLists[provinceCode]?.value ?: emptyList()

    val allDistricts: List<District> by lazy { KoreaMapData.provinces.flatMap { districts(it.code) } }

    fun province(code: String): Province? = provinces.firstOrNull { it.code == code }
}

/**
 * A 시·도 and, when known, its 시·군·구 (or a city made of several 일반구). [center] is where a pin goes when there is no
 * exact position: the label point of the most specific area.
 */
data class KoreaRegion(
    val provinceCode: String,
    val provinceName: String,
    val districtCode: String? = null,
    val districtName: String? = null,
    val center: MapXY,
) {
    /** "서울특별시 성동구", or the 시·도 alone. */
    val label: String get() = if (districtName == null) provinceName else "$provinceName $districtName"
}

/** Point-in-polygon lookup on the 시·군·구 outlines, and the location-text matcher for pins without a position. */
object KoreaRegions {
    /**
     * The 시·군·구 (and so 시·도) containing the position. A point outside every outline (the sea, or a coast cut by the
     * simplification) finds nothing unless [snapKm] > 0, when the nearest 시·군·구 within that distance is taken.
     */
    fun locate(lat: Double, lng: Double, snapKm: Double = 0.0): KoreaRegion? {
        val p = KoreaProjection.toMap(lat, lng)
        return locateMap(p.x, p.y, snapKm)
    }

    fun locateMap(x: Double, y: Double, snapKm: Double = 0.0): KoreaRegion? {
        val districts = KoreaShapes.allDistricts
        // a point on a shared border may fall in both simplified neighbours; the smaller area wins, as on the map
        districts.filter { it.outline.contains(x, y) }.minByOrNull { it.outline.area }?.let { return regionOf(it) }
        if (snapKm <= 0.0) return null
        val reach = snapKm / KoreaProjection.KM_PER_UNIT
        return districts.filter { it.outline.boxContains(x, y, reach) }
            .map { it to it.outline.distanceToEdge(x, y) }
            .filter { it.second <= reach }
            .minByOrNull { it.second }
            ?.let { regionOf(it.first) }
    }

    fun provinceRegion(code: String): KoreaRegion? = KoreaShapes.province(code)?.let { KoreaRegion(it.code, it.info.name, center = it.labelPoint) }

    private fun regionOf(d: KoreaShapes.District): KoreaRegion {
        val province = KoreaShapes.province(d.provinceCode)
        return KoreaRegion(d.provinceCode, province?.info?.name ?: d.provinceCode, d.code, d.info.name, d.labelPoint)
    }

    // ───────────────────────── location text ─────────────────────────

    /**
     * Other ways people write each 시·도 (short names, the names before the 2023–2026 reorganisations, English).
     * Bare "광주" is not here: it names a 경기도 city as well as the former 광주광역시 (see [cityAliases]).
     */
    private val provinceAliases: Map<String, List<String>> = mapOf(
        "서울특별시" to listOf("서울시", "서울", "seoul"),
        "부산광역시" to listOf("부산시", "부산", "busan", "pusan"),
        "대구광역시" to listOf("대구시", "대구", "daegu"),
        "인천광역시" to listOf("인천시", "인천", "incheon"),
        "전남광주통합특별시" to listOf("전남광주", "광주광역시", "전라남도", "전남", "gwangju", "jeonnam", "jeollanam"),
        "대전광역시" to listOf("대전시", "대전", "daejeon"),
        "울산광역시" to listOf("울산시", "울산", "ulsan"),
        "세종특별자치시" to listOf("세종시", "세종", "sejong"),
        "경기도" to listOf("경기", "gyeonggi"),
        "강원특별자치도" to listOf("강원도", "강원", "gangwon"),
        "충청북도" to listOf("충청북", "충북", "chungcheongbuk", "chungbuk"),
        "충청남도" to listOf("충청남", "충남", "chungcheongnam", "chungnam"),
        "전북특별자치도" to listOf("전라북도", "전라북", "전북", "jeollabuk", "jeonbuk"),
        "경상북도" to listOf("경상북", "경북", "gyeongsangbuk", "gyeongbuk"),
        "경상남도" to listOf("경상남", "경남", "gyeongsangnam", "gyeongnam"),
        "제주특별자치도" to listOf("제주도", "제주", "jeju"),
    )

    /** Everyday names of cities that the data splits into 일반구 (or that are gone as a 시·도). */
    private val cityAliases: Map<String, List<String>> = mapOf("광주" to listOf("광주광역시", "gwangju"))

    /** 인천's districts before the 2026 reorganisation, by the name they have now. */
    private val renamedDistricts: Map<Pair<String, String>, String> = mapOf(
        ("인천광역시" to "남구") to "미추홀구",
        ("인천광역시" to "중구") to "제물포구",
        ("인천광역시" to "동구") to "제물포구",
        ("인천광역시" to "서구") to "서해구",
    )

    private val noise = listOf("대한민국", "south korea", "republic of korea", "korea", "한국")

    private fun tokens(text: String): List<String> {
        var t = text.lowercase()
        noise.forEach { t = t.replace(it, " ") }
        return t.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    }

    /** "성동구" → "성동", only when at least two letters remain (never "중" from 중구). */
    private fun stem(name: String): String? =
        if (name.length >= 3 && name.last() in "시군구") name.dropLast(1) else null

    private class ProvinceNames(val code: String, val name: String, val names: List<String>)

    private val provinceNames: List<ProvinceNames> by lazy {
        KoreaMapData.provinces.map { p ->
            val all = (listOf(p.name, p.short) + provinceAliases[p.name].orEmpty()).map { it.lowercase() }.distinct().sortedByDescending { it.length }
            ProvinceNames(p.code, p.name, all)
        }
    }

    /**
     * Finds the 시·도 and, if the text names one, the 시·군·구 (or city) of a free-form location such as
     * "서울특별시 성동구 성수동", "서울 성동", "경기도 수원시 장안구", "부산 해운대" or "Jeju". Returns null when not even a
     * 시·도 can be told. A district name that several 시·도 share ("중구", "고성군") counts only with its 시·도.
     */
    fun matchText(text: String): KoreaRegion? {
        val raw = tokens(text)
        if (raw.isEmpty()) return null
        // the 시·도 is the first token that starts with one of its names; a glued rest ("서울특별시성동구") is kept as a word
        var province: ProvinceNames? = null
        val words = ArrayList<String>()
        for (tok in raw) {
            val hit = provinceNames.firstNotNullOfOrNull { p -> p.names.firstOrNull { tok.startsWith(it) }?.let { p to it } }
            if (hit == null) { words += tok; continue }
            if (province == null) province = hit.first
            val rest = tok.removePrefix(hit.second)
            // "제주시", "부산진구": the whole token may itself be a district, so it stays a word next to its rest
            if (rest.isNotEmpty()) { words += tok; if (rest.length >= 2) words += rest }
        }
        val p = province
        val scope = if (p != null) KoreaShapes.districts(p.code) else KoreaShapes.allDistricts

        if (p != null) {
            renamedDistricts.entries.firstOrNull { (k, _) -> k.first == p.name && k.second in words }?.let { (_, now) ->
                scope.firstOrNull { it.info.name == now }?.let { return regionOf(it) }
            }
        }
        val cityHits = KoreaMapData.cities.filter { c ->
            (p == null || c.provinceCode == p.code) &&
                words.any { w -> w == c.name.lowercase() || w == stem(c.name) || w in cityAliases[c.name].orEmpty() }
        }
        // without a 시·도, a city's everyday name beats a district's stem ("광주" is the metropolis, not 경기 광주시)
        val hits = districtHits(scope, words, allowStems = p != null || cityHits.isEmpty())
        if (cityHits.size == 1) {
            val city = cityHits.single()
            hits.singleOrNull { it.code in city.districtCodes }?.let { return regionOf(it) }
            if (hits.none { it.code in city.districtCodes }) {
                val pn = KoreaShapes.province(city.provinceCode)?.info?.name ?: city.provinceCode
                return KoreaRegion(city.provinceCode, pn, null, city.name, MapXY(city.labelX.toDouble(), city.labelY.toDouble()))
            }
        }
        if (hits.size == 1) return regionOf(hits.single())
        if (hits.isEmpty() && p != null) {
            // a district that moved to another 시·도 (경북 군위군 → 대구) is still found when its full name is unique
            districtHits(KoreaShapes.allDistricts, words, allowStems = false).singleOrNull()?.let { return regionOf(it) }
        }
        return p?.let { provinceRegion(it.code) }
    }

    /** Districts a word names: "장안구", "수원시장안구", "수원 장안" (city + district), "성동구청"; stems ("성동") if allowed. */
    private fun districtHits(pool: List<KoreaShapes.District>, words: List<String>, allowStems: Boolean): List<KoreaShapes.District> =
        pool.filter { d ->
            val parts = d.info.name.lowercase().split(' ')
            val gu = parts.last()
            val guStem = if (allowStems) stem(gu) else null
            words.any { w -> w == gu || w == parts.joinToString("") || w == guStem || (w.length > gu.length && gu.length >= 3 && w.startsWith(gu)) } ||
                (parts.size == 2 && words.zipWithNext().any { (a, b) -> (a == parts[0] || a == stem(parts[0])) && (b == gu || b == stem(gu)) })
        }
}
