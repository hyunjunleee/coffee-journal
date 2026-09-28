package com.coffeejournal.domain

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.domain.rules.BlendBeans
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A café blend's beans (bean 1 in the record's fields, later beans inheriting 로스터리 / 로스팅) and blend shares. */
class BlendBeansTest {
    private val house = Entry(
        id = "h", createdAt = 1_000, name = "하우스 블렌드 (프릳츠)", beanMode = BeanMode.COMMERCIAL_BLEND,
        roastery = "프릳츠", roast = "미디엄", roastDate = "2026-09-01", country = "브라질", region = "Cerrado", variety = "Mundo Novo",
        process = "내추럴", expectedNotes = "초콜릿", actualNotes = "견과",
        blendComponents = listOf(
            // bean 1's copy: the record's own fields win over it, only its share is read
            BlendComponent(percent = "60", country = "옛 값", roastery = "프릳츠"),
            BlendComponent(percent = "40", country = "에티오피아", region = "Yirgacheffe", variety = "Heirloom", process = "워시드"),
            BlendComponent(country = "콜롬비아", roastery = "다른 로스터리", roast = "라이트"),
        ),
    )

    @Test fun beans_bean1IsTheRecordsFields_laterBeansTakeRoasteryAndRoastFromBean1UnlessTheirOwn() {
        val beans = BlendBeans.beans(house)
        assertEquals(3, beans.size)
        assertEquals(listOf("브라질", "에티오피아", "콜롬비아"), beans.map { it.country })
        assertEquals(listOf("60", "40", ""), beans.map { it.percent })
        assertEquals(listOf("프릳츠", "프릳츠", "다른 로스터리"), beans.map { it.roastery })
        assertEquals(listOf("미디엄", "미디엄", "라이트"), beans.map { it.roast })
        assertEquals(listOf("2026-09-01", "2026-09-01", "2026-09-01"), beans.map { it.roastDate })
        assertEquals("Heirloom", beans[1].variety, "fields that are not inherited stay the bean's own")
        assertEquals("", beans[2].variety)
        assertTrue(BlendBeans.hasBeans(house))
    }

    @Test fun beans_aRecordWithoutBlendBeansIsItsOneBean() {
        val single = Entry(id = "s", createdAt = 1, name = "벤사", country = "에티오피아", roast = "라이트")
        assertEquals(listOf(BlendBeans.firstBean(single)), BlendBeans.beans(single))
        assertEquals("에티오피아", BlendBeans.beans(single).single().country)
        // a café blend recorded before its beans could be entered, and a custom blend (its components are the user's beans)
        val older = house.copy(blendComponents = emptyList())
        assertEquals(1, BlendBeans.beans(older).size)
        assertFalse(BlendBeans.hasBeans(older))
        assertTrue(BlendBeans.isCafeBlend(older))
        val custom = house.copy(beanMode = BeanMode.CUSTOM_BLEND, blendComponents = listOf(BlendComponent("A", "10"), BlendComponent("B", "5")))
        assertEquals(1, BlendBeans.beans(custom).size)
        assertFalse(BlendBeans.hasBeans(custom))
        assertFalse(BlendBeans.isCafeBlend(house.copy(category = Category.CUPPING)))
    }

    @Test fun resolve_emptyMeansBean1s_ownValueWins() {
        val first = BlendComponent(roastery = "A", roast = "라이트", roastDate = "2026-09-01", country = "브라질")
        assertEquals(BlendComponent(roastery = "A", roast = "라이트", roastDate = "2026-09-01"), BlendBeans.resolve(first, BlendComponent()))
        val own = BlendComponent(roastery = "B", roast = "다크", roastDate = "2026-08-01")
        assertEquals(own, BlendBeans.resolve(first, own))
        assertEquals("", BlendBeans.resolve(first, BlendComponent()).country, "the origin is never inherited")
    }

