package com.coffeejournal.android

import android.Manifest
import android.app.Application
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.ui.map.search.CurrentLocation
import com.coffeejournal.ui.map.search.DevicePlaceSearch
import com.coffeejournal.ui.map.search.LocateResult
import com.coffeejournal.ui.map.search.PlaceHit
import com.coffeejournal.ui.map.search.PlaceSearchException
import kotlinx.coroutines.awaitCancellation
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The phone's place search for the flow tests: answers [hits] (or fails when [fail]), records every query; no test
 * reaches Google's geocoder or Apple Maps. Bound by [testPlatformModule].
 */
class FakePlaceSearch : DevicePlaceSearch {
    @Volatile override var supported: Boolean = true
    @Volatile var hits: List<PlaceHit> = emptyList()
    @Volatile var fail: Boolean = false
    val asked = CopyOnWriteArrayList<Triple<String, Boolean, GeoPoint?>>()

    override suspend fun search(query: String, domestic: Boolean, near: GeoPoint?): List<PlaceHit> {
        asked += Triple(query, domestic, near)
        if (fail) throw PlaceSearchException("Service not Available")
        return hits
    }
}

/** The phone's position for the flow tests: [answer], or nothing ever with [hang]; no test reads a real GPS. */
class FakeCurrentLocation : CurrentLocation {
    @Volatile override var supported: Boolean = true
    @Volatile var servicesOn: Boolean = true
    @Volatile var answer: LocateResult = LocateResult.NoFix
    @Volatile var hang: Boolean = false
    @Volatile var asked = 0

    override suspend fun servicesOn(): Boolean = servicesOn

    override suspend fun locate(): LocateResult {
        asked++
        if (hang) awaitCancellation()
        return answer
    }
}

object PlaceSetup {
    private val koin get() = GlobalContext.get()
    val search: FakePlaceSearch get() = koin.get<DevicePlaceSearch>() as FakePlaceSearch
    val location: FakeCurrentLocation get() = koin.get<CurrentLocation>() as FakeCurrentLocation

    private val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun grantLocation(app: Application) = shadowOf(app).grantPermissions(*LOCATION)
    fun denyLocation(app: Application) = shadowOf(app).denyPermissions(*LOCATION)
}

/** SYNTHETIC places (2026-09-28): names and addresses are made up, positions are ordinary spots in those districts. */
object PlaceFixtures {
    val FELT = PlaceHit("FELT 청계천점", "서울 중구 청계천로 100", GeoPoint(37.5663, 126.9910), "카페")
    val FELT_OTHER = PlaceHit("FELT 광화문점", "서울 종로구 세종대로 170", GeoPoint(37.5716, 126.9769), "카페")
    val BUILDING = PlaceHit("테스트빌딩", "서울 성동구 성수이로7길 51", GeoPoint(37.5446, 127.0557), "주소")
    val MOMOS = PlaceHit("모모스커피 영도", "부산 영도구 봉래나루로 160", GeoPoint(35.0930, 129.0450), "카페")

    /** The phone's answer for an area: only its address, as Android's geocoder gives it. */
    val JANGCHUNG = PlaceHit("서울특별시 중구 장충동", "서울특별시 중구 장충동", GeoPoint(37.5580, 127.0050))
}
