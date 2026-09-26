package com.coffeejournal.ui.about

import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.domain.rules.Numbers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AboutDataTest {
    @Test fun cafeRecipeStepsPourTheRecipeWater_andSourcesAreHttps() {
        for (r in CafeRecipes.all) {
            val poured = r.steps.sumOf { Numbers.parse(it.water) ?: 0.0 }
            assertEquals(Numbers.parse(r.water), poured, "${r.id}: the steps pour the recipe's water")
            r.sourceUrl?.let { assertTrue(it.startsWith("https://"), "${r.id}: $it") }
        }
    }

    @Test fun librariesGroupByLicense_mostUsedFirst() {
        fun lib(a: String, vararg l: String) = Library("g", a, "1", a, null, l.toList())
        val groups = LibraryGroups.byLicense(listOf(lib("b", "MIT"), lib("a", "Apache-2.0"), lib("c", "Apache-2.0", "MIT"), lib("d", "Apache-2.0")))
        assertEquals(listOf("Apache-2.0", "MIT"), groups.map { it.spdx })
        assertEquals(listOf("a", "c", "d"), groups[0].libraries.map { it.artifact })
        assertEquals(listOf("b", "c"), groups[1].libraries.map { it.artifact })
    }

    @Test fun everyCreditLinkIsHttps() {
        Credits.all.flatMap { it.links }.forEach { (label, url) -> assertTrue(url.startsWith("https://"), "$label: $url") }
    }
}
