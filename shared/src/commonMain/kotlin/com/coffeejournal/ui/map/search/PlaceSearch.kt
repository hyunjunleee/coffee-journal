package com.coffeejournal.ui.map.search

import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.ui.ai.AiHttp
import com.coffeejournal.ui.ai.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One place a search found: its name, its address (road address when there is one) and position, and what kind of
 * place it is when the search says so ("카페", or "지역" / "도로" / "주소" for an area, a road, a bare address).
 * [distanceM] is set when the search measured from somewhere: the current location, or ([distanceFrom]) the area a
 * name was searched around ("장충").
 */
data class PlaceHit(
    val name: String,
    val address: String,
    val point: GeoPoint,
    val category: String? = null,
    val distanceM: Int? = null,
    val distanceFrom: String? = null,
)

/** Which search answered, shown faintly under the results. */
enum class PlaceSource(val label: String) {
    KAKAO("카카오"),
    DEVICE("기기 지도 서비스"),
    OSM("오픈스트리트맵"),
}

/**
 * The phone's own place search, bound in Koin by the platform module, with no key: Android's Geocoder (Google's on
 * most phones), iOS's MKLocalSearch (Apple Maps). Tests bind a fake, so no test ever reaches the network.
 */
interface DevicePlaceSearch {
    /** False where the phone has no such service (a phone without Google's geocoder); the picker then says so. */
    val supported: Boolean

    /**
     * Places for [query], at most about 15: kept to Korea (or biased to it) when [domestic], near [near] when given.
     * Throws [PlaceSearchException] when the service could not answer (offline, refused).
     */
    suspend fun search(query: String, domestic: Boolean, near: GeoPoint?): List<PlaceHit>
}

class PlaceSearchException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Why a search found nothing to show. */
enum class PlaceSearchError { FAILED, UNAVAILABLE, KAKAO_KEY, KAKAO_DISABLED, KAKAO_QUOTA }

/** Kakao's next page of the same name search ("더 보기"): from [near] when given, measured from [from]. */
data class MorePlaces(val query: String, val near: GeoPoint?, val page: Int, val from: String? = null)

sealed interface PlaceSearchResult {
    /**
     * [hits] empty: the search ran and found nothing. [sources]: which searches answered, the first one leading.
     * [notice]: Kakao could not answer and the keyless searches did. [next]: Kakao has more places for the name.
     */
    data class Found(
        val hits: List<PlaceHit>,
        val sources: List<PlaceSource>,
        val notice: String? = null,
        val next: MorePlaces? = null,
    ) : PlaceSearchResult {
        constructor(hits: List<PlaceHit>, source: PlaceSource, notice: String? = null) : this(hits, listOf(source), notice)

        val source: PlaceSource get() = sources.first()
    }

    data class Failed(val error: PlaceSearchError, val source: PlaceSource?) : PlaceSearchResult
}

/** What a Kakao key check found (설정 › 장소 검색 › 키 확인). */
enum class KakaoKeyCheck(val label: String, val ok: Boolean) {
    OK("✓ 키가 맞아요. 국내 검색은 카카오로 찾아요.", true),
    REJECTED("키가 맞지 않아요. REST API 키를 붙여 넣었는지 확인해 주세요.", false),
    DISABLED(PlaceSearchTexts.KAKAO_DISABLED, false),
    QUOTA(PlaceSearchTexts.KAKAO_QUOTA, false),
    OFFLINE("카카오에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.", false),
    FAILED("지금은 확인하지 못했어요. 잠시 뒤 다시 해 보세요.", false),
}

/**
 * Finds places for the location picker. 국내 goes to Kakao Local when the user saved their own Kakao REST API key
 * (설정 › 장소 검색); without one (and always for 해외) the phone's own search and OpenStreetMap ([OsmPhoton]) run side
 * by side and their places are put together. Kakao and OpenStreetMap are reached through the app's [AiHttp] (no HTTP
 * library added) and the Kakao key lives in the same [SecretStore] as the AI keys: never in the app, the repository, a
 * backup or the database. Only the typed words go out, with a position when the search starts from one.
 *
 * Places come before areas and bare addresses unless the words read as an address ("성수이로7길 51"). A name with an
 * area ("프릳츠 장충") that found no place carrying the whole name is searched again: the name alone around the area,
 * nearest first ([aroundArea]), so a brand's branches near there are listed before the area itself.
 */
