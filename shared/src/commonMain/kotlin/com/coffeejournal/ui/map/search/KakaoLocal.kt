package com.coffeejournal.ui.map.search

import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.rules.MapLinks
import com.coffeejournal.ui.ai.AiConnectionException
import com.coffeejournal.ui.ai.AiHttp
import com.coffeejournal.ui.ai.AiHttpRequest
import com.coffeejournal.ui.ai.AiJson
import com.coffeejournal.ui.ai.arr
import com.coffeejournal.ui.ai.bool
import com.coffeejournal.ui.ai.get
import com.coffeejournal.ui.ai.str

/**
 * Kakao Local (developers.kakao.com, REST, header `Authorization: KakaoAK <REST API key>`): the keyword search for
 * places by name (cafés, roasteries) and the address search for road / lot addresses, both GET with the query in the
 * URL. Used only for 국내 and only with the user's own key ([PlaceSearchService]).
 */
internal object KakaoLocal {
    const val KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
    const val ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address.json"

    sealed interface Outcome {
        /** [more]: Kakao has another page of places for the name ("더 보기"). */
        data class Hits(val hits: List<PlaceHit>, val more: Boolean = false) : Outcome
        /** No reply came back (offline, DNS, timeout). */
        data object Offline : Outcome
        /**
         * Kakao answered but not with places: [keyRejected] for a wrong key (401), [disabled] when the app behind the
         * key may not use the map / local APIs (403, "App(…) disabled OPEN_MAP_AND_LOCAL service": its 카카오맵 ›
         * 사용 설정 is not ON), [quota] when its free quota is used up (429).
         */
        data class Refused(
            val status: Int,
            val keyRejected: Boolean = false,
            val disabled: Boolean = false,
            val quota: Boolean = false,
            val message: String = "",
        ) : Outcome
    }

    fun headers(key: String) = mapOf("Authorization" to "KakaoAK $key")

    /**
     * Up to 15 places for [query] ([page] 2 onwards for "더 보기", Kakao keeps 45); from [near] the nearest first
     * (Kakao then reports each one's distance too).
     */
    fun keywordRequest(key: String, query: String, near: GeoPoint?, size: Int = 15, page: Int = 1): AiHttpRequest {
        val around = near?.let { "&x=${it.lng}&y=${it.lat}&sort=distance" } ?: ""
        val more = if (page > 1) "&page=$page" else ""
        return AiHttpRequest("$KEYWORD_URL?query=${MapLinks.encode(query)}&size=$size$more$around", headers(key))
    }

    fun addressRequest(key: String, query: String): AiHttpRequest =
        AiHttpRequest("$ADDRESS_URL?query=${MapLinks.encode(query)}&size=10", headers(key))

    /**
     * The places for the name, then the address matches; the address matches first when [addressFirst] (a typed
     * address names one building). A failed address step after a good keyword step still gives the places. [Outcome.Hits.more]
     * tells whether the name has another page.
     */
    suspend fun search(http: AiHttp, key: String, query: String, near: GeoPoint?, addressFirst: Boolean = false): Outcome {
        val places = searchPage(http, key, query, near, page = 1)
        if (places !is Outcome.Hits) return places
        val addresses = (send(http, addressRequest(key, query), ::parseAddress) as? Outcome.Hits)?.hits.orEmpty()
        return Outcome.Hits(if (addressFirst) addresses + places.hits else places.hits + addresses, places.more)
    }

    /** One page of places for the name (no address step): the first page of a search, or "더 보기". */
    suspend fun searchPage(http: AiHttp, key: String, query: String, near: GeoPoint?, page: Int): Outcome =
        send(http, keywordRequest(key, query, near, page = page), ::parseKeyword, ::hasMore)

    /** The key check: one keyword search for one place. */
    suspend fun check(http: AiHttp, key: String): Outcome = send(http, keywordRequest(key, "카페", near = null, size = 1), ::parseKeyword)

    private suspend fun send(
        http: AiHttp,
        request: AiHttpRequest,
        parse: (String) -> List<PlaceHit>?,
        more: (String) -> Boolean = { false },
    ): Outcome {
        val response = try {
            http.send(request)
        } catch (e: AiConnectionException) {
            return Outcome.Offline
        }
        if (response.status == 200) {
            return parse(response.body)?.let { Outcome.Hits(it, more(response.body)) } ?: Outcome.Refused(200, message = "not a search reply")
        }
        val message = errorMessage(response.body)
        return when (response.status) {
            401 -> Outcome.Refused(401, keyRejected = true, message = message)
            // a key Kakao knows but may not use for this: in practice the app's 카카오맵 is not switched on
            403 -> Outcome.Refused(403, disabled = true, message = message)
            429 -> Outcome.Refused(429, quota = true, message = message)
            else -> Outcome.Refused(response.status, message = message)
        }
    }

    /** {"errorType": "…", "message": "…"} (or the older {"code": -401, "msg": "…"}). */
    fun errorMessage(body: String): String {
        val root = AiJson.parse(body)
        return (root["message"].str ?: root["msg"].str ?: body).trim().take(300)
    }

    /** Whether a keyword reply says another page follows (meta.is_end false). */
    fun hasMore(body: String): Boolean = AiJson.parseObject(body)?.get("meta")?.get("is_end")?.bool == false

    /** Null when [body] is not a keyword reply; places without a readable position are left out. */
    fun parseKeyword(body: String): List<PlaceHit>? {
        val root = AiJson.parseObject(body) ?: return null
        if (root["documents"] == null) return null
        return root["documents"].arr.mapNotNull { d ->
            val point = point(d["x"].str, d["y"].str) ?: return@mapNotNull null
            val name = d["place_name"].str?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val address = d["road_address_name"].str?.trim()?.takeIf { it.isNotEmpty() } ?: d["address_name"].str?.trim() ?: ""
            val category = d["category_group_name"].str?.trim()?.takeIf { it.isNotEmpty() }
                ?: d["category_name"].str?.substringAfterLast('>')?.trim()?.takeIf { it.isNotEmpty() }
            PlaceHit(name, address, point, category)
        }
    }

    /**
     * Null when [body] is not an address reply. A building's road address is named after the building when Kakao
     * knows it; an area (a 동, a road) is named by its address.
     */
    fun parseAddress(body: String): List<PlaceHit>? {
        val root = AiJson.parseObject(body) ?: return null
        if (root["documents"] == null) return null
        return root["documents"].arr.mapNotNull { d ->
            val point = point(d["x"].str, d["y"].str) ?: return@mapNotNull null
            val matched = d["address_name"].str?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val road = d["road_address"]["address_name"].str?.trim()?.takeIf { it.isNotEmpty() }
            val building = d["road_address"]["building_name"].str?.trim()?.takeIf { it.isNotEmpty() }
            val category = when (d["address_type"].str) {
                "REGION" -> "지역"
                "ROAD" -> "도로"
                else -> "주소"
            }
            PlaceHit(building ?: road ?: matched, road ?: matched, point, category)
        }
    }

    /** Kakao sends the coordinates as strings: x = longitude, y = latitude (WGS84). */
    private fun point(x: String?, y: String?): GeoPoint? = GeoPoint.of(y?.trim()?.toDoubleOrNull(), x?.trim()?.toDoubleOrNull())
}
