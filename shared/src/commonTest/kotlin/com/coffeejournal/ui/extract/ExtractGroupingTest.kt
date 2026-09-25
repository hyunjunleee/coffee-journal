package com.coffeejournal.ui.extract

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractGroupingTest {
    private val day0 = Dates.startOfDayMillis(LocalDate(2026, 7, 1))
    private fun at(days: Int, hour: Int = 9) = day0 + days * Dates.DAY_MS + hour * 3_600_000L
    private fun brew(id: String, name: String, days: Int, block: Entry.() -> Entry = { this }) =
        Entry(id = id, createdAt = at(days), category = Category.BEAN, name = name).block()

    @Test fun groupsAreKeyedByCoreNameAndOrderedByFirstRecord() {
        val entries = listOf(
            brew("a1", "Kenya AA (리브레)", 0),
            brew("a2", "kenya aa", 5),
            brew("b1", "벤사", 3),
            brew("c1", "게이샤", 10),
        )
        val groups = ExtractGrouping.buildGroups(entries.map { ListedEntry(it) }, entries)
        assertEquals(listOf("게이샤", "벤사", "kenya aa"), groups.map { it.key })
        val kenya = groups.last()
        assertEquals(listOf("a2", "a1"), kenya.entries.map { it.entry.id })
        assertEquals("Kenya AA", kenya.displayName)
        assertEquals("7.1 ~ 7.6", kenya.dateRangeLabel)
        assertEquals("7.4", groups[1].dateRangeLabel)
        assertEquals(2, kenya.count)
    }

    @Test fun customBlendJoinsEveryComponentGroup() {
        val blend = brew("x", "나만의 블렌드", 2) {
            copy(beanMode = BeanMode.CUSTOM_BLEND, blendComponents = listOf(BlendComponent("벤사", "10"), BlendComponent("게이샤", "8")))
        }
        val entries = listOf(brew("b1", "벤사", 0), brew("g1", "게이샤 (센터커피)", 1), blend)
        val groups = ExtractGrouping.buildGroups(entries.map { ListedEntry(it) }, entries)
        assertEquals(2, groups.size)
        assertTrue(groups.all { g -> g.entries.any { it.entry.id == "x" } })
        assertEquals("직접 블렌드 · 벤사 10g + 게이샤 8g", groups.first { it.key == "벤사" }.customBlendLine)
        assertEquals(listOf("게이샤", "벤사"), groups.map { it.key })
    }

    @Test fun singleBeanHomeCuppingIsProjectedIntoBrewGroupOnly() {
        val brews = listOf(brew("b1", "벤사", 0))
        val home = Entry(id = "c1", createdAt = at(1), category = Category.CUPPING, cuppingType = "홈커핑", cuppingBeans = listOf(CuppingBean(name = "벤사 (리브레)", roastery = "리브레")))
        val twoBeans = home.copy(id = "c2", cuppingBeans = home.cuppingBeans + CuppingBean(name = "게이샤"))
        val publicCupping = home.copy(id = "c3", cuppingType = "퍼블릭")
        val other = home.copy(id = "c4", cuppingBeans = listOf(CuppingBean(name = "예가체프")))
        val projected = ExtractGrouping.projectHomeCuppings(brews + listOf(home, twoBeans, publicCupping, other), brews)
        assertEquals(listOf("c1"), projected.map { it.entry.id })
        assertTrue(projected.single().isHomeCuppingProjection)
        assertEquals("벤사 (리브레)", projected.single().entry.name)
        val groups = ExtractGrouping.buildGroups(brews.map { ListedEntry(it) } + projected, brews)
        assertEquals(1, groups.size)
        assertEquals(listOf("c1", "b1"), groups.single().entries.map { it.entry.id })
        assertEquals(listOf("b1"), groups.single().recipeEntries.map { it.id })
    }

    @Test fun smallPackFilterSelectsPackagesAndOpenedPantryPacks() {
        val entries = listOf(
            brew("s", "일반", 0),
            brew("d", "드립백 원두", 1) { copy(packageType = PackageType.DRIPBAG) },
            brew("m", "소량 원두", 2) { copy(packageType = PackageType.SAMPLE) },
            Entry(id = "cafe", createdAt = at(3), category = Category.CAFE, name = "카페 원두"),
        )
        val pantry = listOf(
            PantryItem(id = "p1", name = "개봉 드립백", packageType = PackageType.DRIPBAG, status = PantryItem.STATUS_OPENED, openedAt = at(1), createdAt = at(0)),
            PantryItem(id = "p2", name = "미개봉 드립백", packageType = PackageType.DRIPBAG, createdAt = at(0)),
            PantryItem(id = "p3", name = "개봉 일반", status = PantryItem.STATUS_OPENED, openedAt = at(2), createdAt = at(0)),
            PantryItem(id = "p4", name = "레거시 20g", packageType = "", weight = "20", status = PantryItem.STATUS_OPENED, openedAt = at(3), createdAt = at(0)),
        )
        val all = ExtractGrouping.selectEntries(entries, pantry, ExtractFilter.ALL, SmallPackFilter.ALL)
        assertEquals(listOf("s"), all.brewEntries.map { it.id })
        assertTrue(all.openedSmallPacks.isEmpty())

        val small = ExtractGrouping.selectEntries(entries, pantry, ExtractFilter.SMALLPACK, SmallPackFilter.ALL)
        assertEquals(setOf("d", "m"), small.brewEntries.map { it.id }.toSet())
        assertEquals(listOf("p4", "p1"), small.openedSmallPacks.map { it.id })

        val drip = ExtractGrouping.selectEntries(entries, pantry, ExtractFilter.SMALLPACK, SmallPackFilter.DRIPBAG)
        assertEquals(listOf("d"), drip.brewEntries.map { it.id })
        assertEquals(listOf("p1"), drip.openedSmallPacks.map { it.id })
        assertEquals("아직 드립백 기록이 없습니다.", ExtractGrouping.emptyText(ExtractFilter.SMALLPACK, SmallPackFilter.DRIPBAG))
        assertEquals("아직 드립백이나 소량 기록이 없습니다.", ExtractGrouping.emptyText(ExtractFilter.SMALLPACK, SmallPackFilter.ALL))
        assertEquals("아직 추출 기록이 없습니다. 오늘 내린 커피부터 남겨보세요.", ExtractGrouping.emptyText(ExtractFilter.ALL, SmallPackFilter.ALL))
    }

    @Test fun searchMatchesNameRoasteryImporterAcrossCategories() {
        val entries = listOf(
            brew("b1", "벤사 내추럴", 0) { copy(roastery = "리브레") },
            Entry(id = "cafe", createdAt = at(2), category = Category.CAFE, name = "게이샤", roastery = "커피 리브레", cafeName = "리브레 연남"),
            Entry(id = "cup", createdAt = at(3), category = Category.CUPPING, cuppingType = "퍼블릭", cuppingPlace = "센터", cuppingBeans = listOf(CuppingBean(name = "벤사"), CuppingBean(name = "시다모"))),
            brew("b2", "예가체프", 4) { copy(selection = "모모스 셀렉션") },
        )
        assertNull(ExtractGrouping.search(entries, "   "))
        val r = ExtractGrouping.search(entries, "리 브레")!!
        assertEquals(2, r.total)
        assertEquals(listOf("게이샤", "벤사 내추럴"), r.groups.map { it.name })
        val bensa = ExtractGrouping.search(entries, "벤사")!!
        assertEquals(2, bensa.total)
        assertEquals(listOf("벤사", "벤사 내추럴"), bensa.groups.map { it.key })
        val row = ExtractGrouping.searchRow(bensa.groups.first().records.single())
        assertEquals("커핑", row.badge); assertEquals("퍼블릭", row.cuppingType); assertEquals("센터", row.place); assertEquals("cup", row.entryId)
        assertEquals("직접 내림", ExtractGrouping.searchRow(bensa.groups.last().records.single()).badge)
        assertEquals("집 추출", ExtractGrouping.searchRow(bensa.groups.last().records.single()).place)
        assertEquals(1, ExtractGrouping.search(entries, "모모스")!!.total)
        assertTrue(ExtractGrouping.search(entries, "없는이름")!!.groups.isEmpty())
    }

    @Test fun drinkingPrefersOpenedStandardBagsSortedByPeak() {
        val entries = listOf(brew("b1", "벤사", 0) { copy(dose = "15", bagWeight = "200") }, brew("b2", "벤사", 1) { copy(dose = "15.5") })
        val blends = listOf(Blend(id = "bl", beans = listOf(BlendComponent("벤사", "10")), createdAt = at(1)))
        val pantry = listOf(
            PantryItem(id = "late", name = "벤사", weight = "200", roastDate = "2026-06-20", roastLevel = "미디엄", status = PantryItem.STATUS_OPENED, openedAt = at(2), createdAt = at(0)),
            PantryItem(id = "early", name = "게이샤", roastDate = "2026-06-01", roastLevel = "라이트", status = PantryItem.STATUS_OPENED, openedAt = at(1), createdAt = at(0)),
            PantryItem(id = "blend", name = "하우스 블렌드", status = PantryItem.STATUS_OPENED, createdAt = at(0)),
            PantryItem(id = "decaf", name = "디카페인 콜롬비아", status = PantryItem.STATUS_OPENED, createdAt = at(0)),
            PantryItem(id = "drip", name = "드립백", packageType = PackageType.DRIPBAG, status = PantryItem.STATUS_OPENED, createdAt = at(0)),
            PantryItem(id = "closed", name = "미개봉", createdAt = at(0)),
        )
        val state = ExtractGrouping.drinking(entries, pantry, blends, emptyList(), today = LocalDate(2026, 7, 10))
        val cards = assertIs<DrinkingState.OpenedBags>(state).cards
        assertEquals(listOf("early", "late"), cards.map { it.item.id })
        val bensa = cards.last()
        assertEquals("잔여량 159.5g/200g", bensa.remainingLine)
        assertEquals("마시는 중 · 2026.7.3.~", bensa.eyebrow)
        assertEquals("잔여량 100g/100g", cards.first().remainingLine)
    }

    @Test fun drinkingFallsBackToMostRecentStandardBrew() {
        val entries = listOf(
            brew("old", "벤사", 0) { copy(dose = "15", bagWeight = "250", farmProducer = "봄베 농장") },
            brew("new", "벤사 (리브레)", 3) { copy(dose = "16") },
            brew("blend", "블렌드 실험", 5) { copy(beanMode = BeanMode.CUSTOM_BLEND, blendComponents = listOf(BlendComponent("벤사", "5"))) },
            brew("decaf", "디카페인 브라질", 6),
            brew("drip", "드립백", 7) { copy(packageType = PackageType.DRIPBAG) },
            Entry(id = "cafe", createdAt = at(8), category = Category.CAFE, name = "카페 게이샤"),
        )
        val misc = listOf(MiscItem(id = "f", type = MiscType.FARM, name = "봄베 농장 ", notes = "시다마 벤사의 고지대 농장", createdAt = 0))
        val state = ExtractGrouping.drinking(entries, emptyList(), emptyList(), misc, today = LocalDate(2026, 7, 10))
        val card = assertIs<DrinkingState.RecentBean>(state).card
        assertEquals("new", card.entry.id)
        assertEquals("마시는 중 · 10일째(2026.7.1.(수)~)", card.eyebrow)
        assertEquals("잔여량 214g/250g", card.remainingLine)
        assertEquals("시다마 벤사의 고지대 농장", card.description)
        assertEquals(listOf(InfoLine("로스터리", "리브레"), InfoLine("농장", "봄베 농장")), card.infoLines)

        assertEquals(DrinkingState.None, ExtractGrouping.drinking(listOf(entries.last()), emptyList(), emptyList(), emptyList(), today = LocalDate(2026, 7, 10)))
    }

    @Test fun knownBlendNameAndRows() {
        val entries = listOf(brew("b", "하우스", 0) { copy(beanMode = BeanMode.COMMERCIAL_BLEND) }, brew("s", "벤사", 1))
        assertTrue(ExtractGrouping.isKnownBlendName("Morning Blend", entries))
        assertTrue(ExtractGrouping.isKnownBlendName("하우스 (리브레)", entries))
        assertFalse(ExtractGrouping.isKnownBlendName("벤사", entries))

        val en = brew("r", "벤사", 0) { copy(dose = "15", water = "240", temp = "92", dripper = "V60", actualNotes = "a".repeat(80), packageType = PackageType.SAMPLE, attributes = mapOf("flavor" to 8.0, "uniformity" to 10.0)) }
        val row = ExtractGrouping.entryRow(en, isBest = true)
        assertEquals("15g · 240g · 92°C · V60", row.recipeLine)
        assertEquals("2026.07.01", row.dateText)
        assertEquals("소량", row.packageBadge)
        assertEquals("18.00 / 100", row.scoreText)
        assertEquals(60, row.notePreview.length)
        assertTrue(row.isBest)
        assertNotNull(ExtractGrouping.smallPackCard(PantryItem(id = "p", name = "x", weight = "20", createdAt = 0)).statsLine.contains("20g"))
    }

    @Test fun pantryListingSortsAndMarks() {
        val items = listOf(
            PantryItem(id = "a", name = "a", roastDate = "2026-06-01", roastLevel = "다크", createdAt = at(0)),
            PantryItem(id = "b", name = "b", createdAt = at(1)),
            PantryItem(id = "c", name = "c", peakStart = "2026-06-03", createdAt = at(2)),
            PantryItem(id = "o", name = "o", status = PantryItem.STATUS_OPENED, openedAt = at(5), createdAt = at(3)),
        )
        assertEquals(listOf("c", "b", "a"), PantryListing.unopened(items, PantryListing.SORT_REGISTERED).map { it.id })
        assertEquals(listOf("c", "a", "b"), PantryListing.unopened(items, PantryListing.SORT_PEAK).map { it.id })
        assertEquals(listOf("o"), PantryListing.opened(items).map { it.id })
        val opened = PantryListing.markOpened(items[1].copy(packageType = "", weight = "20"), at(9))
        assertEquals(PantryItem.STATUS_OPENED, opened.status); assertEquals(at(9), opened.openedAt); assertEquals(PackageType.SAMPLE, opened.packageType)
        val back = PantryListing.markUnopened(opened)
        assertEquals(PantryItem.STATUS_UNOPENED, back.status); assertNull(back.openedAt)
        assertEquals("이 소량를 개봉하고 드립백 / 소량으로 옮길까요?", PantryListing.openConfirmText(opened))
        assertEquals("이 원두를 개봉하고 위클리 원두로 옮길까요?", PantryListing.openConfirmText(items[1]))
        assertEquals("일반 원두 · 리브레 · 200g · 18,000원 · 100g 환산 9,000원 · 로스팅 2026-06-01 · 구매 2026-06-05",
            PantryListing.metaLine(PantryItem(id = "m", name = "m", roastery = "리브레", weight = "200", price = "18000", roastDate = "2026-06-01", purchaseDate = "2026-06-05", createdAt = 0)))
    }

    // home-3: the opened-bag card shows the web's info lines (filled from same-name records), not roast date / weight
    @Test fun openedBagCardShowsInfoLinesFromSameNameRecords() {
        val entries = listOf(
            brew("b1", "워카 첼베사", 0) { copy(roastery = "커피 리브레", selection = "Nordic Approach", farmProducer = "워카 첼베사(SNAP)", washingStation = "첼베사") },
        )
        val pantry = listOf(PantryItem(id = "p", name = "워카 첼베사", weight = "200", price = "18000", roastDate = "2026-06-20", status = PantryItem.STATUS_OPENED, openedAt = at(2), createdAt = at(0)))
        val card = assertIs<DrinkingState.OpenedBags>(ExtractGrouping.drinking(entries, pantry, emptyList(), emptyList(), today = LocalDate(2026, 7, 10))).cards.single()
        assertEquals(
            listOf(InfoLine("로스터리", "커피 리브레"), InfoLine("생두 수입사", "Nordic Approach"), InfoLine("농장", "워카 첼베사(SNAP)"), InfoLine("워싱 스테이션", "첼베사")),
            card.infoLines,
        )
        assertEquals("200g · 18,000원 · 100g 환산 9,000원", card.priceText)
        // the bag's own roastery wins over the records'
        val own = pantry.single().copy(roastery = "모모스")
        val ownCard = assertIs<DrinkingState.OpenedBags>(ExtractGrouping.drinking(entries, listOf(own), emptyList(), emptyList(), today = LocalDate(2026, 7, 10))).cards.single()
        assertEquals(InfoLine("로스터리", "모모스"), ownCard.infoLines.first())
    }

    // home-5: home-brew rows drop "원두"; a custom-blend row names the blend
    @Test fun entryRowsHideBeanCategoryAndLabelCustomBlends() {
        assertEquals("", ExtractGrouping.entryRow(brew("b", "벤사", 0), isBest = false).categoryText)
        assertNull(ExtractGrouping.entryRow(brew("b", "벤사", 0), isBest = false).blendLabel)
        val blend = brew("x", "벤사 + 게이샤", 1) { copy(beanMode = BeanMode.CUSTOM_BLEND, blendComponents = listOf(BlendComponent("벤사", "10"), BlendComponent("게이샤", "8"))) }
        assertEquals("직접 블렌드 · 벤사 + 게이샤", ExtractGrouping.entryRow(blend, isBest = false).blendLabel)
        val cup = Entry(id = "c", createdAt = at(2), category = Category.CUPPING, cuppingType = "홈커핑", name = "벤사")
        assertEquals("커핑 · 홈커핑", ExtractGrouping.entryRow(cup, isBest = false).categoryText)
    }

    // home-6: the most-recent-bean card subtracts only standard single-bean brews (plus blend components)
    @Test fun recentBeanRemainingIgnoresDripBagsSamplesAndDecafOfTheSameName() {
        val entries = listOf(
            brew("b1", "게이샤", 0) { copy(dose = "12", bagWeight = "200") },
            brew("d1", "게이샤", 1) { copy(dose = "12", packageType = PackageType.DRIPBAG) },
            brew("s1", "게이샤", 2) { copy(dose = "10", packageType = PackageType.SAMPLE) },
            brew("x1", "게이샤 + 벤사", 3) { copy(dose = "15", beanMode = BeanMode.CUSTOM_BLEND, blendComponents = listOf(BlendComponent("게이샤", "7"), BlendComponent("벤사", "8"))) },
        )
        val blends = listOf(Blend(id = "lab", beans = listOf(BlendComponent("게이샤", "5")), createdAt = at(1)))
        val card = assertIs<DrinkingState.RecentBean>(ExtractGrouping.drinking(entries, emptyList(), blends, emptyList(), today = LocalDate(2026, 7, 10))).card
        // 200 − 12 (standard brew) − 7 (custom-blend component) − 5 (lab blend); the drip bag and sample do not count
        assertEquals("잔여량 176g/200g", card.remainingLine)
    }

    // home-7: the stored importer text is searchable, "셀렉션" included
    @Test fun searchFindsTheStoredImporterText() {
        val entries = listOf(brew("b", "예가체프", 0) { copy(selection = "모모스 셀렉션") })
        assertEquals(1, ExtractGrouping.search(entries, "모모스 셀렉션")!!.total)
        assertEquals(1, ExtractGrouping.search(entries, "셀렉션")!!.total)
        assertEquals(1, ExtractGrouping.search(entries, "모모스")!!.total)
    }

    // hand-off from the data lane: stored "NaN" / "Infinity" weights never render as "NaNg"
    @Test fun nonFiniteWeightsRenderNoGrams() {
        assertEquals("", ExtractGrouping.weightText("NaN"))
        assertEquals("", ExtractGrouping.weightText("Infinity"))
        assertEquals("", ExtractGrouping.weightText("0"))
        assertEquals("200g", ExtractGrouping.weightText(" 200 "))
        assertEquals("12.5g", ExtractGrouping.weightText("12.5"))
        assertEquals("리브레", ExtractGrouping.smallPackCard(PantryItem(id = "p", name = "x", roastery = "리브레", weight = "NaN", createdAt = 0)).statsLine)
        assertEquals("소량 · 리브레", PantryListing.metaLine(PantryItem(id = "p", name = "x", roastery = "리브레", weight = "Infinity", packageType = PackageType.SAMPLE, createdAt = 0)))
    }
}
