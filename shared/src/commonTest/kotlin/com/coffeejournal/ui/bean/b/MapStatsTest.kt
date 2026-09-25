package com.coffeejournal.ui.bean.b

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.domain.rules.Dates
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapStatsTest {
    private fun t(day: Int): Long = Dates.startOfDayMillis(LocalDate(2026, 1, day)) + 36_000_000L
    private val entries = listOf(
        Entry(id = "e1", createdAt = t(10), name = "예가체프 게뎁", country = "에티오피아", region = "예가체프, Gedeb", farmProducer = "워카", process = "워시드", variety = "Heirloom"),
        Entry(id = "e2", createdAt = t(12), name = "예가체프 게뎁", country = "Ethiopia", region = "Yirgacheffe, Gedeb", farmProducer = "워카", process = "워시드"),
        Entry(id = "e3", createdAt = t(11), category = Category.CAFE, name = "콜롬비아 우일라", cafeName = "듀잇"),
        Entry(id = "e4", createdAt = t(13), category = Category.CUPPING, cuppingPlace = "리브레", cuppingBeans = listOf(
            CuppingBean(name = "케냐 AA", country = "Kenya", region = "Nyeri"), CuppingBean(name = "브라질", country = "브라질"),
        )),
        Entry(id = "e5", createdAt = t(14), name = "이름만 있는 원두"),
    )
    private val records = BeanRecords.flatten(entries)

    @Test fun aggregatesCupsAndRegionsPerCountry() {
        val stats = MapStats.compute(records)
        assertEquals(setOf("Ethiopia", "Colombia", "Kenya", "Brazil"), stats.keys)
        val et = stats.getValue("Ethiopia")
        assertEquals(2, et.cups)
        assertEquals(listOf("yirgacheffe"), et.regions.keys.toList())
        assertEquals("Yirgacheffe", et.regions.getValue("yirgacheffe").label)
        assertEquals(1, et.regions.getValue("yirgacheffe").subs["예가체프, Gedeb"])
        assertTrue(MapStats.regionTried(stats, "Kenya", "Nyeri"))
        assertTrue(!MapStats.regionTried(stats, "Brazil", "Cerrado"))
        assertEquals(1, stats.getValue("Colombia").cups, "country falls back to the bean name")
    }

    @Test fun totalCupsCountsCuppingBeans() {
        val t = MapStats.totalCups(entries)
        assertEquals(3, t.brew); assertEquals(1, t.cafe); assertEquals(2, t.cupping); assertEquals(6, t.total)
        assertEquals(1, MapStats.totalCups(listOf(Entry(id = "c", createdAt = 1, category = Category.CUPPING))).cupping)
    }

    @Test fun groupsRegionsThenFarmsWithUnknownLast() {
        val ethiopia = MapStats.recordsByCountry(records).getValue("Ethiopia") + records.first { it.name == "이름만 있는 원두" }.copy(country = "Ethiopia")
        val groups = MapStats.groupByRegionThenFarm(ethiopia)
        assertEquals(listOf("Yirgacheffe", MapStats.UNKNOWN_REGION), groups.map { it.region })
        assertEquals(2, groups[0].total)
        assertEquals("워카", groups[0].farms.single().farm)
        assertEquals(MapStats.UNKNOWN_FARM, groups[1].farms.single().farm)
        val farms = MapStats.farmsForRegion(ethiopia, "yirgacheffe")
        assertEquals(listOf("Gedeb"), farms.single().subs)
        assertTrue(MapStats.farmsForRegion(ethiopia, "Sidamo").isEmpty())
    }

    @Test fun visitSummaryCollapsesBrews() {
        val s = MapStats.visitSummary(MapStats.recordsByCountry(records).getValue("Ethiopia"))
        assertEquals(2, s.brewCount)
        assertTrue(s.brewRange.contains(" ~ "))
        assertEquals(listOf("워시드"), s.processes)
        assertEquals(listOf("Heirloom"), s.varieties)
        assertTrue(s.others.isEmpty())
        val cafe = MapStats.visitSummary(MapStats.recordsByCountry(records).getValue("Colombia"))
        assertEquals(0, cafe.brewCount); assertEquals(1, cafe.others.size)
    }

    @Test fun zoneSectionsFollowZoneOrderAndUntriedFillsTheRest() {
        val byCountry = MapStats.recordsByCountry(records)
        val zones = MapStats.zoneSections(byCountry)
        assertEquals(listOf("Africa", "Americas"), zones.map { it.english })
        assertEquals(listOf("Ethiopia", "Kenya"), zones[0].countries.map { it.country.en })
        val entry = zones[0].countries[0]
        assertEquals("1.12", entry.latest)
        assertEquals(listOf("Yirgacheffe", "워시드", "Heirloom"), entry.tags)
        val untried = MapStats.untriedByZone(byCountry.keys)
        assertEquals(45 - 4, untried.sumOf { it.second.size })
        assertEquals("Africa", untried.first().first)
    }
}
