package com.coffeejournal.ui.bean.a

import com.coffeejournal.domain.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VarietyStatsTest {
    private val records = listOf(
        rec("1", name = "예가체프", variety = "Heirloom(74110, 74112)", country = "Ethiopia", createdAt = 1),
        rec("2", name = "구지", variety = "74110", country = "에티오피아", createdAt = 2),
        rec("3", name = "파나마", variety = "Geisha", country = "Panama", createdAt = 3),
        rec("4", name = "콜롬비아", variety = "Caturra, Castillo", country = "Colombia", createdAt = 4),
        rec("5", name = "베트남", variety = "Robusta 외", country = "Vietnam", createdAt = 5),
        rec("6", name = "마라카투라", variety = "Maracaturra", country = "Nicaragua", createdAt = 6),
        rec("c", name = "커핑 카투라", variety = "Caturra", category = Category.CUPPING, parentEntryId = "c", cuppingPlace = "리브레", createdAt = 7),
    )
    private val index = VarietyStats.index(records)

    @Test fun keysAndSpecies() {
        assertEquals("ethiopian heirloom", VarietyStats.key("Heirloom"))
        assertEquals("ethiopian heirloom", VarietyStats.key("Ethiopian Heirloom 74110"))
        assertEquals("gesha", VarietyStats.key("Gesha"))
        assertEquals("geisha", VarietyStats.key("Geisha"))
        assertEquals("robusta", VarietyStats.key("Robusta 외"))
        assertEquals(VarietyStats.ROBUSTA, VarietyStats.species("robusta"))
        assertEquals(VarietyStats.ARABICA, VarietyStats.species("caturra"))
        assertTrue(VarietyStats.isHeirloomNumber("74110"))
        assertFalse(VarietyStats.isHeirloomNumber("7411"))
        assertEquals("마라카투라 (Maracaturra)", VarietyStats.koreanLabel("Maracaturra"))
        assertEquals("SL28", VarietyStats.koreanLabel("SL28"))
    }

    @Test fun indexFoldsSelectionNumbersAndAddsReferenceVarieties() {
        assertEquals(listOf("74110"), index.heirloomSelections.map { it.key })
        val heirloom = index.groups.getValue("ethiopian heirloom")
        assertEquals(2, heirloom.records.size) // record 1 (Heirloom) + record 2 (74110)
        assertTrue(index.groups.containsKey("typica")) // reference variety without records
        assertTrue(index.groups.getValue("typica").records.isEmpty())
        assertEquals(2, index.groups.getValue("caturra").records.size)
        assertEquals("게이샤 (Geisha)", VarietyStats.label(index.groups.getValue("geisha")))
    }

    @Test fun explorerFiltersBySpeciesSearchAndSort() {
        val arabica = VarietyStats.explorer(index, VarietyStats.ARABICA, "", VarietyStats.SORT_ALPHA)
        assertTrue(arabica.items.none { it.key == "robusta" || VarietyStats.isHeirloomNumber(it.key) })
        assertTrue(arabica.items.any { it.key == "maracaturra" })
        val lineage = VarietyStats.explorer(index, VarietyStats.ARABICA, "", VarietyStats.SORT_LINEAGE)
        assertEquals("ethiopian heirloom", lineage.items.first().key)
        assertEquals("maracaturra", lineage.items.last().key) // unknown lineage ranks after the 21 known
        // a 74xxx query shows only Heirloom
        assertEquals(listOf("ethiopian heirloom"), VarietyStats.explorer(index, VarietyStats.ARABICA, "74110", VarietyStats.SORT_ALPHA).items.map { it.key })
        // a query matching another species switches the species automatically
        val robusta = VarietyStats.explorer(index, VarietyStats.ARABICA, "로부스타", VarietyStats.SORT_ALPHA)
        assertEquals(VarietyStats.ROBUSTA, robusta.species)
        assertEquals(listOf("robusta"), robusta.items.map { it.key })
        assertEquals(1, VarietyStats.speciesCount(index, VarietyStats.ROBUSTA))
        assertEquals(0, VarietyStats.speciesCount(index, VarietyStats.LIBERICA))
    }

    @Test fun profileGroupsAndLineage() {
        assertEquals("재래종 · 선발", VarietyStats.lineage("geisha").type) // alias → gesha
        assertEquals("계보 정보 준비 중", VarietyStats.lineage("maracaturra").type)
        val caturra = VarietyStats.recordGroups(index, index.groups.getValue("caturra"))
        assertEquals(listOf("카투라 (Caturra) 단일 품종", "카투라 (Caturra) 포함 · 복수 품종"), caturra.map { it.title })
        assertEquals(listOf("c"), caturra[0].records.map { it.entryId })
        assertEquals(listOf("4"), caturra[1].records.map { it.entryId })
        assertFalse(VarietyStats.isSingle(records[4], "robusta")) // "… 외" is never single
        val heirloom = VarietyStats.recordGroups(index, index.groups.getValue("ethiopian heirloom"))
        assertEquals(listOf("Heirloom · 74110", "Heirloom · 번호 미상"), heirloom.map { it.title })
        assertEquals(listOf("2"), heirloom[0].records.map { it.entryId })
        assertEquals(listOf("1"), heirloom[1].records.map { it.entryId })
        assertTrue(VarietyStats.recordGroups(index, index.groups.getValue("typica")).isEmpty())
        assertEquals(1, VarietyStats.origins(VarietyStats.uniqueRecords(index.groups.getValue("ethiopian heirloom"))))
    }

    @Test fun byCountryKeepsSubNumbersAndPlaces() {
        val groups = VarietyStats.byCountry(records)
        val ethiopia = groups.first { it.country == "에티오피아(Ethiopia)" }
        assertEquals(listOf("74110", "Heirloom (74110, 74112)"), ethiopia.rows.map { it.display })
        val colombia = groups.first { it.country == "콜롬비아(Colombia)" }
        assertEquals(listOf("Castillo", "Caturra"), colombia.rows.map { it.display })
        val places = BeanFormat.categoriesWithPlaces(index.groups.getValue("caturra").records)
        assertEquals(listOf(Category.BEAN to emptyList(), Category.CUPPING to listOf("리브레")), places)
    }
}
