package com.coffeejournal.ui.bean.b

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.reference.KoreaMapData
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.MapXY
import com.coffeejournal.ui.map.WorldPlaces
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.ui.bean.a.BeanFormat
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
        // beanB-5: the web breakdown counts a repeated home brew once ("1종") and each cafe visit ("1번")
        assertEquals(listOf(Category.BEAN to 1, Category.CAFE to 1), BeanFormat.categoryCounts(libre))
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
            MiscItem(id = "5", type = MiscType.SOURCE, name = "모모스", scope = Scope.DOMESTIC, location = "", createdAt = 5, lat = 35.2280, lng = 129.0870),
            MiscItem(id = "6", type = MiscType.SOURCE, name = "어딘가", scope = Scope.DOMESTIC, location = "우리 동네", createdAt = 6),
        )
        val domestic = FlatItemLogic.roasteryMap(items, records, Scope.DOMESTIC)
        assertEquals(listOf("리브레", "듀잇", "모모스", "어딘가"), domestic.items.map { it.name })
        assertEquals(4, domestic.cups)
        // a location text naming only the 시·도: the pin sits at the 시·도's centre
        val pin = domestic.pins.first()
        assertEquals("리브레", pin.item.name); assertEquals(3, pin.count)
        assertEquals("서울특별시" to false, pin.place to pin.exact)
        val seoul = KoreaMapData.provinces.single { it.code == "11" }
        assertEquals(MapXY(seoul.labelX.toDouble(), seoul.labelY.toDouble()), pin.at)
        assertEquals("11", pin.area)
        // an exact position wins and names its 시·군·구
        val exact = domestic.pins.single { it.item.name == "모모스" }
        assertEquals("부산광역시 금정구" to true, exact.place to exact.exact)
        assertEquals(KoreaProjection.toMap(35.2280, 129.0870), exact.at)
        assertEquals(listOf("듀잇"), domestic.unlocated.map { it.name })
        assertEquals(listOf("어딘가"), domestic.unmatched.map { it.name })
        assertEquals("한국 로스터리 지도. 로스터리 2곳 표시, 위치 미입력 1곳, 지도에서 찾지 못한 곳 1곳. 시·도를 누르면 시·군·구 지도로 확대돼요. 전체 목록은 지도 아래에 있어요.",
            FlatItemLogic.roasteryMapDescription(true, domestic))
        val overseas = FlatItemLogic.roasteryMap(items, records, Scope.OVERSEAS).pins.single()
        assertEquals("미국" to "United States of America", overseas.place to overseas.area)
        assertEquals("United States of America", WorldPlaces.countryAt(overseas.at))
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
