package com.coffeejournal.domain.model

/** A position in WGS84 degrees (what map apps and the Korea map use). */
data class GeoPoint(val lat: Double, val lng: Double) {
    companion object {
        /** Both coordinates, finite and in range, or null (a half-set or damaged pair is no position). */
        fun of(lat: Double?, lng: Double?): GeoPoint? =
            if (lat == null || lng == null || !lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) null
            else GeoPoint(lat, lng)
    }
}

/**
 * Where a visited café is (table cafe_places, backup key `cafePlaces`): café records only carry the café's name, so
 * its position is kept once per name and shared by every visit.
 */
data class CafePlace(val name: String, val lat: Double?, val lng: Double?, val createdAt: Long) {
    val point: GeoPoint? get() = GeoPoint.of(lat, lng)

    companion object {
        /** Café names are matched trimmed and case-insensitively ("FELT 청계천" = "felt 청계천 "). */
        fun key(name: String): String = name.trim().lowercase()
    }
}
