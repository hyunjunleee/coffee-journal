package com.coffeejournal.ui.bean.a

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.rules.RoastFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun rec(
    id: String,
    name: String = "Bean $id",
    category: String = Category.BEAN,
    createdAt: Long = 1_000L,
    parentEntryId: String? = null,
    country: String = "",
    region: String = "",
    variety: String = "",
    process: String = "",
    processOther: String = "",
    roast: String = "",
    expectedNotes: String = "",
    actualNotes: String = "",
    beanMode: String = "single",
    score: String = "",
    cafeName: String = "",
    cuppingPlace: String = "",
): BeanRecord = BeanRecord(
    entryId = id, parentEntryId = parentEntryId, category = category, createdAt = createdAt, name = name,
    country = country, region = region, farmProducer = "", roastery = "", selection = "", altitude = "",
    variety = variety, process = process, processOther = processOther, roast = roast,
    expectedNotes = expectedNotes, actualNotes = actualNotes, notes = "", beanMode = beanMode,
    blendComponents = emptyList(), blendComponentsText = "", score = score, cafeName = cafeName,
    cuppingPlace = cuppingPlace, cuppingType = "", roasterDesc = "", packageType = "standard",
)

class NoteStatsTest {
    private val records = listOf(
        rec("a", name = "예가체프 (리브레)", expectedNotes = "재스민, 오렌지", actualNotes = "자스민, 홍차", createdAt = 3),
        rec("b", name = "예가체프", expectedNotes = "jasmine, 복숭아", actualNotes = "", createdAt = 2),
        rec("c", name = "케냐 AA", category = Category.CAFE, cafeName = "글리치", expectedNotes = "오렌지", actualNotes = "자몽.", createdAt = 1),
    )

    @Test fun cloudMergesSynonymsAndCountsUniqueBeans() {
        val cloud = NoteStats.aggregate(records, NoteStats.EXPECTED)
        assertEquals(setOf("자스민", "오렌지", "복숭아"), cloud.keys)
        assertEquals(1, cloud.getValue("자스민").beanCount) // both 예가체프 records share one core name
        assertEquals(2, cloud.getValue("오렌지").beanCount)
        assertEquals(2, cloud.getValue("자스민").records.size)
    }

    @Test fun sortAndSearch() {
        val cloud = NoteStats.aggregate(records, NoteStats.EXPECTED).values
        assertEquals(listOf("오렌지", "자스민", "복숭아"), NoteStats.filterAndSort(cloud, "", NoteStats.SORT_COUNT).map { it.label })
        assertEquals(listOf("복숭아", "오렌지", "자스민"), NoteStats.filterAndSort(cloud, "", NoteStats.SORT_ALPHA).map { it.label })
        // place and bean name are searchable too
        assertEquals(listOf("오렌지"), NoteStats.filterAndSort(cloud, "글리치", NoteStats.SORT_COUNT).map { it.label })
        assertEquals(listOf("오렌지", "자스민", "복숭아"), NoteStats.filterAndSort(cloud, "예가", NoteStats.SORT_COUNT).map { it.label })
    }

    @Test fun actualModeAndCombinations() {
        val actual = NoteStats.aggregate(records, NoteStats.ACTUAL)
        assertEquals(setOf("자스민", "홍차", "자몽"), actual.keys)
        val combos = NoteStats.combinations(NoteStats.aggregate(records, NoteStats.EXPECTED).getValue("자스민"), NoteStats.EXPECTED)
        assertEquals(2, combos.size)
        assertEquals(listOf("자스민", "오렌지"), combos[0].labels)
        assertEquals(1, combos[0].beanCount)
        assertEquals("아직 내가 기록한 노트가 없어요.", NoteStats.emptyText(NoteStats.ACTUAL, ""))
        assertEquals("검색 결과가 없어요.", NoteStats.emptyText(NoteStats.ACTUAL, "x"))
    }
}

class ProcessStatsTest {
    private val honeyParen = rec("h1", process = "허니(더블 퍼멘티드)")
    private val redHoney = rec("h2", process = "기타", processOther = "Red Honey")
    private val washed = rec("w1", process = "워시드", country = "Ethiopia", variety = "Heirloom")
    private val decaf = rec("d1", process = "기타", processOther = "디카페인(슈거케인 프로세스)", country = "콜롬비아")
    private val anaerobicCupping = rec("c1", process = "아나로빅", category = Category.CUPPING, parentEntryId = "c1", cuppingPlace = "리브레")
    private val all = listOf(honeyParen, redHoney, washed, decaf, anaerobicCupping)

    @Test fun matchingFollowsTheWebRules() {
        assertTrue(ProcessStats.matches(honeyParen, "허니", "허니"))
        assertTrue(ProcessStats.matches(redHoney, "허니", "허니"))
        assertFalse(ProcessStats.matches(washed, "허니", "허니"))
        assertTrue(ProcessStats.matches(decaf, "디카페인", null))
        assertFalse(ProcessStats.matches(decaf, "워시드", "워시드"))
        assertTrue(ProcessStats.matches(anaerobicCupping, "아나로빅", "아나로빅"))
    }