class PlaceSearchService(
    private val device: DevicePlaceSearch,
    private val http: AiHttp,
    private val secrets: SecretStore,
    private val timeoutMs: Long = 20_000,
) {
    suspend fun kakaoKey(): String? =
        if (!secrets.supported) null else runCatching { secrets.get(KAKAO_SECRET) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** The search a [domestic] (or 해외) query would go to first, or null when none can run on this phone. */
    suspend fun sourceFor(domestic: Boolean): PlaceSource? = when {
        domestic && http.supported && kakaoKey() != null -> PlaceSource.KAKAO
        device.supported -> PlaceSource.DEVICE
        http.supported -> PlaceSource.OSM
        else -> null
    }

    /**
     * Searches [query] ([domestic]: 국내, else 해외). Results outside the scope are left out (a 국내 search keeps the
     * places inside a 시·군·구, a 해외 one the places outside them) and get their distance from [near] when given.
     */
    suspend fun search(query: String, domestic: Boolean, near: GeoPoint? = null): PlaceSearchResult {
        val q = query.trim().take(MAX_QUERY)
        val key = if (domestic && http.supported) kakaoKey() else null
        val first = if (key != null) searchKakao(key, q, near) else searchKeyless(q, domestic, near)
        if (first !is PlaceSearchResult.Found) return first
        val kakao = key != null && first.source == PlaceSource.KAKAO
        return aroundArea(q, domestic, first, if (kakao) key else null)
    }

    /** "더 보기": Kakao's [next] page of the same name search, in the same scope (국내 only). */
    suspend fun more(next: MorePlaces): PlaceSearchResult {
        val key = (if (http.supported) kakaoKey() else null) ?: return PlaceSearchResult.Failed(PlaceSearchError.KAKAO_KEY, PlaceSource.KAKAO)
        val outcome = kakao { KakaoLocal.searchPage(http, key, next.query, next.near, next.page) }
        return when (outcome) {
            is KakaoLocal.Outcome.Hits -> PlaceSearchResult.Found(
                tidy(outcome.hits, domestic = true, near = next.near, from = next.from),
                listOf(PlaceSource.KAKAO),
                next = if (outcome.more) next.copy(page = next.page + 1) else null,
            )
            KakaoLocal.Outcome.Offline -> PlaceSearchResult.Failed(PlaceSearchError.FAILED, PlaceSource.KAKAO)
            is KakaoLocal.Outcome.Refused -> PlaceSearchResult.Failed(refusal(outcome), PlaceSource.KAKAO)
        }
    }

    private suspend fun kakao(block: suspend () -> KakaoLocal.Outcome): KakaoLocal.Outcome = try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        KakaoLocal.Outcome.Offline
    }

    private fun refusal(outcome: KakaoLocal.Outcome.Refused): PlaceSearchError = when {
        outcome.disabled -> PlaceSearchError.KAKAO_DISABLED
        outcome.keyRejected -> PlaceSearchError.KAKAO_KEY
        outcome.quota -> PlaceSearchError.KAKAO_QUOTA
        else -> PlaceSearchError.FAILED
    }

    private suspend fun searchKakao(key: String, q: String, near: GeoPoint?): PlaceSearchResult {
        return when (val outcome = kakao { KakaoLocal.search(http, key, q, near, addressFirst = looksLikeAddress(q)) }) {
            is KakaoLocal.Outcome.Hits -> PlaceSearchResult.Found(
                tidy(outcome.hits, domestic = true, near),
                listOf(PlaceSource.KAKAO),
                next = if (outcome.more) MorePlaces(q, near, page = 2) else null,
            )
            KakaoLocal.Outcome.Offline -> PlaceSearchResult.Failed(PlaceSearchError.FAILED, PlaceSource.KAKAO)
            // the key or the service refused: the keyless searches can still answer
            is KakaoLocal.Outcome.Refused -> {
                val error = refusal(outcome)
                if (!device.supported && !http.supported) return PlaceSearchResult.Failed(error, PlaceSource.KAKAO)
                val notice = when (error) {
                    PlaceSearchError.KAKAO_KEY -> PlaceSearchTexts.FALLBACK_KEY
                    PlaceSearchError.KAKAO_DISABLED -> PlaceSearchTexts.FALLBACK_DISABLED
                    PlaceSearchError.KAKAO_QUOTA -> PlaceSearchTexts.FALLBACK_QUOTA
                    else -> PlaceSearchTexts.FALLBACK_FAILED
                }
                when (val r = searchKeyless(q, domestic = true, near)) {
                    is PlaceSearchResult.Found -> r.copy(notice = notice)
                    is PlaceSearchResult.Failed -> PlaceSearchResult.Failed(error, PlaceSource.KAKAO)
                }
            }
        }
    }

    /**
     * The phone's search and OpenStreetMap side by side: their places together (the phone's first), then areas and
     * addresses (or those first for an address). Null parts did not answer; both silent is a failure.
     */
    private suspend fun searchKeyless(q: String, domestic: Boolean, near: GeoPoint?): PlaceSearchResult {
        if (!device.supported && !http.supported) return PlaceSearchResult.Failed(PlaceSearchError.UNAVAILABLE, null)
        val (phone, osm) = keyless(q, domestic, near)
        if (phone == null && osm == null) return PlaceSearchResult.Failed(PlaceSearchError.FAILED, if (device.supported) PlaceSource.DEVICE else PlaceSource.OSM)
        val sources = listOfNotNull(PlaceSource.DEVICE.takeIf { phone != null }, PlaceSource.OSM.takeIf { !osm.isNullOrEmpty() })
        val hits = ordered(phone.orEmpty() + osm.orEmpty(), addressFirst = looksLikeAddress(q))
        return PlaceSearchResult.Found(tidy(hits, domestic, near), sources.ifEmpty { listOf(PlaceSource.OSM) })
    }

    private suspend fun keyless(q: String, domestic: Boolean, near: GeoPoint?): Pair<List<PlaceHit>?, List<PlaceHit>?> = coroutineScope {
        val phone = async { if (device.supported) phoneHits(q, domestic, near) else null }
        val osm = async { if (http.supported) osmHits(q, domestic, near) else null }
        phone.await() to osm.await()
    }

    private suspend fun phoneHits(q: String, domestic: Boolean, near: GeoPoint?): List<PlaceHit>? = try {
        withTimeout(timeoutMs) { device.search(q, domestic, near) }
    } catch (e: TimeoutCancellationException) {
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun osmHits(q: String, domestic: Boolean, near: GeoPoint?): List<PlaceHit>? = try {
        withTimeout(minOf(timeoutMs, OSM_TIMEOUT_MS)) { OsmPhoton.search(http, q, domestic, near) }
    } catch (e: TimeoutCancellationException) {
        null
    }

    /**
     * "프릳츠 장충": when no place found carries every word, one word is taken as an area (the last, else the first,
     * whichever names an area among the results or is in a found place's address; with Kakao also an area of its own
     * search) and the other words are searched around it. The places found there come first, nearest first with their
     * distance from the area; the first results follow. Nothing found around the area leaves [first] as it was.
     */
    private suspend fun aroundArea(q: String, domestic: Boolean, first: PlaceSearchResult.Found, key: String?): PlaceSearchResult.Found {
        val words = q.split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.size < 2 || looksLikeAddress(q)) return first
        if (first.hits.any { h -> isPlace(h) && words.all { normal(h.name).contains(normal(it)) } }) return first
        val (area, anchor) = anchorIn(words, first.hits) ?: key?.let { anchorBySearch(words, it) } ?: return first
        val name = (words - area).joinToString(" ")
        val (around, next) = if (key != null) {
            when (val outcome = kakao { KakaoLocal.searchPage(http, key, name, anchor, page = 1) }) {
                is KakaoLocal.Outcome.Hits -> outcome.hits to (if (outcome.more) MorePlaces(name, anchor, page = 2, from = area) else null)
                else -> return first
            }
        } else {
            // without Kakao the searches match loosely: keep the places that carry one of the words, close to the area
            val (phone, osm) = keyless(name, domestic, anchor)
            val wanted = (words - area).map(::normal)
            (phone.orEmpty() + osm.orEmpty())
                .filter { h -> isPlace(h) && wanted.any { normal(h.name).contains(it) } && distanceM(anchor, h.point) <= AROUND_M } to null
        }
        val near = tidy(around, domestic, anchor, from = area, cap = MAX_HITS).sortedBy { it.distanceM }
        if (near.isEmpty()) return first
        val sources = (first.sources + if (key == null && around.isNotEmpty()) listOf(PlaceSource.OSM) else emptyList()).distinct()
        val shown = near.map(::identity).toSet()
        return first.copy(hits = near + first.hits.filter { identity(it) !in shown }, sources = sources, next = next)
    }

    private fun anchorIn(words: List<String>, hits: List<PlaceHit>): Pair<String, GeoPoint>? {
        for (area in listOf(words.last(), words.first()).distinct()) {
            val a = normal(area)
            val hit = hits.firstOrNull { isArea(it) && (normal(it.name).contains(a) || normal(it.address).contains(a)) }
                ?: hits.firstOrNull { isPlace(it) && normal(it.address).contains(a) }
            if (hit != null) return area to hit.point
        }
        return null
    }

    /**
     * The area on its own through Kakao, whose address search names areas ("서울 중구 장충동1가"): an area or an
     * address named after the word, not a shop that carries it. (Without Kakao the first search already asked the
     * phone and OpenStreetMap, which name the area among its results when they know it.)
     */
    private suspend fun anchorBySearch(words: List<String>, key: String): Pair<String, GeoPoint>? {
        for (area in listOf(words.last(), words.first()).distinct()) {
            val found = (kakao { KakaoLocal.search(http, key, area, near = null, addressFirst = true) } as? KakaoLocal.Outcome.Hits)?.hits.orEmpty()
            val a = normal(area)
            val hit = found.filter { KoreaRegions.locate(it.point.lat, it.point.lng, snapKm = 3.0) != null }
                .firstOrNull { !isPlace(it) && (normal(it.name).contains(a) || normal(it.address).contains(a)) }
            if (hit != null) return area to hit.point
        }
        return null
    }

    /** 키 확인: one keyword search for one result (Kakao counts it like any search; the free quota is per day). */
    suspend fun checkKakaoKey(): KakaoKeyCheck {
        val key = kakaoKey() ?: return KakaoKeyCheck.REJECTED
        return when (val outcome = kakao { KakaoLocal.check(http, key) }) {
            is KakaoLocal.Outcome.Hits -> KakaoKeyCheck.OK
            KakaoLocal.Outcome.Offline -> KakaoKeyCheck.OFFLINE
            is KakaoLocal.Outcome.Refused -> when {
                outcome.disabled -> KakaoKeyCheck.DISABLED
                outcome.keyRejected -> KakaoKeyCheck.REJECTED
                outcome.quota -> KakaoKeyCheck.QUOTA
                else -> KakaoKeyCheck.FAILED
            }
        }
    }

    suspend fun saveKakaoKey(typed: String) {
        val key = typed.trim().replace(Regex("\\s+"), "")
        if (key.isNotEmpty() && secrets.supported) secrets.put(KAKAO_SECRET, key)
    }

    suspend fun clearKakaoKey() = secrets.delete(KAKAO_SECRET)

    val storeSupported: Boolean get() = secrets.supported

    companion object {
        /** The Kakao key's name in the [SecretStore] (beside the AI keys). */
        const val KAKAO_SECRET = "kakao_local"
        const val MAX_QUERY = 100
        const val MAX_HITS = 15

        /** OpenStreetMap answers within this or is left out (the phone's search may take the full time). */
        const val OSM_TIMEOUT_MS = 10_000L

        /** A place found around an area without Kakao counts when it is this close to it. */
        const val AROUND_M = 30_000

        private val WHITESPACE = Regex("\\s+")

        /** The kinds that name an area, a road or a bare address rather than a place. */
        private val AREA_KINDS = setOf("지역", "도로", "주소")

        /**
         * A word of an address: a number ("51", "300-1"), a road ("성수이로7길", "세종대로"), or an area with its
         * ending ("장충동", "영도구", "성수동2가").
         */
        private val ADDRESS_WORD = Regex("^(\\d+(-\\d+)?|.+(로|길)(\\d+(번)?(길)?)?|.+(시|도|군|구|동|읍|면|리|가)(\\d+)?(가)?)$")

        /** Whether every word of [q] is a word of an address: then addresses come first and no area search runs. */
        fun looksLikeAddress(q: String): Boolean {
            val words = q.trim().split(WHITESPACE).filter { it.isNotEmpty() }
            return words.isNotEmpty() && words.all { ADDRESS_WORD.matches(it) }
        }

        /** A place (a café, a shop), not an area, a road or an address standing in for one. */
        fun isPlace(h: PlaceHit): Boolean = h.category !in AREA_KINDS && normal(h.name) != normal(h.address)

        /** An area or a road: a 동, a 구, a street, or a result that is only its address ("서울특별시 중구 장충동"). */
        fun isArea(h: PlaceHit): Boolean = h.category == "지역" || h.category == "도로" || (h.category == null && normal(h.name) == normal(h.address))

        /** Places first, then areas and addresses, each in the order found; the other way round for an address. */
        fun ordered(hits: List<PlaceHit>, addressFirst: Boolean): List<PlaceHit> {
            val (places, areas) = hits.partition(::isPlace)
            return if (addressFirst) areas + places else places + areas
        }

        /** Lower case without spaces or middle dots: "Blue Bottle 성수" and "bluebottle성수" match. */
        fun normal(s: String): String = s.lowercase().filterNot { it.isWhitespace() || it == '·' }

        /** The same name at about the same spot (within about 55 m) is the same place. */
        private fun identity(h: PlaceHit) = h.name.trim().lowercase() + "@" + (h.point.lat * 2000).roundToInt() + "," + (h.point.lng * 2000).roundToInt()

        /**
         * In scope, each place once (same name at about the same spot), with its distance from [near] (measured from
         * [from] when that names an area rather than the current location), at most [cap].
         */
        fun tidy(hits: List<PlaceHit>, domestic: Boolean, near: GeoPoint?, from: String? = null, cap: Int = MAX_HITS): List<PlaceHit> {
            val seen = HashSet<String>()
            return hits.asSequence()
                .filter { (KoreaRegions.locate(it.point.lat, it.point.lng, snapKm = 3.0) != null) == domestic }
                .filter { seen.add(identity(it)) }
                .map { h -> if (near == null) h else h.copy(distanceM = distanceM(near, h.point), distanceFrom = from) }
                .take(cap)
                .toList()
        }

        /** Great-circle distance in metres (haversine, mean Earth radius). */
        fun distanceM(a: GeoPoint, b: GeoPoint): Int {
            val rad = PI / 180.0
            val dLat = (b.lat - a.lat) * rad
            val dLng = (b.lng - a.lng) * rad
            val h = sin(dLat / 2) * sin(dLat / 2) + cos(a.lat * rad) * cos(b.lat * rad) * sin(dLng / 2) * sin(dLng / 2)
            return (2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))).roundToInt()
        }

        /** "350 m", "1.2 km", "12 km". */
        fun distanceLabel(m: Int): String = when {
            m < 1_000 -> "$m m"
            m < 10_000 -> "${m / 1000}.${(m % 1000) / 100} km"
            else -> "${(m + 500) / 1000} km"
        }
    }
}

