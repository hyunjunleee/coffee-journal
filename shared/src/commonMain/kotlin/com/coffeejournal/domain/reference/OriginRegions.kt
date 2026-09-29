package com.coffeejournal.domain.reference

/**
 * Representative coffee-growing regions and sub-regions of every producing country in [CoffeeCountries],
 * for the bean form's 지역 (region) and 세부 지역 (sub-region) dropdowns. Free text stays allowed; this is
 * only the list of suggestions.
 *
 * Shape: a country's top-level [Place]s are its regions (지역), the level the map groups records by. Their
 * [Place.subs] are the sub-regions (세부 지역) the trade commonly names: districts, woredas, counties,
 * municipalities or DO areas, and, where roasters name them too, one more level of kebeles, villages or
 * washing-station areas (e.g. 시다모 › 벤사 › 코코세 = Sidamo › Bensa › Kokose). Nesting is at most three deep.
 *
 * How it was compiled: by hand, from the region lists of national coffee bodies (FNC Colombia, ICAFE, ANACAFE,
 * IHCAFE, the Consejo Salvadoreño del Café, Coffee Board of India, PNG Coffee Industry Corporation), official
 * administrative lists (woredas of the Sidama Region and Guji Zone, Panama's corregimientos), Cup of Excellence
 * results, and importers' origin and offer pages (Royal Coffee, Cafe Imports, Trabocca, Sucafina, Ally Coffee,
 * Mercanta and others). Korean spellings are the ones Korean importers and roasters print on their products
 * (커피 리브레, 코빈즈, 커만사, 함께커피, 카페알트로 …); where no roaster usage was found the name is
 * transliterated by the 외래어 표기법 and common variants are listed in [Place.aliases]. The Korean spellings
 * the app already uses ([CoffeeCountries.regionSynonyms]: 예가체프, 시다모/시다마, 우일라, 타라주 …) are kept,
 * and every map-dot region of [CoffeeCountries.all] is a top-level Place whose [Place.en] (or an alias) is the
 * map's canonical name, so e.g. Ethiopia's Sidama region is `Place("시다모", "Sidamo", aliases = "시다마",
 * "Sidama" …)`. Where a political name changed (Sidama Region, Bench Sheko, Tanzania's Songwe) the trade name is
 * kept and the new name is an alias or a sibling. The sources used for each country are named in a
 * `// source:` comment above its block.
 *
 * Coordinates: every top-level region carries [Place.lat]/[Place.lng], rounded to 2 decimals, from the English
 * Wikipedia article coordinates (MediaWiki `prop=coordinates`) of the zone, department, county, district or town
 * the region is named after (Wikidata P625 or Spanish Wikipedia where the English article has none); for a trade
 * region that spans an area (e.g. West Valley, Cerrado, Mogiana) the point is that of its main producing town or
 * area. The article used for each region is named in the country's `coords:` comment.
 *
 * Checked: 2026-09-29.
 */
object OriginRegions {
    /**
     * A region or a place inside it: [ko] as Korean specialty-coffee roasters write it, [en] the usual English
     * (trade) name, [aliases] other spellings people type (Korean or English), [subs] the places inside it.
     * [lat]/[lng] (WGS84 decimal degrees) are an approximate centre point, set on every top-level region (the
     * world map puts a dot there) and left null on sub-places.
     */
    data class Place(val ko: String, val en: String, val aliases: List<String> = emptyList(), val subs: List<Place> = emptyList(), val lat: Double? = null, val lng: Double? = null)

    /** [countryEn] is exactly a [CoffeeCountries.Country.en]. */
    data class Origin(val countryEn: String, val regions: List<Place>)

    // Kept by continent (OriginsAmericas, OriginsAfrica, OriginsAsia), one private function per country, which keeps
    // each JVM method well under the 64 KB bytecode limit.
    val all: List<Origin> = OriginsAmericas.all + OriginsAfrica.all + OriginsAsia.all
}
