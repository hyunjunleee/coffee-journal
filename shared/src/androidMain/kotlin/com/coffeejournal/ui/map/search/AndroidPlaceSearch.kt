package com.coffeejournal.ui.map.search

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.coffeejournal.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [DevicePlaceSearch] on android.location.Geocoder, which the phone provides (Google's geocoder on phones with Google
 * services; none on some others, hence [supported]). Android 13+ answers through its listener; older versions call the
 * blocking method on the IO dispatcher. Results come in Korean; a 국내 search is limited to a box around Korea.
 */
class AndroidPlaceSearch(
    context: Context,
    /** A geocoder answering in Korean; tests pass one they control. */
    private val geocoders: () -> Geocoder = { Geocoder(context, Locale.KOREAN) },
) : DevicePlaceSearch {
    override val supported: Boolean get() = Geocoder.isPresent()

    override suspend fun search(query: String, domestic: Boolean, near: GeoPoint?): List<PlaceHit> {
        val geocoder = geocoders()
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listen(geocoder, query, domestic) else blocking(geocoder, query, domestic)
        return found.mapNotNull { toHit(it, domestic) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun listen(geocoder: Geocoder, query: String, domestic: Boolean): List<Address> = suspendCancellableCoroutine { cont ->
        val listener = object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (cont.isActive) cont.resume(addresses.toList())
            }

            override fun onError(errorMessage: String?) {
                if (cont.isActive) cont.resumeWithException(PlaceSearchException(errorMessage ?: "geocoder failed"))
            }
        }
        try {
            if (domestic) geocoder.getFromLocationName(query, MAX_RESULTS, SOUTH, WEST, NORTH, EAST, listener)
            else geocoder.getFromLocationName(query, MAX_RESULTS, listener)
        } catch (e: IllegalArgumentException) {
            if (cont.isActive) cont.resumeWithException(PlaceSearchException(e.message ?: "bad query", e))
        }
    }

    private suspend fun blocking(geocoder: Geocoder, query: String, domestic: Boolean): List<Address> = try {
        runInterruptible(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val found = if (domestic) geocoder.getFromLocationName(query, MAX_RESULTS, SOUTH, WEST, NORTH, EAST)
            else geocoder.getFromLocationName(query, MAX_RESULTS)
            found.orEmpty()
        }
    } catch (e: IOException) {
        // no network, or the phone's geocoder service is not answering
        throw PlaceSearchException(e.message ?: "geocoder failed", e)
    } catch (e: IllegalArgumentException) {
        throw PlaceSearchException(e.message ?: "bad query", e)
    }

    companion object {
        const val MAX_RESULTS = 15
        // South Korea with its islands (Marado to Dokdo), as the 국내 map shows it
        const val SOUTH = 33.0
        const val WEST = 124.5
        const val NORTH = 38.7
        const val EAST = 131.9

        /**
         * A geocoder result as a place: named after the building or place when the geocoder names one (not a bare house
         * number or the area itself), else after its address. In Korea the country ("대한민국") is left off the address.
         */
        fun toHit(a: Address, domestic: Boolean): PlaceHit? {
            if (!a.hasLatitude() || !a.hasLongitude()) return null
            val point = GeoPoint.of(a.latitude, a.longitude) ?: return null
            val line = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it)?.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            val country = a.countryName?.trim().orEmpty()
            val address = (if (domestic && country.isNotEmpty()) line.removePrefix(country).trimStart(' ', ',') else line)
                .ifEmpty { listOfNotNull(a.adminArea, a.locality, a.subLocality, a.thoroughfare, a.subThoroughfare).joinToString(" ") }
            val areas = setOf(a.thoroughfare, a.subThoroughfare, a.locality, a.subLocality, a.adminArea, a.subAdminArea, a.countryName)
            val name = a.featureName?.trim()?.takeIf { f -> f.isNotEmpty() && f.any { it.isLetter() } && f !in areas && f != address }
            val shown = name ?: address.ifEmpty { return null }
            return PlaceHit(shown, address, point)
        }
    }
}