    @Test fun percentSum_andWhetherItIsAWholeBlend() {
        assertNull(BlendBeans.percentSum(listOf("", " ")))
        assertEquals(100.0, BlendBeans.percentSum(listOf("60", "40", "")))
        assertEquals(90.0, BlendBeans.percentSum(listOf("60", "30", "NaN")))
        assertTrue(BlendBeans.isWhole(100.0))
        assertTrue(BlendBeans.isWhole(99.9), "three thirds typed as 33.3")
        assertFalse(BlendBeans.isWhole(90.0))
        assertFalse(BlendBeans.isWhole(100.5))
        assertEquals("99.9", BlendBeans.formatPercent(33.3 * 3))
        assertEquals("100", BlendBeans.formatPercent(100.0))
        assertEquals("60%", BlendBeans.percentText("60.0"))
        assertNull(BlendBeans.percentText(""))
    }

    @Test fun sharesFromGrams_wholePercentsThatAddUpTo100() {
        assertEquals(listOf(67, 33), BlendBeans.sharesFromGrams(listOf("10", "5")))
        assertEquals(listOf(34, 33, 33), BlendBeans.sharesFromGrams(listOf("1", "1", "1")), "the earlier row takes the spare point")
        assertEquals(listOf(60, 40), BlendBeans.sharesFromGrams(listOf("12", "8")))
        assertEquals(listOf(17, 50, 33), BlendBeans.sharesFromGrams(listOf("1", "3", "2")))
        assertEquals(listOf(null, null, 100), BlendBeans.sharesFromGrams(listOf("", "0", "7.5")))
        assertEquals(listOf(null, null), BlendBeans.sharesFromGrams(listOf("", "abc")))
        assertEquals(listOf(null, 100), BlendBeans.sharesFromGrams(listOf("NaN", "5")))
        listOf(listOf("7", "7", "7", "7", "7", "7", "7"), listOf("0.1", "99.9"), listOf("3", "3", "4")).forEach { g ->
            assertEquals(100, BlendBeans.sharesFromGrams(g).sumOf { it ?: 0 }, "$g")
        }
    }

    @Test fun labelsAndSummary() {
        val beans = BlendBeans.beans(house)
        assertEquals("브라질 Cerrado", BlendBeans.label(beans[0], 0))
        assertEquals("콜롬비아", BlendBeans.label(beans[2], 2))
        assertEquals("농장 A", BlendBeans.label(BlendComponent(farmProducer = "농장 A"), 1))
        assertEquals("원두 2", BlendBeans.label(BlendComponent(), 1))
        assertEquals("브라질 Cerrado 60% · 에티오피아 Yirgacheffe 40% · 콜롬비아", BlendBeans.summary(beans))
    }

    @Test fun originRecords_countEveryBeanOfACafeBlend_withoutCountingTheRecordsNotesTwice() {
        val single = Entry(id = "s", createdAt = 2, name = "벤사", country = "에티오피아", actualNotes = "자스민")
        assertEquals(2, BeanRecords.flatten(listOf(house, single)).size, "one row per record by default")
        val rows = BeanRecords.flatten(listOf(house, single), blendBeans = true)
        assertEquals(listOf("브라질", "에티오피아", "콜롬비아", "에티오피아"), rows.map { it.country })
        assertEquals(listOf(null, "h", "h", null), rows.map { it.parentEntryId })
        assertEquals(listOf("h", "h", "h", "s"), rows.map { it.entryId })
        assertEquals(listOf("견과", "", "", "자스민"), rows.map { it.actualNotes })
        assertEquals(listOf("미디엄", "미디엄", "라이트", ""), rows.map { it.roast })
        assertTrue(rows.take(3).all { it.name == house.name && it.beanMode == BeanMode.COMMERCIAL_BLEND })
        assertEquals(BeanRecords.flatten(listOf(single)), BeanRecords.flatten(listOf(single), blendBeans = true))
    }
}
