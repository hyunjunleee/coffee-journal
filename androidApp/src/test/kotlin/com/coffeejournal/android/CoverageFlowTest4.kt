package com.coffeejournal.android

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Critic gap #7, the remaining home flows of its list: picking, re-picking and clearing the best recipe of a bean,
 * deleting a 총정리 by saving it empty, and the pantry's 등록순 / 예상 피크 빠른 순 toggle.
 * Web references: script3.js 874-925 (bean group), 605-624 (summary save), 5986-6000 (pantry sort).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class CoverageFlowTest4 : CoverageFlowBase() {

    private val homeAnchor = hasText("+ 새 기록 추가")

    private fun openGroup(name: String) {
        val header = hasText(name) and hasClickAction()
        scrollListTo(homeAnchor, header)
        clickNode(header)
    }

    private fun best(key: String): String? = runBlocking { koinGet<BeanMetaRepository>().getBest().singleOrNull { it.beanKey == key }?.entryId }

    // ───────────── web 905-925: ⭐ 베스트 레시피 고르기 / 다시 고르기 / 해제 ─────────────

    @Test
    fun cov30_bestRecipe_pickWithFiveRecords_repick_thenClearFromTheDetail() {
        val bean = "베스트 고르기 테스트 원두"
        val day0 = LocalDate(2026, 8, 1)
        val days = (1..5).map { Dates.plusDays(day0, it) }
        runBlocking {
            days.forEachIndexed { i, d ->
                koinGet<EntryRepository>().upsert(Entry(id = "r${i + 1}", createdAt = Dates.toMillis(d, 9, 0), name = bean, dose = "15", water = "240", temp = "${91 + i}", dripper = "V60"))
            }
        }
        val key = BeanNames.coreBeanName(bean)
        fun pickerRow(i: Int) = hasText("${Dates.isoDate(days[i])} · 15g · 240g · ${91 + i}°C · V60") and hasClickAction()
        launchApp()
        waitForText("5 entries")
        openGroup(bean)
        val pick = button("⭐ 베스트 레시피 고르기")
        scrollListTo(homeAnchor, pick) // web: offered from 5 recipe records on, while nothing is picked
        clickNode(pick)
        scrollListTo(homeAnchor, hasText("이 원두로 마셨던 것 중 가장 좋았던 걸 골라주세요"))
        scrollListTo(homeAnchor, pickerRow(2))
        clickNode(pickerRow(2))
        waitUntil("best stored") { best(key) == "r3" }
        scrollListTo(homeAnchor, hasText("⭐ 이 원두의 베스트 레시피"))
        waitForText("${Dates.isoDate(days[2])} 기록 · 15g · 240g · 93°C · V60")
        assertFalse("the pick button goes once a best is set", has(pick))
        assertFalse("the picker closes", has(hasText("이 원두로 마셨던 것 중 가장 좋았던 걸 골라주세요")))

        clickNode(hasText("다시 고르기") and hasClickAction())
        scrollListTo(homeAnchor, pickerRow(4))
        clickNode(pickerRow(4))
        waitUntil("best re-picked") { best(key) == "r5" }
        waitForText("${Dates.isoDate(days[4])} 기록 · 15g · 240g · 95°C · V60")

        // clearing it on the record's detail brings the pick button back
        val row = hasText("15g · 240g · 95°C · V60") and hasClickAction()
        scrollListTo(homeAnchor, row)
        clickNode(row)
        waitUntil("detail") { has(button("⭐ 베스트 레시피 해제")) }
        clickText("⭐ 베스트 레시피 해제")
        waitFor(button("이 원두의 베스트 레시피로 지정"))
        assertNull(best(key))
        back()
        scrollListTo(homeAnchor, pick)
        assertFalse(has(hasText("⭐ 이 원두의 베스트 레시피")))
    }

    // ───────────── web 605-624: saving an empty 총정리 deletes it ─────────────

    @Test
    fun cov31_beanSummary_editThenDeleteBySavingItEmpty() {
        SampleData.seed()
        val bean = "에티오피아 예가체프 워카 첼베사"
        val old = "92도보다 91도가 안정적. 2차 푸어를 천천히."
        launchApp()
        waitForText("5 entries")
        openGroup(bean)
        scrollListTo(homeAnchor, hasText(old))
        assertTrue(has(hasText("☕ 이 원두 총정리")))
        clickText("✏️ 내용 고치기")
        replaceIn(old, "91도 고정.")
        clickText("저장")
        scrollListTo(homeAnchor, hasText("91도 고정."))
        val key = BeanNames.coreBeanName(bean)
        assertEquals("91도 고정.", runBlocking { koinGet<BeanMetaRepository>().getSummaries().single { it.beanKey == key }.text })

        clickText("✏️ 내용 고치기")
        replaceIn("91도 고정.", "")
        clickText("저장")
        scrollListTo(homeAnchor, button("✏️ 직접 정리하기"))
        assertFalse("the summary card is gone", has(hasText("☕ 이 원두 총정리")))
        assertTrue(runBlocking { koinGet<BeanMetaRepository>().getSummaries().none { it.beanKey == key } })
    }

    // ───────────── web 5986-6000: 등록순 / 예상 피크 빠른 순 ─────────────

    @Test
    fun cov32_pantrySortToggle_registeredOrPeak() {
        val t = Dates.nowMillis()
        runBlocking {
            koinGet<PantryRepository>().upsertAll(listOf(
                PantryItem(id = "pd", name = "미디엄 로스팅 봉투", roastLevel = "미디엄", roastDate = "2026-09-20", createdAt = t),
                PantryItem(id = "pa", name = "라이트 로스팅 봉투", roastLevel = "라이트", roastDate = "2026-09-20", createdAt = t + 1),
                PantryItem(id = "pb", name = "날짜 없는 봉투", createdAt = t + 2),
                PantryItem(id = "pc", name = "피크 직접 입력 봉투", peakStart = "2026-09-01", peakEnd = "2026-09-30", createdAt = t + 3),
            ))
        }
        launchApp()
        clickText("원두 보관함")
        waitForText("원두 보관함 · 4봉")
        fun card(name: String) = hasText(name) and !hasClickAction()
        assertTrue("등록순 is the default", isSelected(button("등록순")))
        assertTopToBottom("newest first", card("피크 직접 입력 봉투"), card("날짜 없는 봉투"), card("라이트 로스팅 봉투"), card("미디엄 로스팅 봉투"))
        clickText("예상 피크 빠른 순")
        // manual peak 9/1, medium 9/20 + 7, light 9/20 + 14, then the bag without any date
        assertTopToBottom("earliest peak first", card("피크 직접 입력 봉투"), card("미디엄 로스팅 봉투"), card("라이트 로스팅 봉투"), card("날짜 없는 봉투"))
        clickText("등록순")
        assertTopToBottom("back to newest first", card("피크 직접 입력 봉투"), card("날짜 없는 봉투"), card("라이트 로스팅 봉투"), card("미디엄 로스팅 봉투"))
    }
}
