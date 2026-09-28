package com.coffeejournal.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A café blend entered as one bean-info block per bean ("+ 원두 추가 (블렌드)", shares, bean 1's roastery and roast
 * in grey on the later beans), the shares of a custom blend, and the café record's folded recipe — through the real app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class BlendBeansFlowTest : CoverageFlowBase() {
    private val sumTag = hasTestTag("blend-percent-sum")
    private val inheritedRoast = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "원두 1과 같음")

    /** Bean [i]'s share field, in its block head. */
    private fun percentField(i: Int) = hasSetTextAction() and hasAnyAncestor(hasTestTag("bean-block-$i"))

    /** Text inside bean [i]'s group of the detail. */
    private fun inBeanGroup(i: Int, text: String) = hasText(text) and hasAnyAncestor(hasTestTag("blend-bean-$i"))

    @Test
    fun cafeBlend_plusAddsAnIdenticalBlock_sharesAndInheritedRoastery_savedAndShownPerBean_xBackToSingle() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "하우스 블렌드 테스트")
        typeInto("예: 커피정경", "프릳츠 테스트")
        typeInto("브라질", "콜롬비아")
        clickNode(button("미디엄"))
        assertFalse("one bean: no share asked", has(field("%")))
        tap(button("+ 원두 추가 (블렌드)"))
        waitFor(hasTestTag("bean-block-1"))
        // the same fields again, and the segment says what the record now is
        assertTrue(has(button("카페 블렌드")) && !has(button("단일 원두")))
        assertEquals(2, count(field("Cerrado")))
        assertEquals("bean 2 shows bean 1's roastery in grey", 2, count(field("프릳츠 테스트")))
        assertTrue("and its roast", has(button("미디엄") and inheritedRoast))
        node(percentField(0)).performTextInput("60")
        settle(1)
        node(percentField(1)).performTextInput("30")
        waitFor(sumTag and hasText("합계 90%"))
        waitForText("비율을 더하면 100%가 아니에요. 모르는 비율이 있으면 그대로 저장해도 괜찮아요.")
        node(percentField(1)).performTextReplacement("40")
        waitFor(sumTag and hasText("합계 100%"))
        waitGone(hasText("비율을 더하면 100%가 아니에요. 모르는 비율이 있으면 그대로 저장해도 괜찮아요."))
        typeInto("브라질", "에티오피아")
        typeInto("Cerrado", "Yirgacheffe", index = 1)
        clickNode(button("워시드"), 1)

        saveForm("하우스 블렌드 테스트")
        val saved = entries().single()
        assertEquals(BeanMode.COMMERCIAL_BLEND, saved.beanMode)
        assertEquals(listOf("콜롬비아", "프릳츠 테스트", "미디엄"), listOf(saved.country, saved.roastery, saved.roast))
        assertEquals(
            listOf(
                BlendComponent(percent = "60", roastery = "프릳츠 테스트", country = "콜롬비아", roast = "미디엄"),
                BlendComponent(percent = "40", country = "에티오피아", region = "Yirgacheffe", process = "워시드"),
            ),
            saved.blendComponents,
        )
        // the detail: each bean its own group with its share; bean 2 has bean 1's roastery and roast
        waitFor(inBeanGroup(0, "원두 1 · 콜롬비아 · 60%"))
        waitFor(inBeanGroup(1, "원두 2 · 에티오피아 Yirgacheffe · 40%"))
        assertTrue(has(inBeanGroup(1, "프릳츠 테스트")) && has(inBeanGroup(1, "미디엄")) && has(inBeanGroup(1, "워시드")))
        assertFalse("bean 1 is not washed", has(inBeanGroup(0, "워시드")))

        // editing: both blocks are back; ✕ on bean 2 makes it a single bean again
        clickText("수정")
        waitForText("기록 수정")
        waitFor(hasTestTag("bean-block-1"))
        assertTrue(has(field("에티오피아")) && has(percentField(0) and hasText("60")) && has(percentField(1) and hasText("40")))
        tap(hasContentDescription("원두 2 삭제") and hasClickAction())
        waitGone(hasTestTag("bean-block-1"))
        assertTrue(has(button("단일 원두")))
        assertFalse("one bean: no share asked", has(field("%")))
        saveForm("하우스 블렌드 테스트", label = "수정 저장")
        val single = entries().single()
        assertEquals(BeanMode.SINGLE, single.beanMode)
        assertTrue(single.blendComponents.isEmpty())
        assertFalse(has(hasText("원두 1 · 콜롬비아", substring = true)))
    }

    @Test
    fun customBlend_showsEachRowsShareOfTheWeight() {
        launchApp()
        openNewForm()
        clickText("직접 블렌드")
        assertFalse("a custom blend's beans are its rows", has(button("+ 원두 추가 (블렌드)")))
        typeInto("원두 선택", "재료 A 테스트")
        typeInto("그램(g)", "10")
        assertTrue(has(hasTestTag("blend-share-0") and hasText("100%")))
        typeInto("원두 선택", "재료 B 테스트")
        typeInto("그램(g)", "5")
        waitFor(hasTestTag("blend-share-0") and hasText("67%"))
        waitFor(hasTestTag("blend-share-1") and hasText("33%"))
    }

    @Test
    fun cafeRecord_recipeFoldedAway_opensWhenTold_andSavedOnlyThen_homeBrewShowsItDirectly() {
        launchApp()
        // a home brew shows the recipe straight away
        openNewForm()
        waitFor(field("칼리타 웨이브"))
        assertFalse(has(hasText("레시피 입력 (카페에서 알려준 경우)")))
        back()

        tab("tab-calendar")
        clickText("카페")
        clickText("+ 카페 기록 추가")
        waitForText("새 기록")
        typeInto(namePlaceholder, "카페 레시피 없음")
        waitFor(hasText("레시피 입력 (카페에서 알려준 경우)"))
        assertFalse("folded away", has(field("칼리타 웨이브")))
        saveForm("카페 레시피 없음")
        val plain = entries().single()
        assertEquals(Category.CAFE, plain.category)
        assertEquals(listOf("", "", "", "", ""), listOf(plain.dripper, plain.filter, plain.dose, plain.water, plain.time))
        assertTrue(plain.steps.isEmpty())
        back()

        clickText("+ 카페 기록 추가")
        waitForText("새 기록")
        typeInto(namePlaceholder, "카페 레시피 있음")
        tap(hasText("레시피 입력 (카페에서 알려준 경우)") and hasClickAction())
        typeInto("칼리타 웨이브", "V60")
        typeInto("20", "15")
        typeInto("320", "250")
        // folding it again keeps what was typed, and it is saved
        tap(hasText("레시피 입력 (카페에서 알려준 경우)") and hasClickAction())
        waitGone(field("V60"))
        tap(hasText("레시피 입력 (카페에서 알려준 경우)") and hasClickAction())
        waitFor(field("V60"))
        saveForm("카페 레시피 있음")
        val told = entries().single { it.name == "카페 레시피 있음" }
        assertEquals(listOf("V60", "15", "250"), listOf(told.dripper, told.dose, told.water))
        waitForText("15g : 250g")
        // editing it: the recipe is unfolded
        clickText("수정")
        waitForText("기록 수정")
        waitFor(field("V60"))
    }
}
