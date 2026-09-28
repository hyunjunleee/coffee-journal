@file:OptIn(ExperimentalForeignApi::class)

package com.coffeejournal.ui.map.search

import com.coffeejournal.domain.model.GeoPoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKErrorDomain
import platform.MapKit.MKErrorPlacemarkNotFound
import platform.MapKit.MKLocalSearch
import platform.MapKit.MKLocalSearchRequest
import platform.MapKit.MKLocalSearchResultTypeAddress
import platform.MapKit.MKLocalSearchResultTypePointOfInterest
import platform.MapKit.MKMapItem
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [DevicePlaceSearch] on MapKit's MKLocalSearch (Apple Maps, no key): a natural-language query for places and
 * addresses, biased to Korea for 국내 (or to the current location when the search starts there). Apple answers "not
 * found" with an error, which reads as no results here.
 */
class IosPlaceSearch : DevicePlaceSearch {
    override val supported: Boolean get() = true

    override suspend fun search(query: String, domestic: Boolean, near: GeoPoint?): List<PlaceHit> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val request = MKLocalSearchRequest()
            request.naturalLanguageQuery = query
            request.resultTypes = MKLocalSearchResultTypeAddress or MKLocalSearchResultTypePointOfInterest
            if (near != null) {
                request.region = MKCoordinateRegionMakeWithDistance(CLLocationCoordinate2DMake(near.lat, near.lng), NEAR_METRES, NEAR_METRES)
            } else if (domestic) {
                request.region = MKCoordinateRegionMake(CLLocationCoordinate2DMake(KOREA_LAT, KOREA_LNG), MKCoordinateSpanMake(KOREA_SPAN_LAT, KOREA_SPAN_LNG))
            }
            val search = MKLocalSearch(request)
            search.startWithCompletionHandler { response, error ->
                if (!cont.isActive) return@startWithCompletionHandler
                when {
                    response != null -> cont.resume(response.mapItems.mapNotNull { (it as? MKMapItem)?.let { item -> toHit(item, domestic) } })
                    error != null && error.domain == MKErrorDomain && error.code == MKErrorPlacemarkNotFound.toLong() -> cont.resume(emptyList())
                    else -> cont.resumeWithException(PlaceSearchException(error?.localizedDescription ?: "no response"))
                }
            }
            cont.invokeOnCancellation { search.cancel() }
        }
    }

    private companion object {
        // South Korea with its islands, as the 국내 map shows it
        const val KOREA_LAT = 35.9
        const val KOREA_LNG = 128.0
        const val KOREA_SPAN_LAT = 6.0
        const val KOREA_SPAN_LNG = 7.5
        const val NEAR_METRES = 20_000.0

        /** Apple's category for a place (iOS 13+), in the app's words for the few a café picker meets. */
        val CATEGORIES = mapOf(
            "Cafe" to "카페", "Bakery" to "베이커리", "Restaurant" to "음식점", "FoodMarket" to "식료품점", "Store" to "상점",
        )

        fun toHit(item: MKMapItem, domestic: Boolean): PlaceHit? {
            val placemark = item.placemark
            val point = placemark.coordinate.useContents { GeoPoint.of(latitude, longitude) } ?: return null
            val country = placemark.country?.trim().orEmpty()
            val title = placemark.title?.trim().orEmpty()
            val address = (if (domestic && country.isNotEmpty()) title.removePrefix(country).trimStart(' ', ',') else title)
                .ifEmpty {
                    listOfNotNull(placemark.administrativeArea, placemark.locality, placemark.subLocality, placemark.thoroughfare, placemark.subThoroughfare)
                        .joinToString(" ")
                }
            val name = item.name?.trim()?.takeIf { it.isNotEmpty() } ?: address.ifEmpty { return null }
            val category = item.pointOfInterestCategory?.removePrefix("MKPOICategory")?.let { CATEGORIES[it] }
            return PlaceHit(name, address, point, category)
        }
    }
}