/** The picker's and the settings' copy (also read by the tests). */
object PlaceSearchTexts {
    const val FIELD = "주소나 이름으로 찾기"
    const val PLACEHOLDER = "예: 모모스커피 영도, 성수이로7길 51"
    const val SEARCH = "검색"
    const val SEARCHING = "찾는 중…"
    const val HERE = "현재 위치"
    const val LOCATING = "현재 위치를 찾는 중…"
    const val CLOSE = "결과 닫기"
    const val EMPTY_QUERY = "찾을 주소나 이름을 적어 주세요."
    const val FAILED = "검색하지 못했어요. 인터넷 연결을 확인하고 다시 해 보세요."
    const val UNAVAILABLE = "이 기기에서는 장소 검색을 쓸 수 없어요. 지도에서 직접 찍어 주세요."
    const val UNAVAILABLE_KAKAO = " 설정 › 장소 검색에 카카오 키를 넣으면 국내 장소는 찾을 수 있어요."
    const val KAKAO_KEY = "카카오 키가 맞지 않아요. 설정 › 장소 검색에서 키를 확인해 주세요."
    const val KAKAO_DISABLED = "이 키의 앱에서 카카오맵이 꺼져 있어요. 카카오 개발자 사이트의 [앱] › [제품 설정] › [카카오맵]에서 사용 설정 상태를 ON으로 켜 주세요."
    const val KAKAO_QUOTA = "이 키의 카카오 무료 사용량을 다 썼어요. 사용량은 카카오 개발자 사이트의 앱 관리 › 통계 › 쿼터에서 볼 수 있어요."
    const val FALLBACK_KEY = "카카오 키가 맞지 않아 키 없이 찾았어요. 설정 › 장소 검색에서 키를 확인해 주세요."
    const val FALLBACK_DISABLED = "이 키의 앱에서 카카오맵이 꺼져 있어 키 없이 찾았어요. 카카오 개발자 사이트의 [앱] › [제품 설정] › [카카오맵]에서 사용 설정을 켜 주세요."
    const val FALLBACK_QUOTA = "카카오 무료 사용량을 다 써서 키 없이 찾았어요."
    const val FALLBACK_FAILED = "카카오 검색이 지금 안 돼서 키 없이 찾았어요."
    const val PICKED_HINT = "고른 곳이 지도에 표시됐어요. 지도에서 누르면 그 자리로 바뀌어요."

