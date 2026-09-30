package com.coffeejournal.android

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.ui.extract.NewRecordTexts
import com.coffeejournal.ui.form.FormFold
import com.coffeejournal.ui.form.RecordDraftTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Most of the record form folds (FormFold): 원두 상세 정보, 봉투 사진, 레시피, SCA 커핑 평가, 노트, 메모, and a cupping
 * bean's 원두 정보 and 노트. What is folded stays so for the next form of that kind, and folding changes no value.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FormFoldFlowTest : CoverageFlowBase() {

    private val home = hasText("+ 새 기록 추가")
    private val scoreSheet = hasText("CVA")
    private val recipe = hasText("분쇄도")
    private val origin = hasText("이 원두의 예상 노트 (원두 봉투에 적힌 것)")
    private val memo = hasText("추출 관련 메모")

    private fun fold(part: String) = clickNode(hasTestTag("fold-$part"))

    private fun backHome() {
        back()
        waitUntil("home") { has(home) }
    }

    @Test
    fun theCuppingScore_folds_andStaysFoldedForTheNextBrew_butNotForACafe() {
        launchApp()
        openNewRecord()
        waitFor(scoreSheet)
        fold(FormFold.SCORE)
        waitGone(scoreSheet)
        assertTrue("the rest stays open", has(recipe) && has(memo))
        // folding is no input: back leaves without asking
        backHome()
        openNewRecord()
        waitFor(recipe)
        assertFalse("folded in the next brew form too", has(scoreSheet))
        backHome()
        openNewRecord(NewRecordTexts.CAFE)
        waitForText("카페 이름")
        assertTrue("a café record folds its own parts", has(scoreSheet))
    }

    @Test
    fun foldAll_hidesEveryPart_savesEveryValue_andUnfoldAllBringsThemBack() {
        SampleData.seed()
        val before = entry("e1")!!
        launchApp()
        openViaSearch("워카", index = 1)
        clickText("수정")
        waitForText("기록 수정")
        clickNode(hasTestTag("fold-all"))
        waitGone(scoreSheet)
        listOf(recipe, origin, memo, hasText("내가 느낀 노트 — 실제로 맛본 것")).forEach { assertFalse(it.description, has(it)) }
        // folded, a part says what it holds
        assertTrue(has(hasText("자스민", substring = true)))
        assertTrue(has(hasTestTag("unfold-all")) && !has(hasTestTag("fold-all")))
        clickText("수정 저장")
        waitUntil("back on detail") { !has(hasText("기록 수정")) && has(button("수정")) }
        assertEquals("folded parts keep their values", before, entry("e1"))

        clickText("수정")
        waitForText("기록 수정")
        assertFalse("the next edit opens folded", has(recipe))
        clickNode(hasTestTag("unfold-all"))
        waitFor(recipe)
        waitFor(scoreSheet)
        waitFor(origin)
        assertTrue(has(memo))
    }

    @Test
    fun aCuppingBeans_infoAndNotes_foldForEveryBeanCard() {
        launchApp()
        openNewRecord(NewRecordTexts.CUPPING)
        waitFor(field("국가"))
        fold(FormFold.CUPPING_BEAN_INFO)
        waitGone(field("국가"))
        fold(FormFold.CUPPING_BEAN_NOTES)
        waitGone(hasText("예상 노트"))
        assertTrue("the bean's name, evaluation and memo stay", has(field("원두 이름 (예: 에티오피아 예가체프)")) && has(hasText("항목별 평가 (선택)")))
        // the evaluation sheet starts folded, and the first tap unfolds it
        assertFalse(has(hasText("SCA 2004")))
        fold(FormFold.CUPPING_EVALUATION)
        waitForText("SCA 2004")
        fold(FormFold.CUPPING_EVALUATION)
        waitGone(hasText("SCA 2004"))
        clickText("+ 원두 추가")
        waitUntil("a second bean card") { count(field("원두 이름 (예: 에티오피아 예가체프)")) == 2 }
        assertFalse("a new card folds the same", has(field("국가")))
        // the second card is input: leaving asks, and this one is not kept
        back()
        clickText(RecordDraftTexts.DISCARD)
        waitUntil("home") { has(home) }
        openNewRecord(NewRecordTexts.CUPPING)
        waitFor(field("원두 이름 (예: 에티오피아 예가체프)"))
        assertFalse(has(field("국가")))
        clickNode(hasTestTag("unfold-all"))
        waitFor(field("국가"))
        waitForText("예상 노트")
    }
}