    @Test fun honeySubtypes() {
        assertEquals("더블 퍼멘티드", ProcessStats.honeySubtype(honeyParen))
        assertEquals("레드 허니", ProcessStats.honeySubtype(redHoney))
        assertEquals("세부 미기록", ProcessStats.honeySubtype(rec("x", process = "허니")))
        val groups = ProcessStats.honeyGroups(listOf(honeyParen, redHoney, rec("x", process = "허니")))
        assertEquals(listOf("더블 퍼멘티드", "레드 허니", "세부 미기록"), groups.map { it.first })
    }

    @Test fun searchGroupsAndKnownMethods() {
        val groups = ProcessStats.search(all, "허니")
        assertEquals(listOf("허니(더블 퍼멘티드)"), groups.filter { it.records.isNotEmpty() }.map { it.label })
        assertTrue(groups.any { it.label == "허니" && it.records.isEmpty() }) // known method without a matching record
        val english = ProcessStats.search(all, "honey")
        assertEquals(listOf("Red Honey", "허니"), english.map { it.label }) // records first, then the known method
        assertEquals("디카페인(슈거케인 프로세스)", ProcessStats.searchLabel(decaf))
        assertEquals("허니(더블 퍼멘티드)", ProcessStats.searchLabel(honeyParen))
        assertTrue(ProcessStats.search(all, "zzz").isEmpty())
        assertTrue(ProcessStats.search(all, "").isEmpty())
    }

    @Test fun breakdownCountsBilingualCountriesAndSubtypes() {
        val b = ProcessStats.breakdown(all, "디카페인", null)
        assertEquals(listOf("콜롬비아(Colombia)" to 1), b.countries)
        assertEquals(listOf("슈거케인 프로세스" to 1), b.subs)
        val w = ProcessStats.breakdown(all, "워시드", "워시드")
        assertEquals(listOf("에티오피아(Ethiopia)" to 1), w.countries)
        assertEquals(listOf("Heirloom" to 1), w.varieties)
        assertEquals(1, ProcessStats.brewGroups(w.matching).size)
        assertTrue(ProcessStats.breakdown(all, "", null).matching.isEmpty())
    }
}

class RoastStatsTest {
    private val records = listOf(
        rec("1", roast = "라이트", createdAt = 1),
        rec("2", roast = "미디엄 라이트", createdAt = 2),
        rec("3", roast = "미디엄 다크", createdAt = 3, beanMode = "commercialBlend"),
        rec("4", roast = "다크", createdAt = 4),
        rec("c", name = "커핑 원두", roast = "다크", createdAt = 5, category = Category.CUPPING, parentEntryId = "c"),
        rec("c", name = "커핑 원두", roast = "다크", createdAt = 5, category = Category.CUPPING, parentEntryId = "c"),
        rec("5", roast = "", createdAt = 6),
    )

    @Test fun countsAndLists() {
        val c = RoastStats.counts(records)
        assertEquals(2, c.light); assertEquals(0, c.medium); assertEquals(4, c.dark); assertEquals(1, c.darkBlend)
        assertEquals("라이트계 · 2", RoastStats.label("라이트계", c.light))
        assertEquals("중간", RoastStats.label("중간", c.medium))
        val dark = RoastStats.list(records, RoastFamily.DARK, RoastStats.FILTER_ALL, RoastStats.SORT_LATEST)
        assertEquals(listOf("c", "4", "3"), dark.map { it.entryId }) // duplicate cupping bean collapsed
        val blend = RoastStats.list(records, RoastFamily.DARK, RoastStats.FILTER_BLEND, RoastStats.SORT_OLDEST)
        assertEquals(listOf("3"), blend.map { it.entryId })
        assertEquals(listOf("1", "2"), RoastStats.list(records, RoastFamily.LIGHT, RoastStats.FILTER_ALL, RoastStats.SORT_OLDEST).map { it.entryId })
    }
}

class CompetitionStatsTest {
    @Test fun lotsKeepOldestPerBeanSortedByScore() {
        val lots = CompetitionStats.lots(
            listOf(
                rec("a", name = "게이샤 (리브레)", score = "88", createdAt = 5),
                rec("b", name = "게이샤", score = "90", createdAt = 2),
                rec("c", name = "브라질", score = "79.5", createdAt = 1),
                rec("d", name = "케냐", score = "85.25", createdAt = 1),
                rec("e", name = "무점수", score = "", createdAt = 1),
            )
        )
        assertEquals(listOf("b", "d"), lots.map { it.record.entryId })
        assertEquals("90점 · 최상급 경매급·명품급", CompetitionStats.badge(lots[0]))
        assertEquals("85.25점 · 고품질 스페셜티", CompetitionStats.badge(lots[1]))
        assertNull(CompetitionStats.score(rec("x")))
    }
}