    const val MORE = "더 보기"
    const val MORE_LOADING = "더 찾는 중…"
    const val MORE_FAILED = "더 찾지 못했어요. 다시 눌러 보세요."
    const val OSM_CREDIT = "지도 데이터 © OpenStreetMap 기여자"

    /** Under a keyless 국내 search: what a Kakao key adds, with the link to its how-to. */
    const val KAKAO_TIP = "가게 이름으로 잘 안 나오면 카카오 키(무료)를 넣어 보세요. ‘프릳츠’처럼 지점이 여러 곳인 가게도 지점마다 찾아요."
    const val KAKAO_TIP_LINK = "카카오 키 받는 법 ›"

    fun noResults(query: String) = "‘$query’(으)로 찾은 곳이 없어요. 다른 이름이나 주소로 찾거나 지도에서 직접 찍어 주세요."
    fun source(source: PlaceSource) = "검색: ${source.label}"
    fun source(sources: List<PlaceSource>) = "검색: " + sources.joinToString(" · ") { it.label }
    fun fromHere(m: Int) = "현재 위치에서 ${PlaceSearchService.distanceLabel(m)}"

    /** "현재 위치에서 350 m", or from the area a name was searched around: "장충에서 1.2 km". */
    fun distance(hit: PlaceHit): String? = hit.distanceM?.let { m -> "${hit.distanceFrom ?: HERE}에서 ${PlaceSearchService.distanceLabel(m)}" }

