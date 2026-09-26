package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryDisplayTest {
    private val created = Dates.toMillis(LocalDate(2026, 7, 15), 8, 30)

    @Test fun finalEvaluationIsSplitOut() {
        val s = EntryDisplay.splitFinalEvaluation("오늘 메모\n\n## Final Evaluation:\n산미 좋음")
        assertTrue(s.hasFinal)
        assertEquals("오늘 메모", s.before)
        assertEquals("산미 좋음", s.final)
        val ko = EntryDisplay.splitFinalEvaluation("앞\n최종 평가\n")
        assertTrue(ko.hasFinal)
        assertEquals("", ko.final)
        val none = EntryDisplay.splitFinalEvaluation("그냥 메모")
        assertFalse(none.hasFinal)
        assertEquals("그냥 메모", none.before)
    }

    @Test fun beanInfoLinesFallBackToParensAndSiblings() {
        val en = Entry(id = "a", createdAt = created, name = "예가체프 (리브레, 노르딕)")
        val sib = Entry(id = "b", createdAt = created, name = "예가체프", farmProducer = "아리차(코케)", washingStation = "아리차")
        val lines = EntryDisplay.beanInfoLines(en, listOf(sib))
        assertEquals(listOf("로스터리" to "리브레", "생두 수입사" to "노르딕", "농장" to "아리차(코케)", "워싱 스테이션" to "아리차"), lines.map { it.label to it.value })
        assertTrue(EntryDisplay.beanInfoLines(Entry(id = "c", createdAt = created, name = "x"), emptyList()).isEmpty())
    }

    @Test fun subtitleAndRows() {
        val en = Entry(id = "a", createdAt = created, category = Category.CAFE, name = "케냐", cafeName = "OO카페", dripper = "V60", price = "6500", dose = "15", roastDate = "7. 11", moisture = "11.3")
        val sib = Entry(id = "b", createdAt = created, name = "케냐", region = "Nyeri")
        assertEquals("2026.07.15 · 카페 · OO카페 · Nyeri · V60", EntryDisplay.subtitle(en, listOf(sib)))
        val rows = EntryDisplay.infoRows(en)
        assertEquals("카페" to "OO카페", rows[0])
        assertEquals("한 잔 가격" to "6,500원", rows[1])
        assertTrue(rows.contains("수분율" to "11.3%"))
        assertTrue(rows.contains("비율" to "15g : ?g"))
        assertTrue(rows.contains("로스팅 날짜" to "2026. 7. 11"))
        assertNull(EntryDisplay.scaTotalText(en))
        assertEquals("82.50 / 100", EntryDisplay.scaTotalText(en.copy(attributes = mapOf("flavor" to 8.0, "uniformity" to 10.0, "cleancup" to 10.0, "sweetness" to 10.0, "acidity" to 8.0, "body" to 8.0, "balance" to 8.0, "overall" to 8.0, "aftertaste" to 8.0, "fragranceAroma" to 4.5))))
        assertEquals(FormMode.CAFE, EntryDisplay.formModeFor(Category.CAFE))
        assertEquals(FormMode.EXTRACT, EntryDisplay.formModeFor(""))
        assertEquals("케냐 (7/15)", EntryDisplay.defaultRecipeName(en))
    }

    // form-2 / flows-2: the three default 10s are not a score (design §2.3.3)
    @Test fun scaBlockOnlyForScoredOrAnnotatedRecords() {
        val defaults = com.coffeejournal.domain.rules.ScaScoring.defaultAttributes()
        assertFalse(EntryDisplay.showsScaBlock(defaults, emptyMap()))
        assertFalse(EntryDisplay.showsScaBlock(emptyMap(), mapOf("flavor" to "  ")))
        assertTrue(EntryDisplay.showsScaBlock(defaults + ("flavor" to 8.0), emptyMap()))
        assertTrue(EntryDisplay.showsScaBlock(defaults + ("acidityIntensity" to 3.0), emptyMap()), "an intensity alone keeps the block")
        assertTrue(EntryDisplay.showsScaBlock(defaults, mapOf("acidity" to "밝다")), "a memo alone keeps the block")
        assertNull(EntryDisplay.scaTotalText(Entry(id = "u", createdAt = created, attributes = defaults)))
    }

    // NaN from a backup file must not crash the header score
    @Test fun nonFiniteAttributesAreIgnored() {
        val en = Entry(id = "n", createdAt = created, attributes = mapOf("flavor" to 8.0, "uniformity" to Double.NaN, "body" to Double.POSITIVE_INFINITY))
        assertEquals("8.00 / 100", EntryDisplay.scaTotalText(en))
    }

    // form-12
    @Test fun deleteConfirmationNamesTheRecord() {
        assertEquals(
            "“케냐 AA” 기록을 정말 삭제할까요?\n\n삭제한 기록은 복구할 수 없어요. 봉투 사진도 함께 지워져요.",
            EntryDisplay.deleteConfirmText(Entry(id = "a", createdAt = created, name = "케냐 AA")),
        )
        assertTrue(EntryDisplay.deleteConfirmText(Entry(id = "b", createdAt = created, category = Category.CAFE, cafeName = "OO카페")).startsWith("“OO카페” 기록을"))
        assertTrue(EntryDisplay.deleteConfirmText(Entry(id = "c", createdAt = created, cuppingPlace = "FELT")).startsWith("“FELT” 기록을"))
        assertTrue(EntryDisplay.deleteConfirmText(Entry(id = "d", createdAt = created)).startsWith("기록을 정말 삭제할까요?"))
    }

    // form-13
    @Test fun recipeSavedMessageSaysWhereToFindIt() {
        assertEquals(
            "\"아침 레시피\" 이름으로 내 레시피에 저장했어요. 새 기록의 \"⭐ 내 레시피\" 버튼에서 다시 꺼내 쓰실 수 있어요.",
            EntryDisplay.recipeSavedText("아침 레시피"),
        )
    }

    // data-5: the detail's best key is the home group key (each component of a custom blend)
    @Test fun bestKeysFollowTheHomeGroups() {
        val single = Entry(id = "s", createdAt = created, name = "벤사 (리브레)")
        assertEquals(listOf("벤사"), bestKeysOf(single))
        val blend = Entry(
            id = "b", createdAt = created, name = "아침 블렌드", beanMode = com.coffeejournal.domain.model.BeanMode.CUSTOM_BLEND,
            blendComponents = listOf(com.coffeejournal.domain.model.BlendComponent("벤사", "10"), com.coffeejournal.domain.model.BlendComponent("게이샤 (프릳츠)", "8")),
        )
        assertEquals(listOf("벤사", "게이샤"), bestKeysOf(blend))
        assertEquals(com.coffeejournal.ui.extract.ExtractGrouping.groupKeys(blend), bestKeysOf(blend))
        assertTrue(bestKeysOf(Entry(id = "c", createdAt = created, category = Category.CAFE, name = "벤사")).isEmpty())
        assertTrue(bestKeysOf(Entry(id = "e", createdAt = created, name = "")).isEmpty())
    }
}
