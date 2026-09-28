package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.BlendBeans
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The record form's café blend: a bean-info block per bean, with shares. */
class BlendBeansFormTest {
    private val now = Dates.toMillis(LocalDate(2026, 9, 25), 12, 0)

    private fun entryOf(state: FormState, existing: Entry? = null) = FormMapper.toEntry(state, "id1", existing, emptyList(), now)

    private fun threeBeans(): FormState {
        var s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(
            name = "하우스 블렌드", roastery = "프릳츠", roast = "미디엄", roastDate = "2026. 9. 1", country = "브라질", region = "Cerrado",
            process = "내추럴", firstBeanPercent = "50",
        )
        s = FormMapper.addBlendBean(FormMapper.addBlendBean(s))
        s = s.withBean(1, s.bean(1).copy(country = "에티오피아", region = "Yirgacheffe", process = "허니", processSub = "화이트", percent = "30.0"))
        s = s.withBean(2, s.bean(2).copy(country = "콜롬비아", roastery = "리브레", roast = "라이트", process = "기타", processOther = "카보닉", percent = "20"))
        return s
    }

    @Test fun addingABlockMakesACafeBlend_removingBackToOneMakesItASingleBeanAgain() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now)
        assertEquals(BeanMode.SINGLE, s.effectiveBeanMode)
        assertEquals(1, s.beanCount)
        val two = FormMapper.addBlendBean(s.copy(firstBeanPercent = ""))
        assertEquals(BeanMode.COMMERCIAL_BLEND, two.beanMode)
        assertEquals(BeanMode.COMMERCIAL_BLEND, two.effectiveBeanMode)
        assertEquals(2, two.beanCount)
        assertEquals(listOf(BeanForm()), two.blendBeans)
        val three = FormMapper.addBlendBean(two).let { it.withBean(2, it.bean(2).copy(country = "케냐")) }
        assertEquals(listOf("케냐"), FormMapper.removeBlendBean(three, 1).blendBeans.map { it.country }, "the later blocks move up")
        val back = FormMapper.removeBlendBean(FormMapper.removeBlendBean(three.copy(firstBeanPercent = "50"), 2), 1)
        assertEquals(BeanMode.SINGLE, back.beanMode)
        assertEquals(1, back.beanCount)
        assertEquals("", back.firstBeanPercent, "a single bean has no share")
        assertEquals(back, FormMapper.removeBlendBean(back, 1), "bean 1 is never removed")
        // 직접 블렌드 keeps its grams rows; the café blend's blocks wait hidden and are not saved
        val custom = three.copy(beanMode = BeanMode.CUSTOM_BLEND, blendRows = listOf(BlendRowForm("A", "10"), BlendRowForm("B", "5")))
        assertEquals(1, custom.beanCount)
        assertEquals(listOf("A", "B"), entryOf(custom).blendComponents.map { it.name })
    }

    @Test fun threeBeans_saveEveryBlock_bean1InTheRecordsFields_andComeBackTheSame() {
        val s = threeBeans()
        assertEquals(100.0, FormMapper.blendPercentSum(s))
        val en = entryOf(s)
        assertEquals(BeanMode.COMMERCIAL_BLEND, en.beanMode)
        assertEquals(listOf("브라질", "Cerrado", "내추럴", "프릳츠", "미디엄"), listOf(en.country, en.region, en.process, en.roastery, en.roast))
        assertEquals(
            listOf(
                BlendComponent(percent = "50", roastery = "프릳츠", country = "브라질", region = "Cerrado", process = "내추럴", roast = "미디엄", roastDate = "2026. 9. 1"),
                // an empty 로스터리 / 로스팅 is saved empty: the same as bean 1
                BlendComponent(percent = "30", country = "에티오피아", region = "Yirgacheffe", process = "허니(화이트)"),
                BlendComponent(percent = "20", roastery = "리브레", country = "콜롬비아", process = "기타", processOther = "카보닉", roast = "라이트"),
            ),
            en.blendComponents,
        )
        val back = FormMapper.fromEntry(en, FormMode.EXTRACT)
        assertEquals(3, back.beanCount)
        assertEquals((0 until 3).map { s.bean(it).copy(percent = s.bean(it).percent.removeSuffix(".0")) }, (0 until 3).map { back.bean(it) })
        assertTrue(back.blendRows.isEmpty(), "no custom rows made up from the café blend's beans")
        assertEquals(en.blendComponents, entryOf(back.copy(createdAt = en.createdAt)).blendComponents)
    }

    @Test fun emptyExtraBlocksAreNotSaved_andAnOlderCafeBlendWithoutBeansOpensWithAnEmptySecondBlock() {
        val s = FormMapper.addBlendBean(FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "하우스", country = "브라질"))
        val en = entryOf(s)
        assertEquals(BeanMode.COMMERCIAL_BLEND, en.beanMode, "still a blend: the user added a bean")
        assertTrue(en.blendComponents.isEmpty(), "nothing to add to the record's own fields")
        // bean 1's share alone is kept
        assertEquals(listOf("60"), entryOf(s.copy(firstBeanPercent = "60")).blendComponents.map { it.percent })
        // a café blend saved by the web or an older app: its fields are bean 1
        val older = Entry(id = "o", createdAt = 1, name = "하우스", beanMode = BeanMode.COMMERCIAL_BLEND, country = "브라질", roast = "다크")
        val form = FormMapper.fromEntry(older, FormMode.EXTRACT)
        assertEquals(2, form.beanCount)
        assertEquals(listOf(BeanForm()), form.blendBeans)
        assertEquals("다크", form.bean(0).roast)
        val saved = entryOf(form, older)
        assertEquals(BeanMode.COMMERCIAL_BLEND, saved.beanMode)
        assertTrue(saved.blendComponents.isEmpty())
    }

    @Test fun repeatBean_aCafeBlendComesBackWithAllItsBlocks() {
        val first = entryOf(threeBeans()).copy(id = "first", createdAt = 1_000)
        val later = Entry(id = "later", createdAt = 2_000, name = "하우스 블렌드", beanMode = BeanMode.SINGLE)
        val fresh = FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "하우스 블렌드")
        val s = FormMapper.autofill(fresh, listOf(later, first))
        assertTrue(s.repeatBean)
        assertEquals(BeanMode.COMMERCIAL_BLEND, s.effectiveBeanMode)
        assertEquals(3, s.beanCount)
        assertEquals(listOf("브라질", "에티오피아", "콜롬비아"), (0 until 3).map { s.bean(it).country })
        assertEquals(listOf("50", "30", "20"), (0 until 3).map { s.bean(it).percent })
        assertEquals("", s.bean(1).roastery, "still inherited")
        // blocks the user already filled stay, and a 직접 블렌드 is left alone
        val own = FormMapper.addBlendBean(fresh).let { it.withBean(1, it.bean(1).copy(country = "케냐")) }
        assertEquals(listOf("케냐"), FormMapper.autofill(own, listOf(first)).blendBeans.map { it.country })
        val custom = fresh.copy(beanMode = BeanMode.CUSTOM_BLEND)
        assertTrue(FormMapper.autofill(custom, listOf(first)).blendBeans.isEmpty())
        // a café blend first recorded without its beans still makes the repeat a café blend
        val flagOnly = first.copy(blendComponents = emptyList())
        assertEquals(listOf(BeanForm()), FormMapper.autofill(fresh, listOf(flagOnly)).blendBeans)
        assertTrue(FormMapper.autofill(fresh, listOf(later)).blendBeans.isEmpty(), "a single bean stays single")
    }

    @Test fun cafeBlendBlocksWorkOnACafeRecordToo() {
        val cafe = FormMapper.addBlendBean(FormMapper.newState(FormMode.CAFE, null, now).copy(name = "에스프레소 블렌드", cafeName = "OO카페"))
            .let { it.withBean(1, it.bean(1).copy(country = "과테말라", percent = "30")) }
        val en = entryOf(cafe)
        assertEquals(Category.CAFE, en.category)
        assertEquals(BeanMode.COMMERCIAL_BLEND, en.beanMode)
        assertEquals(listOf("", "과테말라"), en.blendComponents.map { it.country })
    }

    @Test fun detail_eachBeanOfACafeBlendIsItsOwnGroup_withItsShare() {
        val en = entryOf(threeBeans()).copy(createdAt = Dates.toMillis(LocalDate(2026, 9, 20), 9, 0), price = "18000", arrival = "2026.8")
        val rows = EntryDisplay.infoRows(en).map { it.first }
        assertEquals(listOf("원두 가격", "입고 시기"), rows.filter { it !in setOf("비율", "물 온도", "분쇄도", "사용한 물", "총 시간", "필터", "예상 노트", "내가 느낀 노트") })
        assertFalse(rows.any { it in setOf("가공", "로스팅", "로스팅 날짜", "품종") }, "the beans' own facts are in their groups")
        assertEquals("원두 1 · 브라질 Cerrado · 50%", EntryDisplay.blendBeanTitle(0, BlendBeans.beans(en)[0]))
        val beans = BlendBeans.beans(en)
        assertEquals(listOf("원두 2 · 에티오피아 Yirgacheffe · 30%", "원두 3 · 콜롬비아 · 20%"), (1..2).map { EntryDisplay.blendBeanTitle(it, beans[it]) })
        assertEquals("원두 2", EntryDisplay.blendBeanTitle(1, BlendComponent()))
        assertEquals(
            listOf("로스터리" to "프릳츠", "국가" to "에티오피아", "지역" to "Yirgacheffe", "가공" to "허니(화이트)", "로스팅" to "미디엄", "로스팅 날짜" to "2026. 9. 1"),
            EntryDisplay.blendBeanRows(beans[1], en.createdAt),
            "bean 2 shows bean 1's roastery and roasting",
        )
        assertEquals("기타 (카보닉)", EntryDisplay.blendBeanRows(beans[2], en.createdAt).toMap()["가공"])
        // the head keeps a roastery only when every bean has it, and no region of one bean
        assertTrue(EntryDisplay.beanInfoLines(en, emptyList()).isEmpty(), "bean 3 is from 리브레")
        val sameRoastery = en.copy(blendComponents = en.blendComponents.mapIndexed { i, c -> if (i == 2) c.copy(roastery = "") else c })
        assertEquals(listOf(EntryDisplay.InfoLine("로스터리", "프릳츠")), EntryDisplay.beanInfoLines(sameRoastery, emptyList()))
        assertFalse("Cerrado" in EntryDisplay.subtitle(en, emptyList()))
        // a single bean's detail is as before
        val single = en.copy(beanMode = BeanMode.SINGLE, blendComponents = emptyList())
        assertTrue(EntryDisplay.infoRows(single).map { it.first }.containsAll(listOf("가공", "로스팅", "로스팅 날짜")))
        assertTrue("Cerrado" in EntryDisplay.subtitle(single, emptyList()))
    }

    @Test fun percentSum_onlyOnceTwoBeansAreThere_andNotBlocking() {
        val s = threeBeans()
        assertNull(FormMapper.blendPercentSum(FormMapper.newState(FormMode.EXTRACT, null, now)))
        val short = s.withBean(2, s.bean(2).copy(percent = ""))
        assertEquals(80.0, FormMapper.blendPercentSum(short))
        assertNull(FormMapper.validate(short), "a sum other than 100 never blocks saving")
    }
}
