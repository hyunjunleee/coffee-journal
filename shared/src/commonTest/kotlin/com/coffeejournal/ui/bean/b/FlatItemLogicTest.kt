package com.coffeejournal.ui.bean.b

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.BeanRecords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlatItemLogicTest {
    private val day = 86_400_000L
    private val records = BeanRecords.flatten(
        listOf(
            Entry(id = "a", createdAt = 1 * day, name = "벤사", roastery = "리브레", selection = "노르딕 셀렉션", country = "에티오피아", farmProducer = "워카"),
            Entry(id = "b", createdAt = 5 * day, name = "벤사", roastery = "리브레", selection = "노르딕"),
            Entry(id = "c", createdAt = 3 * day, name = "케냐 (리브레, 노르딕)", category = Category.CAFE, cafeName = "듀잇", country = "케냐"),
            Entry(id = "d", createdAt = 4 * day, name = "브라질", roastery = "듀잇", farmProducer = "워카", country = "브라질"),
        )
    )

    @Test fun matchesRoasteryByFieldOrNameParens() {
        val libre = FlatItemLogic.roasteryRecords(records, " 리브레 ")
        assertEquals(setOf("a", "b", "c"), libre.map { it.entryId }.toSet())
        val spans = FlatItemLogic.beanSpans(libre)
        assertEquals(listOf("벤사", "케냐 (리브레, 노르딕)"), spans.map { it.name })
        assertEquals(1 * day, spans[0].start); assertEquals(5 * day, spans[0].end)
        assertEquals(listOf("집 추출", "듀잇"), FlatItemLogic.places(libre))
        assertTrue(FlatItemLogic.roasteryRecords(records, "").isEmpty())
    }

    @Test fun farmAndSelectionMatching() {
        assertEquals(listOf("에티오피아" to 1, "브라질" to 1), FlatItemLogic.countryCounts(FlatItemLogic.farmRecords(records, "워카")))
        val nordic = FlatItemLogic.selectionRecords(records, "노르딕")
        assertEquals(setOf("a", "b", "c"), nordic.map { it.entryId }.toSet())
        assertEquals(listOf("에티오피아" to 1, "국가 미상" to 1, "케냐" to 1), FlatItemLogic.countryCounts(nordic))
        assertEquals(listOf("벤사" to Category.BEAN, "케냐 (리브레, 노르딕)" to Category.CAFE), FlatItemLogic.distinctBeans(nordic))
    }

    @Test fun roasteryMapPinsAndUnlocated() {
        val items = listOf(
            MiscItem(id = "1", type = MiscType.SOURCE, name = "리브레", scope = Scope.DOMESTIC, location = "서울 연남동", createdAt = 1),
            MiscItem(id = "2", type = MiscType.SOURCE, name = "듀잇", scope = Scope.DOMESTIC, location = "", createdAt = 2),
            MiscItem(id = "3", type = MiscType.SOURCE, name = "Onyx", scope = Scope.OVERSEAS, location = "USA", createdAt = 3),
            MiscItem(id = "4", type = MiscType.FARM, name = "워카", createdAt = 4),
        )
        val domestic = FlatItemLogic.roasteryMap(items, records, Scope.DOMESTIC)
        assertEquals(listOf("리브레", "듀잇"), domestic.items.map { it.name })
        assertEquals(4, domestic.cups)
        val pin = domestic.pins.single()
        assertEquals("리브레", pin.item.name); assertEquals(3, pin.count)
        assertEquals(48f - 3.5f, pin.x); assertEquals(27f - 5f, pin.y)
        assertEquals(listOf("듀잇"), domestic.unlocated.map { it.name })
        val overseas = FlatItemLogic.roasteryMap(items, records, Scope.OVERSEAS)
        assertEquals(18f - 3.5f, overseas.pins.single().x)
    }

    @Test fun statusSplitAndFavoriteOrdering() {
        val items = listOf(
            MiscItem(id = "1", type = MiscType.SOURCE, name = "old", createdAt = 1),
            MiscItem(id = "2", type = MiscType.SOURCE, name = "new", createdAt = 2),
            MiscItem(id = "3", type = MiscType.SOURCE, name = "fav", favorite = true, createdAt = 0),
            MiscItem(id = "4", type = MiscType.SOURCE, name = "wish", status = MiscStatus.CURIOUS, createdAt = 9),
        )
        val (tried, curious) = FlatItemLogic.splitByStatus(items, favoritable = true)
        assertEquals(listOf("fav", "new", "old"), tried.map { it.name })
        assertEquals(listOf("wish"), curious.map { it.name })
        assertEquals(listOf("new", "old", "fav"), FlatItemLogic.splitByStatus(items, favoritable = false).first.map { it.name })
        assertTrue(FlatItemLogic.matchesSearch(items[0], "OL"))
        assertTrue(!FlatItemLogic.matchesSearch(items[0], "zzz"))
        assertEquals("로스터리", FlatItemLogic.spec(MiscType.SOURCE).label)
        assertTrue(!FlatItemLogic.spec(MiscType.PROCESS).hasStatus)
    }
}
