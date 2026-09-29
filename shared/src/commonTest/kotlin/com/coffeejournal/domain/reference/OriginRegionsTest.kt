package com.coffeejournal.domain.reference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The hand-compiled region / sub-region reference data behind the bean form's 지역 and 세부 지역 dropdowns. */
class OriginRegionsTest {
    private val byCountry = OriginRegions.all.associateBy { it.countryEn }

    /** Every place at every depth, with its depth (1 = top-level region). */
    private fun allPlaces(): List<Pair<OriginRegions.Place, Int>> {
        val out = mutableListOf<Pair<OriginRegions.Place, Int>>()
        fun walk(p: OriginRegions.Place, depth: Int) {
            out += p to depth
            p.subs.forEach { walk(it, depth + 1) }
        }
        OriginRegions.all.forEach { o -> o.regions.forEach { walk(it, 1) } }
        return out
    }

    /** Every sibling list: each country's regions and every place's subs. */
    private fun siblingLists(): List<List<OriginRegions.Place>> =
        OriginRegions.all.map { it.regions } + allPlaces().map { it.first.subs }.filter { it.isNotEmpty() }

    private fun OriginRegions.Place.names(): List<String> = listOf(ko, en) + aliases

    @Test fun everyCountry_isACoffeeCountry_andAppearsOnce() {
        val names = OriginRegions.all.map { it.countryEn }
        names.forEach { assertTrue(it in CoffeeCountries.byEn, "not a CoffeeCountries.Country.en: $it") }
        assertEquals(names.size, names.toSet().size, "countries listed twice: ${names.groupBy { it }.filterValues { it.size > 1 }.keys}")
        OriginRegions.all.forEach { assertTrue(it.regions.isNotEmpty(), "${it.countryEn} has no regions") }
    }

    @Test fun everyMapDotRegion_isATopLevelPlace_byEnOrAlias() {
        CoffeeCountries.all.forEach { country ->
            val origin = assertNotNull(byCountry[country.en], "no OriginRegions entry for ${country.en}")
            country.regions.forEach { dot ->
                assertTrue(
                    origin.regions.any { it.en == dot.name || dot.name in it.aliases },
                    "${country.en}: map-dot region \"${dot.name}\" is not a top-level Place's en or alias",
                )
            }
        }
        // the user's example: 시다모 › 벤사 › 코코세
        val sidamo = byCountry.getValue("Ethiopia").regions.single { it.en == "Sidamo" }
        assertEquals("시다모", sidamo.ko)
        assertTrue("시다마" in sidamo.aliases && "Sidama" in sidamo.aliases)
        val bensa = sidamo.subs.single { it.ko == "벤사" }
        assertEquals("Kokose", bensa.subs.single { it.ko == "코코세" }.en)
    }

    @Test fun everyKoreanRegionSynonym_isThatRegionsKoOrAlias() {
        CoffeeCountries.regionSynonyms.forEach { (ko, canonical) ->
            val countries = CoffeeCountries.all.filter { c -> c.regions.any { it.name == canonical } }
            countries.forEach { country ->
                val place = assertNotNull(
                    byCountry.getValue(country.en).regions.firstOrNull { it.en == canonical || canonical in it.aliases },
                    "${country.en}: no Place for $canonical",
                )
                assertTrue(ko == place.ko || ko in place.aliases, "\"$ko\" (→ $canonical) is neither ko nor an alias of ${place.en}")
            }
        }
    }

    @Test fun names_areNonBlank_withoutHierarchySeparators() {
        allPlaces().forEach { (p, _) ->
            p.names().forEach { name ->
                assertTrue(name.isNotBlank(), "blank name in ${p.en}")
                assertEquals(name.trim(), name, "untrimmed name \"$name\"")
                assertTrue(name.none { it == ',' || it == '›' || it == '>' }, "separator in \"$name\"")
            }
        }
    }

    @Test fun siblings_haveUniqueKoAndEn() {
        siblingLists().forEach { siblings ->
            val ko = siblings.map { it.ko }
            val en = siblings.map { it.en }
            assertEquals(ko.size, ko.toSet().size, "duplicate ko among siblings: ${ko.groupBy { it }.filterValues { it.size > 1 }.keys}")
            assertEquals(en.size, en.toSet().size, "duplicate en among siblings: ${en.groupBy { it }.filterValues { it.size > 1 }.keys}")
        }
    }

    @Test fun eachTopLevelName_pointsToOneRegionOfItsCountry() {
        // the map resolves a typed region by ko / en / alias, so within a country a name may belong to one region only
        OriginRegions.all.forEach { origin ->
            val owners = origin.regions.flatMap { r -> r.names().map { it.lowercase() to r.en } }.groupBy({ it.first }, { it.second })
            owners.forEach { (name, regions) ->
                assertEquals(1, regions.toSet().size, "${origin.countryEn}: \"$name\" names ${regions.toSet()}")
            }
        }
    }

    @Test fun nesting_isAtMostThreeDeep() {
        val deepest = allPlaces().maxOf { it.second }
        assertTrue(deepest <= 3, "nesting depth $deepest")
        allPlaces().filter { it.second == 3 }.forEach { (p, _) -> assertTrue(p.subs.isEmpty(), "${p.en} nests a fourth level") }
    }

    @Test fun everyTopLevelRegion_hasACoordinateInTheCoffeeBelt() {
        OriginRegions.all.forEach { origin ->
            origin.regions.forEach { r ->
                val lat = assertNotNull(r.lat, "${origin.countryEn} / ${r.en} has no lat")
                val lng = assertNotNull(r.lng, "${origin.countryEn} / ${r.en} has no lng")
                assertTrue(lat in -35.0..35.0, "${origin.countryEn} / ${r.en}: lat $lat outside the coffee belt")
                assertTrue(lng in -180.0..180.0, "${origin.countryEn} / ${r.en}: lng $lng")
            }
        }
    }

    @Test fun koIsKorean_enIsNot_andAliasesAddSomething() {
        allPlaces().forEach { (p, _) ->
            assertTrue(p.ko.any { it in '\uAC00'..'\uD7A3' }, "ko of ${p.en} is not Korean: ${p.ko}")
            assertTrue(p.en.none { it in '\uAC00'..'\uD7A3' }, "en of ${p.ko} is Korean: ${p.en}")
            assertTrue(p.aliases.none { it == p.ko || it == p.en }, "${p.en} repeats its name as an alias: ${p.aliases}")
            assertEquals(p.aliases.size, p.aliases.toSet().size, "${p.en} lists an alias twice: ${p.aliases}")
        }
    }

    // Where each region's point lies (in its own country, on the world map's outline) is checked in WorldRegionsTest.
}
