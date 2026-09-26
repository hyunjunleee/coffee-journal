package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.GeoPoint

/**
 * Links that open a place in Naver Map, Kakao Map or Google Maps. Only the documented public URL formats are used,
 * none needs an API key, and the app itself sends nothing anywhere: the map app opens with what the link says.
 *
 * - Naver Map app URL scheme (https://guide.ncloud-docs.com/docs/maps-url-scheme): `nmap://place?lat=…&lng=…&name=…`
 *   for a position inside the scheme's range (lat 31.43–44.35, lng 122.37–132.00), `nmap://search?query=…` otherwise,
 *   both with the mandatory `appname` (the Android applicationId). When the app is not installed the web map's search
 *   `https://map.naver.com/p/search/{검색어}` opens instead.
 * - Kakao Map URL (https://apis.map.kakao.com/web/guide/, "지도 URL"): `https://map.kakao.com/link/map/{이름},{위도},{경도}`
 *   or `https://map.kakao.com/link/search/{검색어}`; it opens the Kakao Map app or the web map.
 * - Google Maps URLs (https://developers.google.com/maps/documentation/urls/get-started):
 *   `https://www.google.com/maps/search/?api=1&query=…`, offered for overseas places where the Korean maps are thin.
 */
object MapLinks {
    const val APP_NAME = "com.coffeejournal.app"

    /** [uri] is tried first; [fallback] opens when no app on the device handles it. */
    data class Link(val label: String, val uri: String, val fallback: String? = null)

    const val NAVER_LABEL = "네이버 지도에서 열기"
    const val KAKAO_LABEL = "카카오맵에서 열기"
    const val GOOGLE_LABEL = "Google 지도에서 열기"

    /**
     * The search words for a place: its name and where it is, without the country for Korean places
     * ("커피 리브레 서울특별시 성동구"). Blank parts are left out.
     */
    fun query(name: String, location: String): String {
        val where = location.replace("대한민국", " ").replace(Regex("\\s+"), " ").trim()
        return listOf(name.trim(), where).filter { it.isNotEmpty() }.distinct().joinToString(" ")
    }

    fun naver(name: String, location: String, point: GeoPoint?): Link {
        val q = query(name, location)
        val uri = if (point != null && point.lat in 31.43..44.35 && point.lng in 122.37..132.0) {
            "nmap://place?lat=${coord(point.lat)}&lng=${coord(point.lng)}&name=${encode(name.trim().ifEmpty { q })}&appname=$APP_NAME"
        } else {
            "nmap://search?query=${encode(q)}&appname=$APP_NAME"
        }
        return Link(NAVER_LABEL, uri, "https://map.naver.com/p/search/${encode(q)}")
    }

    fun kakao(name: String, location: String, point: GeoPoint?): Link {
        val uri = if (point != null) {
            // the name is one comma-separated field of the path, so its own commas are escaped too
            "https://map.kakao.com/link/map/${encode(name.trim().ifEmpty { query(name, location) })},${coord(point.lat)},${coord(point.lng)}"
        } else {
            "https://map.kakao.com/link/search/${encode(query(name, location))}"
        }
        return Link(KAKAO_LABEL, uri)
    }

    fun google(name: String, location: String, point: GeoPoint?): Link {
        val q = if (point != null) "${coord(point.lat)},${coord(point.lng)}" else query(name, location)
        return Link(GOOGLE_LABEL, "https://www.google.com/maps/search/?api=1&query=${encode(q)}")
    }

    /** Naver and Kakao for a Korean place; Google Maps first, then the Korean two, for an overseas one. */
    fun forPlace(name: String, location: String, point: GeoPoint?, overseas: Boolean): List<Link> =
        if (overseas) listOf(google(name, location, point), naver(name, location, point), kakao(name, location, point))
        else listOf(naver(name, location, point), kakao(name, location, point))

    /** Seven decimals (about 1 cm), no exponent, no trailing zeros. */
    fun coord(v: Double): String {
        val scaled = kotlin.math.round(v * 10_000_000.0).toLong()
        val neg = scaled < 0
        val abs = if (neg) -scaled else scaled
        val whole = abs / 10_000_000L
        val frac = (abs % 10_000_000L).toString().padStart(7, '0').trimEnd('0')
        return (if (neg) "-" else "") + whole + (if (frac.isEmpty()) "" else ".$frac")
    }

    /** RFC 3986 percent-encoding of UTF-8: everything but A–Z a–z 0–9 - . _ ~ (a space is %20, never +). */
    fun encode(s: String): String {
        val out = StringBuilder()
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') out.append(ch)
            else out.append('%').append(HEX[c shr 4]).append(HEX[c and 15])
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
