package com.coffeejournal.domain

import com.coffeejournal.domain.reference.FlavorWheel
import com.coffeejournal.domain.reference.FlavorWheelExtras
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The SCA/WCR wheel is complete and pickable at every tier; the app's unofficial notes never repeat it. */
class FlavorWheelTest {
    /**
     * Every label printed on the SCA/WCR Coffee Taster's Flavor Wheel, tier by tier, as read off the poster
     * (SCA_TasterWheel_English_8.5x11.pdf, "© 2016 SCA and WCR", V.2) and its V.3 ("© 2021 SCA and WCR") in SCA
     * Standard 103-2024 Appendix 8.3 — the two print the same labels. Written as on the poster.
     */
    private val inner = listOf("Floral", "Fruity", "Sour/Fermented", "Green/Vegetative", "Other", "Roasted", "Spices", "Nutty/Cocoa", "Sweet")
    private val middle = listOf(
        "Black Tea", "Floral", "Berry", "Dried Fruit", "Other Fruit", "Citrus Fruit", "Sour", "Alcohol/Fermented", "Olive Oil",
        "Raw", "Green/Vegetative", "Beany", "Papery/Musty", "Chemical", "Pipe Tobacco", "Tobacco", "Burnt", "Cereal", "Pungent",
        "Pepper", "Brown Spice", "Nutty", "Cocoa", "Brown Sugar", "Vanilla", "Vanillin", "Overall Sweet", "Sweet Aromatics",
    )
    private val outer = listOf(
        "Chamomile", "Rose", "Jasmine", "Blackberry", "Raspberry", "Blueberry", "Strawberry", "Raisin", "Prune", "Coconut",
        "Cherry", "Pomegranate", "Pineapple", "Grape", "Apple", "Peach", "Pear", "Grapefruit", "Orange", "Lemon", "Lime",
        "Sour Aromatics", "Acetic Acid", "Butyric Acid", "Isovaleric Acid", "Citric Acid", "Malic Acid", "Winey", "Whiskey",
        "Fermented", "Overripe", "Under-ripe", "Peapod", "Fresh", "Dark Green", "Vegetative", "Hay-like", "Herb-like", "Stale",
        "Cardboard", "Papery", "Woody", "Moldy/Damp", "Musty/Dusty", "Musty/Earthy", "Animalic", "Meaty Brothy", "Phenolic",
        "Bitter", "Salty", "Medicinal", "Petroleum", "Skunky", "Rubber", "Acrid", "Ashy", "Smoky", "Brown, Roast", "Grain",
        "Malt", "Anise", "Nutmeg", "Cinnamon", "Clove", "Peanuts", "Hazelnut", "Almond", "Chocolate", "Dark Chocolate",
        "Molasses", "Maple Syrup", "Caramelized", "Honey",
    )

    /** Case, the spaces around "/", and the two poster spellings the app writes differently ("Brown, Roast", "Meaty Brothy"). */
    private fun key(term: String) = term.lowercase().replace(Regex("\\s*/\\s*"), "/").replace("brown, roast", "brown roast").replace("meaty brothy", "meaty/brothy")

    private val descriptors = FlavorWheel.allDescriptors.map(::key)

    @Test fun everyLabelOfTheWheel_isAPickableDescriptor() {
        for (label in inner + middle + outer) assertTrue(key(label) in descriptors, "$label is missing from the app's wheel")
        // …and nothing else: the app adds no descriptor of its own to the wheel
        assertEquals((inner + middle + outer).map(::key).toSet(), descriptors.toSet())
        assertEquals(descriptors.size, descriptors.toSet().size, "each descriptor once")
        assertEquals(inner.map(::key), FlavorWheel.categories.map { key(it.name) })
    }

    @Test fun innerTier_isItsOwnChip_unlessAGroupAlreadyHasIt() {
        val floral = FlavorWheel.categories.single { it.name == "Floral" }
        assertNull(FlavorWheel.innerTerm(floral))
        assertNull(FlavorWheel.innerTerm(FlavorWheel.categories.single { it.name == "Green / Vegetative" }))
        assertEquals("Fruity", FlavorWheel.innerTerm(FlavorWheel.categories.single { it.name == "Fruity" }))
        // not a comma inside a term: notes are stored comma-separated
        assertTrue(FlavorWheel.allDescriptors.none { ',' in it })
    }

    @Test fun extras_everyCategoryExists_noDuplicates_noOfficialTerm() {
        val official = (FlavorWheel.allDescriptors + FlavorWheel.categories.flatMap { c -> c.groups.map { it.name } }).map(::key).toSet()
        assertEquals(FlavorWheel.categories.map { it.name }, FlavorWheelExtras.byCategory.keys.toList(), "one list per category, in wheel order")
        for ((category, terms) in FlavorWheelExtras.byCategory) {
            assertTrue(terms.size in 5..25, "$category: ${terms.size} extras")
            for (t in terms) {
                assertTrue(key(t) !in official, "$t ($category) is on the official wheel")
                assertEquals(t.trim(), t)
                assertTrue(',' !in t && t.first().isUpperCase(), t)
            }
        }
        val all = FlavorWheelExtras.allTerms.map { it.lowercase() }
        assertEquals(all.size, all.toSet().size, "no extra twice: ${all.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    @Test fun extras_haveTheNotesTheWheelLacks() {
        assertTrue("Apricot" in FlavorWheelExtras.terms("Fruity"))
        assertTrue("Tartaric Acid" in FlavorWheelExtras.terms("Sour / Fermented"))
        assertTrue("Caramel" in FlavorWheelExtras.terms("Sweet"))
        assertEquals(emptyList(), FlavorWheelExtras.terms("Fruit"))
    }
}
