package com.coffeejournal.ui.bean.b

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.domain.rules.Dates
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlendSourcesTest {
    private val day = 86_400_000L
    private val blend = Blend(id = "b1", name = "", date = "2026-03-01", beans = listOf(BlendComponent("A", "30"), BlendComponent("B", "20")), createdAt = 1)
    private val entries = listOf(
        Entry(id = "custom", createdAt = Dates.startOfDayMillis(LocalDate(2026, 3, 5)), name = "내 블렌드", beanMode = BeanMode.CUSTOM_BLEND, blendComponents = listOf(BlendComponent("A", "10"))),
        Entry(id = "shop", createdAt = Dates.startOfDayMillis(LocalDate(2026, 2, 1)), name = "하우스 블렌드", beanMode = BeanMode.COMMERCIAL_BLEND, roastery = "듀잇"),
        Entry(id = "cup", createdAt = Dates.startOfDayMillis(LocalDate(2026, 4, 1)), category = Category.CUPPING, cuppingBeans = listOf(
            CuppingBean(name = "커핑 블렌드", beanMode = BeanMode.BLEND, blendComponentsText = "브라질 + 콜롬비아"), CuppingBean(name = "싱글", beanMode = BeanMode.SINGLE),
        )),
        Entry(id = "single", createdAt = 99 * day, name = "싱글 오리진"),
    )

    @Test fun mergesAllSourcesNewestFirst() {
        val all = BlendSources.merge(listOf(blend), entries)
        assertEquals(listOf("cupping", "custom", "custom", "commercial"), all.map { it.kind })
        assertEquals("custom", (all[1] as BlendItem.FromEntry).entry.id)
        assertEquals("b1", (all[2] as BlendItem.Custom).blend.id)
        assertEquals(2, BlendSources.merge(listOf(blend), entries, BlendSources.CUSTOM).size)
        assertEquals(1, BlendSources.merge(listOf(blend), entries, BlendSources.CUPPING).size)
        assertEquals("커핑 블렌드", (BlendSources.merge(emptyList(), entries, BlendSources.CUPPING).single() as BlendItem.FromCupping).bean.name)
        assertTrue(BlendSources.merge(emptyList(), emptyList()).isEmpty())
    }

    @Test fun componentLinesAndTitles() {
        assertEquals(listOf("A 30g (60%)", "B 20g (40%)"), BlendSources.componentLines(blend.beans))
        assertEquals(listOf("A", "B"), BlendSources.componentLines(listOf(BlendComponent("A"), BlendComponent("B"))))
        assertEquals("A + B", BlendSources.customTitle(blend))
        assertEquals("이름 없는 블렌드", BlendSources.customTitle(blend.copy(beans = emptyList())))
        assertEquals("내 이름", BlendSources.customTitle(blend.copy(name = "내 이름")))
        assertEquals("2026.3.1", BlendSources.customDate(blend))
        assertEquals(listOf(BlendComponent("A", "1")), BlendSources.validRows(listOf(BlendComponent(" A ", " 1 "), BlendComponent("  ", "5"))))
    }

    @Test fun sharesAddUpTo100_andACafeBlendCardListsItsBeans() {
        assertEquals(listOf("A 1g (34%)", "B 1g (33%)", "C 1g (33%)"), BlendSources.componentLines(listOf(BlendComponent("A", "1"), BlendComponent("B", "1"), BlendComponent("C", "1"))))
        assertEquals(listOf("A 10g (100%)", "B (0%)"), BlendSources.componentLines(listOf(BlendComponent("A", "10"), BlendComponent("B"))))
        val shop = entries[1].copy(country = "브라질", blendComponents = listOf(BlendComponent(percent = "70"), BlendComponent(percent = "30", country = "에티오피아")))
        assertEquals(listOf("브라질 70%", "에티오피아 30%"), BlendSources.cafeBlendLines(shop))
        assertTrue(BlendSources.cafeBlendLines(entries[1]).isEmpty(), "beans never entered: the roastery line only")
    }

    @Test fun recentBeanNamesAreDistinctAndNewestFirst() {
        val names = BlendSources.recentBeanNames(BeanRecords.flatten(entries + Entry(id = "dup", createdAt = 100 * day, name = "싱글 오리진 (리브레)")))
        assertEquals(listOf("커핑 블렌드", "싱글", "내 블렌드", "하우스 블렌드", "싱글 오리진"), names)
    }
}
