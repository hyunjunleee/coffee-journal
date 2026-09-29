package com.coffeejournal.domain.reference

/** Placeholder: replaced by the compiled producing-region data. */
object OriginRegions {
    data class Place(val ko: String, val en: String, val aliases: List<String> = emptyList(), val subs: List<Place> = emptyList(), val lat: Double? = null, val lng: Double? = null)

    data class Origin(val countryEn: String, val regions: List<Place>)

    val all: List<Origin> = listOf(
        Origin(
            "Ethiopia",
            listOf(
                Place("시다모", "Sidamo", listOf("시다마", "Sidama"), listOf(Place("벤사", "Bensa", subs = listOf(Place("코코세", "Kokose"))))),
                Place("예가체프", "Yirgacheffe", listOf("이르가체프"), listOf(Place("게뎁", "Gedeb", subs = listOf(Place("워카", "Worka"))))),
            ),
        ),
    )
}
