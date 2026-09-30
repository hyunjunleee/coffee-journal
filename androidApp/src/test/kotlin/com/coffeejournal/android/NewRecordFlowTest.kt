package com.coffeejournal.android

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.extract.NewRecordTexts
import com.coffeejournal.ui.form.AgainTexts
import com.coffeejournal.ui.form.RecordDraftTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home tab's "+ 새 기록 추가" is the way to every kind of record (NewRecordScreen), and a café record's
 * "같은 커피 다시 기록" records the same coffee again as a new visit.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NewRecordFlowTest : CoverageFlowBase() {

    private val home = hasText("+ 새 기록 추가")
    private val chooser = hasText(NewRecordTexts.BREW_HINT)

    /** The chooser's [choice] opens the screen showing [expected]; its back returns home, the chooser gone. */
    private fun opens(choice: String, expected: String, substring: Boolean = false) {
        openNewRecord(choice)
        waitUntil("'$choice' → '$expected'") { has(hasText(expected, substring)) && !has(chooser) && !has(home) }
        back()
        waitUntil("home again after '$choice'") { has(home) && !has(chooser) }
    }

    @Test
    fun chooser_offersEveryKindOfRecord_eachOpensItsOwnForm() {
        launchApp()
        opens(NewRecordTexts.BREW, "레시피로 시작", substring = true)
        opens(NewRecordTexts.CAFE, "카페 이름")
        opens(NewRecordTexts.CUPPING, "홈커핑")
        opens(NewRecordTexts.PANTRY, "원두 보관함")
        opens(NewRecordTexts.BLEND, "블렌드 기록 추가")
        opens(NewRecordTexts.FARM, "농장(생산자) 추가")
        opens(NewRecordTexts.SELECTION, "생두 수입사 추가")
        opens(NewRecordTexts.BOOK, "책 추가")
        opens(NewRecordTexts.VIDEO, "동영상 추가")
        opens(NewRecordTexts.CLASS, "클래스 추가")
        opens("드리퍼", "드리퍼 추가")
        opens("물", "물 추가")
        opens(NewRecordTexts.CAFE_PLACE, "카페 추가")
        opens(NewRecordTexts.ROASTERY, "로스터리 추가")
        // nothing recorded yet: no coffee to record again
        clickText("+ 새 기록 추가")
        waitForText(NewRecordTexts.COFFEE)
        assertFalse(has(hasText(NewRecordTexts.AGAIN)))
    }

    @Test
    fun aCafeRecord_fromTheChooser_savesAndLeavesHomeUnderItsDetail() {
        launchApp()
        openNewRecord(NewRecordTexts.CAFE)
        typeInto("예: OO카페 (서울 성수동)", "모모스 영도")
        typeInto("예: 콜롬비아 라 플라타 게이샤 워시드", "에티오피아 구지 함벨라")
        saveForm("에티오피아 구지 함벨라")
        val saved = entries().single()
        assertEquals(Category.CAFE, saved.category)
        assertEquals("모모스 영도", saved.cafeName)
        back()
        waitUntil("home under the new record") { has(home) && !has(chooser) }
    }

    @Test
    fun cafeDetail_sameCoffeeAgain_isASecondVisit_withoutTheFirstVisitsTasting() {
        SampleData.seed()
        val first = entry("e4")!!
        launchApp()
        openViaSearch("세하도")
        waitForText("FELT 청계천")
        clickText(AgainTexts.BUTTON)
        waitFor(hasTestTag("again-banner"))
        waitForText(AgainTexts.banner("2026.09.18 · FELT 청계천", withRecipe = true))
        assertTrue("café name filled", has(field("FELT 청계천")))
        assertTrue("bean name filled", has(field("브라질 세하도 내추럴")))
        assertTrue("price filled", has(field("6,500")))

        saveForm("브라질 세하도 내추럴")
        val visits = entries().filter { it.isCafe && it.cafeName == "FELT 청계천" }.sortedBy { it.createdAt }
        assertEquals(2, visits.size)
        val again = visits.last()
        assertNotEquals(first.id, again.id)
        assertEquals("the first visit is left as it was", first, entry("e4"))
        assertEquals("dated today", Dates.today(), Dates.toLocalDate(again.createdAt))
        assertEquals(first.name, again.name)
        assertEquals("6500", again.price)
        assertEquals(first.country, again.country)
        assertEquals(first.region, again.region)
        assertEquals(first.process, again.process)
        assertEquals(first.dripper, again.dripper)
        assertEquals("", again.actualNotes)
        assertEquals("", again.notes)
        assertTrue(again.bagPhotos.isEmpty())
    }

    @Test
    fun chooser_offersTheLastCafeCoffees_andAgainKeepsItsOwnDraft() {
        SampleData.seed()
        launchApp()
        clickText("+ 새 기록 추가")
        waitForText(NewRecordTexts.AGAIN)
        waitForText("FELT 청계천 · 2026.09.18")
        clickText("브라질 세하도 내추럴")
        waitFor(hasTestTag("again-banner"))

        // what was typed there stays with that coffee: leaving keeps it, the same coffee again brings it back
        typeInto("맛, 개선할 점, 다음에 시도할 것 등", "두 번째가 더 달았다")
        back()
        clickText(RecordDraftTexts.LEAVE)
        waitUntil("home") { has(home) && !has(chooser) }
        clickText("+ 새 기록 추가")
        clickText("브라질 세하도 내추럴")
        waitFor(hasTestTag("draft-banner"))
        assertTrue(has(field("두 번째가 더 달았다")))
        // a plain new café record is not that coffee's draft
        back()
        clickText(RecordDraftTexts.LEAVE)
        waitUntil("home") { has(home) }
        openNewRecord(NewRecordTexts.CAFE)
        waitForText("카페 이름")
        assertFalse(has(hasTestTag("draft-banner")))
        assertFalse(has(hasTestTag("again-banner")))
    }
}
