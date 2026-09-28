package com.coffeejournal.android

import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.ui.map.search.AndroidCurrentLocation
import com.coffeejournal.ui.map.search.AndroidPlaceSearch
import com.coffeejournal.ui.map.search.LocateResult
import com.coffeejournal.ui.map.search.PlaceHit
import com.coffeejournal.ui.map.search.PlaceSearchException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The Android side of the picker's search and "현재 위치" on Robolectric's framework shadows: geocoder results as
 * places (Android 13+'s listener), a refused or switched-off location, and a recent fix used at once. Nothing reaches
 * Google's geocoder or a real GPS.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class LocationPlatformTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun address(line: String, lat: Double?, lng: Double?, feature: String? = null, country: String = "대한민국") = Address(Locale.KOREAN).apply {
        setAddressLine(0, line)
        countryName = country
        featureName = feature
        thoroughfare = "성수이로7길"
        if (lat != null) latitude = lat
        if (lng != null) longitude = lng
    }

    @Test
    fun geocoderResults_becomePlaces_namedAfterThePlaceNotAHouseNumber() {
        val building = address("대한민국 서울특별시 성동구 성수이로7길 51", 37.5446, 127.0557, feature = "51")
        assertEquals(
            PlaceHit("서울특별시 성동구 성수이로7길 51", "서울특별시 성동구 성수이로7길 51", GeoPoint(37.5446, 127.0557)),
            AndroidPlaceSearch.toHit(building, domestic = true),
        )
        val cafe = address("대한민국 서울특별시 성동구 성수이로7길 51", 37.5446, 127.0557, feature = "테스트커피")
        assertEquals("테스트커피", AndroidPlaceSearch.toHit(cafe, domestic = true)!!.name)
        assertEquals("the street is no name", "서울특별시 성동구 성수이로7길 51", AndroidPlaceSearch.toHit(address("대한민국 서울특별시 성동구 성수이로7길 51", 37.5, 127.0, feature = "성수이로7길"), true)!!.name)
        // abroad the country stays in the address
        assertEquals("일본 교토부 교토시", AndroidPlaceSearch.toHit(address("일본 교토부 교토시", 34.98, 135.75, country = "일본"), domestic = false)!!.address)
        assertNull("no position, no place", AndroidPlaceSearch.toHit(address("어딘가", null, null), domestic = true))
    }

    @Test
    fun search_answersThroughTheListener_andAGeocoderErrorFails() = runBlocking {
        val geocoder = Geocoder(app, Locale.KOREAN)
        val search = AndroidPlaceSearch(app) { geocoder }
        shadowOf(geocoder).setFromLocation(listOf(address("일본 교토부 교토시 시모교구", 34.9858, 135.7588, feature = "Kurasu Kyoto", country = "일본")))
        assertEquals(listOf("Kurasu Kyoto"), search.search("Kurasu", domestic = false, near = null).map { it.name })
        shadowOf(geocoder).setErrorMessage("Service not Available")
        val failed = runCatching { search.search("Kurasu", domestic = false, near = null) }.exceptionOrNull()
        assertTrue("$failed", failed is PlaceSearchException)
    }

    @Test
    fun currentLocation_refused_off_orARecentFix() = runBlocking {
        val lm = app.getSystemService(LocationManager::class.java)
        val location = AndroidCurrentLocation(app)
        assertTrue(location.supported)
        PlaceSetup.denyLocation(app)
        assertEquals(LocateResult.Denied, location.locate())

        PlaceSetup.grantLocation(app)
        shadowOf(lm).setLocationEnabled(false)
        assertEquals(false, location.servicesOn())
        assertEquals(LocateResult.ServicesOff, location.locate())

        // a fix from the last minute is used at once
        shadowOf(lm).setLocationEnabled(true)
        shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val fix = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 37.5446; longitude = 127.0557; accuracy = 20f
            time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        shadowOf(lm).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, fix)
        assertEquals(LocateResult.Found(GeoPoint(37.5446, 127.0557), 20.0), location.locate())
    }
}
