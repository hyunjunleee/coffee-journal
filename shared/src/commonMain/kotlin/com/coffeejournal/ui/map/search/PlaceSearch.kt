package com.coffeejournal.ui.map.search

import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.ui.ai.AiHttp
import com.coffeejournal.ui.ai.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One place a search found: its name, its address (road address when there is one) and position, and what kind of
 * place it is when the search says so ("카페"). [distanceM] is set when the search started from the current location.
 */
data class PlaceHit(
    val name: String,
    val address: String,
    val point: GeoPoint,
    val category: String? = null,
    val distanceM: Int? = null,
)

/** Which search answered, shown faintly under the results. */
enum class PlaceSource(val label: String) {
    KAKAO("카카오"),
    DEVICE("기기 지도 서비스"),
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

sealed interface PlaceSearchResult {
    /** [hits] empty: the search ran and found nothing. [notice]: Kakao could not answer and the phone's search did. */
    data class Found(val hits: List<PlaceHit>, val source: PlaceSource, val notice: String? = null) : PlaceSearchResult
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
 * (설정 › 장소 검색), else to the phone's search; 해외 always to the phone's. Kakao is reached through the app's
 * [AiHttp] (no HTTP library added) and the key lives in the same [SecretStore] as the AI keys: never in the app, the
 * repository, a backup or the database. Only the typed words go out, with the current location when the search starts
 * from it (so the nearest places come first).
 */
class PlaceSearchService(
    private val device: DevicePlaceSearch,
    private val http: AiHttp,
    private val secrets: SecretStore,
    private val timeoutMs: Long = 20_000,
) {
    suspend fun kakaoKey(): String? =
        if (!secrets.supported) null else runCatching { secrets.get(KAKAO_SECRET) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** The search a [domestic] (or 해외) query would go to, or null when none can run on this phone. */
    suspend fun sourceFor(domestic: Boolean): PlaceSource? = when {
        domestic && http.supported && kakaoKey() != null -> PlaceSource.KAKAO
        device.supported -> PlaceSource.DEVICE
        else -> null
    }

    /**
     * Searches [query] ([domestic]: 국내, else 해외). Results outside the scope are left out (a 국내 search keeps the
     * places inside a 시·군·구, a 해외 one the places outside them) and get their distance from [near] when given.
     */
    suspend fun search(query: String, domestic: Boolean, near: GeoPoint? = null): PlaceSearchResult {
        val q = query.trim().take(MAX_QUERY)
        val key = if (domestic && http.supported) kakaoKey() else null
        if (key != null) {
            val outcome = try {
                withTimeout(timeoutMs) { KakaoLocal.search(http, key, q, near) }
            } catch (e: TimeoutCancellationException) {
                KakaoLocal.Outcome.Offline
            }
            when (outcome) {
                is KakaoLocal.Outcome.Hits -> return PlaceSearchResult.Found(tidy(outcome.hits, domestic = true, near), PlaceSource.KAKAO)
                KakaoLocal.Outcome.Offline -> return PlaceSearchResult.Failed(PlaceSearchError.FAILED, PlaceSource.KAKAO)
                // the key or the service refused: the phone's own search can still answer
                is KakaoLocal.Outcome.Refused -> {
                    val error = when {
                        outcome.disabled -> PlaceSearchError.KAKAO_DISABLED
                        outcome.keyRejected -> PlaceSearchError.KAKAO_KEY
                        outcome.quota -> PlaceSearchError.KAKAO_QUOTA
                        else -> PlaceSearchError.FAILED
                    }
                    if (!device.supported) return PlaceSearchResult.Failed(error, PlaceSource.KAKAO)
                    val notice = when (error) {
                        PlaceSearchError.KAKAO_KEY -> PlaceSearchTexts.FALLBACK_KEY
                        PlaceSearchError.KAKAO_DISABLED -> PlaceSearchTexts.FALLBACK_DISABLED
                        PlaceSearchError.KAKAO_QUOTA -> PlaceSearchTexts.FALLBACK_QUOTA
                        else -> PlaceSearchTexts.FALLBACK_FAILED
                    }
                    return when (val r = searchDevice(q, domestic, near)) {
                        is PlaceSearchResult.Found -> r.copy(notice = notice)
                        is PlaceSearchResult.Failed -> PlaceSearchResult.Failed(error, PlaceSource.KAKAO)
                    }
                }
            }
        }
        if (!device.supported) return PlaceSearchResult.Failed(PlaceSearchError.UNAVAILABLE, null)
        return searchDevice(q, domestic, near)
    }

    private suspend fun searchDevice(query: String, domestic: Boolean, near: GeoPoint?): PlaceSearchResult = try {
        val hits = withTimeout(timeoutMs) { device.search(query, domestic, near) }
        PlaceSearchResult.Found(tidy(hits, domestic, near), PlaceSource.DEVICE)
    } catch (e: TimeoutCancellationException) {
        PlaceSearchResult.Failed(PlaceSearchError.FAILED, PlaceSource.DEVICE)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PlaceSearchResult.Failed(PlaceSearchError.FAILED, PlaceSource.DEVICE)
    }

    /** 키 확인: one keyword search for one result (Kakao counts it like any search; the free quota is per day). */
    suspend fun checkKakaoKey(): KakaoKeyCheck {
        val key = kakaoKey() ?: return KakaoKeyCheck.REJECTED
        val outcome = try {
            withTimeout(timeoutMs) { KakaoLocal.check(http, key) }
        } catch (e: TimeoutCancellationException) {
            KakaoLocal.Outcome.Offline
        }
        return when (outcome) {
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

        /** In scope, each place once (same name at about the same spot), with its distance from [near]. */
        fun tidy(hits: List<PlaceHit>, domestic: Boolean, near: GeoPoint?): List<PlaceHit> {
            val seen = HashSet<String>()
            return hits.asSequence()
                .filter { (KoreaRegions.locate(it.point.lat, it.point.lng, snapKm = 3.0) != null) == domestic }
                .filter { seen.add(it.name.trim().lowercase() + "@" + (it.point.lat * 2000).roundToInt() + "," + (it.point.lng * 2000).roundToInt()) }
                .map { h -> if (near == null) h else h.copy(distanceM = distanceM(near, h.point)) }
                .take(MAX_HITS)
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
    const val KAKAO_DISABLED = "이 키의 앱에서 카카오맵이 꺼져 있어요. 카카오 개발자 사이트의 앱 관리 › 카카오맵 › 사용 설정에서 상태를 ON으로 켜 주세요."
    const val KAKAO_QUOTA = "이 키의 카카오 무료 사용량을 다 썼어요. 사용량은 카카오 개발자 사이트의 앱 관리 › 통계 › 쿼터에서 볼 수 있어요."
    const val FALLBACK_KEY = "카카오 키가 맞지 않아 기기 지도 서비스로 찾았어요. 설정 › 장소 검색에서 키를 확인해 주세요."
    const val FALLBACK_DISABLED = "이 키의 앱에서 카카오맵이 꺼져 있어 기기 지도 서비스로 찾았어요. 카카오 앱 관리 › 카카오맵 › 사용 설정을 켜 주세요."
    const val FALLBACK_QUOTA = "카카오 무료 사용량을 다 써서 기기 지도 서비스로 찾았어요."
    const val FALLBACK_FAILED = "카카오 검색이 지금 안 돼서 기기 지도 서비스로 찾았어요."
    const val PICKED_HINT = "고른 곳이 지도에 표시됐어요. 지도에서 누르면 그 자리로 바뀌어요."

    fun noResults(query: String) = "‘$query’(으)로 찾은 곳이 없어요. 다른 이름이나 주소로 찾거나 지도에서 직접 찍어 주세요."
    fun source(source: PlaceSource) = "검색: ${source.label}"
    fun fromHere(m: Int) = "현재 위치에서 ${PlaceSearchService.distanceLabel(m)}"

    fun message(error: PlaceSearchError, domestic: Boolean): String = when (error) {
        PlaceSearchError.FAILED -> FAILED
        PlaceSearchError.UNAVAILABLE -> UNAVAILABLE + if (domestic) UNAVAILABLE_KAKAO else ""
        PlaceSearchError.KAKAO_KEY -> KAKAO_KEY
        PlaceSearchError.KAKAO_DISABLED -> KAKAO_DISABLED
        PlaceSearchError.KAKAO_QUOTA -> KAKAO_QUOTA
    }

    // 설정 › 장소 검색
    const val SECTION = "장소 검색"
    const val INTRO = "로스터리·카페 위치를 정할 때 이름이나 주소로 찾는 검색이에요. 기본은 휴대폰의 지도 서비스" +
        "(Android는 Google 지오코더, iOS는 Apple 지도)로 찾아요. 키는 필요 없어요."
    const val KAKAO_LABEL = "카카오 REST API 키 (선택)"
    const val KAKAO_PLACEHOLDER = "REST API 키 붙여넣기"
    const val KAKAO_HINT = "넣으면 국내 검색은 카카오 로컬로 찾아요. 한국 카페·로스터리를 이름으로 더 잘 찾아요. 해외는 계속 휴대폰의 지도 서비스로 찾아요."
    const val KAKAO_GUIDE = "키 받기: developers.kakao.com에 카카오 계정으로 로그인해 앱을 만들고, 앱 키의 ‘REST API 키’를 복사해 " +
        "붙여 넣어요. 같은 앱의 카카오맵 › 사용 설정에서 상태를 ON으로 켜야 검색돼요. 무료 사용량은 계정마다 카카오맵을 처음 켠 앱 하나에만 있어요."
    const val KAKAO_PRIVACY = "키는 이 휴대폰에만 암호화해 저장하고 백업에 넣지 않아요. 검색할 때 적은 말(현재 위치에서 찾으면 그 좌표도)만 카카오로 보내요."
    const val KAKAO_GUIDE_URL = "https://developers.kakao.com/console/app"
    const val STORE_UNSUPPORTED = "이 기기에서는 키를 안전하게 저장할 수 없어 카카오 검색을 쓸 수 없어요."
    const val SAVE = "저장"
    const val CHECK = "키 확인"
    const val CHECKING = "확인 중…"
    const val CLEAR = "지우기"
}
