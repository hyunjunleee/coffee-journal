package com.coffeejournal.ui.misc

import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiscListLogicTest {
    private fun item(id: String, type: String, createdAt: Long, status: String = "", since: String = "") =
        MiscItem(id = id, type = type, name = id, status = status, since = since, createdAt = createdAt)

    private val items = listOf(
        item("d1", MiscType.DRIPPER, 100, status = MiscStatus.OWNED),
        item("d2", MiscType.DRIPPER, 300, status = MiscStatus.CURIOUS),
        item("d3", MiscType.DRIPPER, 200), // blank status counts as 보유 (web: status || '보유')
        item("k1", MiscType.KETTLE, 400),
        item("w1", MiscType.WATER, 50),
        item("s1", MiscType.SOURCE, 999), // roastery: never shown in the 기타 tab
        item("p1", MiscType.PROCESS, 998),
    )

    @Test fun equipmentOnlyDropsBeanTabTypes() {
        assertEquals(listOf("d1", "d2", "d3", "k1", "w1"), MiscListLogic.equipmentOnly(items).map { it.id })
        assertEquals(0, MiscListLogic.filtered(items, MiscType.FILTER).size)
        assertTrue(MiscListLogic.sections(items, MiscType.FILTER, MiscListLogic.SORT_FIXED).isEmpty())
        assertEquals("아직 등록한 필터이(가) 없어요.", MiscListLogic.emptyText(MiscType.FILTER))
        assertEquals("아직 등록한 전체 장비이(가) 없어요.", MiscListLogic.emptyText(MiscListLogic.ALL))
    }

    @Test fun allFixedGroupsByTypeOrderNewestFirst() {
        val sections = MiscListLogic.sections(items, MiscListLogic.ALL, MiscListLogic.SORT_FIXED)
        assertEquals(listOf("드리퍼", "케틀", "물"), sections.map { it.label })
        assertEquals(listOf("d2", "d3", "d1"), sections[0].items.map { it.id })
        assertEquals(listOf("k1"), sections[1].items.map { it.id })
        assertTrue(sections.all { it.emptyText == null })
    }

    @Test fun allRecentIsOneFlatListNewestFirst() {
        val sections = MiscListLogic.sections(items, MiscListLogic.ALL, MiscListLogic.SORT_RECENT)
        assertEquals(1, sections.size)
        assertNull(sections[0].label)
        assertEquals(listOf("k1", "d2", "d3", "d1", "w1"), sections[0].items.map { it.id })
    }

    @Test fun singleTypeSplitsOwnedAndCurious() {
        val sections = MiscListLogic.sections(items, MiscType.DRIPPER, MiscListLogic.SORT_FIXED)
        assertEquals(listOf("보유 드리퍼", "궁금한 드리퍼"), sections.map { it.label })
        assertEquals(listOf("d3", "d1"), sections[0].items.map { it.id })
        assertEquals(listOf("d2"), sections[1].items.map { it.id })
        assertEquals("아직 등록한 드리퍼이(가) 없어요.", sections[0].emptyText)
        assertEquals("궁금한 드리퍼을(를) 추가해보세요.", sections[1].emptyText)

        val kettle = MiscListLogic.sections(items, MiscType.KETTLE, MiscListLogic.SORT_FIXED)
        assertEquals(listOf("k1"), kettle[0].items.map { it.id })
        assertTrue(kettle[1].items.isEmpty())
    }

    @Test fun labelsAndSinceFormatting() {
        assertEquals("전체 장비", MiscListLogic.title(MiscListLogic.ALL))
        assertEquals("장비", MiscListLogic.name(MiscListLogic.ALL))
        assertEquals("온도계", MiscListLogic.title(MiscType.THERMOMETER))
        assertEquals("예: 아카이아 펄", MiscListLogic.placeholder(MiscType.SCALE))
        assertEquals("2025.3.1부터 사용", MiscListLogic.sinceLabel("2025-03-01"))
        assertNull(MiscListLogic.sinceLabel(""))
        assertNull(MiscListLogic.sinceLabel("not-a-date"))
        // web hides "+ 추가" on 전체 (miscBackup-9): no type, no button
        assertNull(MiscListLogic.formType(MiscListLogic.ALL))
        assertEquals(MiscType.WATER, MiscListLogic.formType(MiscType.WATER))
        assertEquals(listOf("all", "dripper", "filter", "kettle", "thermometer", "scale", "water"), MiscListLogic.typeTabs)
    }
}