    fun message(error: PlaceSearchError, domestic: Boolean): String = when (error) {
        PlaceSearchError.FAILED -> FAILED
        PlaceSearchError.UNAVAILABLE -> UNAVAILABLE + if (domestic) UNAVAILABLE_KAKAO else ""
        PlaceSearchError.KAKAO_KEY -> KAKAO_KEY
        PlaceSearchError.KAKAO_DISABLED -> KAKAO_DISABLED
        PlaceSearchError.KAKAO_QUOTA -> KAKAO_QUOTA
    }

    // 설정 › 장소 검색
    const val SECTION = "장소 검색"
    const val INTRO = "로스터리·카페 위치를 정할 때 이름이나 주소로 찾는 검색이에요. 키 없이도 휴대폰의 지도 서비스" +
        "(Android는 Google 지오코더, iOS는 Apple 지도)와 오픈스트리트맵으로 함께 찾아요. 두 곳에는 적은 말만 보내요."
    const val KAKAO_LABEL = "카카오 REST API 키 (선택)"
    const val KAKAO_PLACEHOLDER = "REST API 키 붙여넣기"
    const val KAKAO_HINT = "넣으면 국내 검색은 카카오 로컬로 찾아요. 한국 카페·로스터리를 이름으로 훨씬 잘 찾고, ‘프릳츠’처럼 " +
        "지점이 여러 곳이면 지점마다 보여 줘요. 해외는 계속 키 없이 찾아요."
    const val KAKAO_GUIDE = "키는 카카오 개발자 사이트에서 무료로 받아요. 받는 법은 아래 안내에 한 단계씩 적어 두었어요."
    const val KAKAO_GUIDE_OPEN = "카카오 키 받는 법 자세히 ›"
    const val KAKAO_PRIVACY = "키는 이 휴대폰에만 암호화해 저장하고 백업에 넣지 않아요. 검색할 때 적은 말(현재 위치에서 찾으면 그 좌표도)만 카카오로 보내요."
    const val KAKAO_GUIDE_URL = "https://developers.kakao.com/console/app"
    const val STORE_UNSUPPORTED = "이 기기에서는 키를 안전하게 저장할 수 없어 카카오 검색을 쓸 수 없어요."
    const val SAVE = "저장"
    const val CHECK = "키 확인"
    const val CHECKING = "확인 중…"
    const val CLEAR = "지우기"
}
