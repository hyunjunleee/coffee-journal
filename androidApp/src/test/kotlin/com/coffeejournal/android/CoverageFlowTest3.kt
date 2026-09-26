package com.coffeejournal.android

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.reference.RoadmapDefaults
import com.coffeejournal.domain.rules.Dates
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Critic gap #7, calendar and 기타 part: month navigation and 오늘, the category / cupping-type / 월별·전체 filters,
 * the roadmap (phase, items, current-phase D-day indicator), 커핑 리뷰 모음 and the equipment 보유/궁금함 sections
 * with 지정순/최신순. Web references: script3.js 1028-1045 and 1741-1760 (calendar), 8031-8200 (reviews, roadmap),
 * 5355-5420 (renderMiscList).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class CoverageFlowTest3 : CoverageFlowBase() {

    private val today: LocalDate get() = Dates.today()
    private fun monthLabel(d: LocalDate) = "${d.year}년 ${d.month.number}월"
    private fun prevMonth(d: LocalDate) = if (d.month.number == 1) LocalDate(d.year - 1, 12, 1) else LocalDate(d.year, d.month.number - 1, 1)
    private fun nextMonth(d: LocalDate) = if (d.month.number == 12) LocalDate(d.year + 1, 1, 1) else LocalDate(d.year, d.month.number + 1, 1)
    private fun at(d: LocalDate, day: Int, hour: Int = 10) = Dates.toMillis(LocalDate(d.year, d.month, day), hour, 0)
    private fun dayCell(day: Int) = hasText(day.toString()) and hasClickAction()
    private fun row(name: String) = hasText(name) and hasClickAction()
    private val anchor = hasText("토") // the weekday header of the calendar list

    private fun openCalendar() {
        tab("tab-calendar")
        waitForText(monthLabel(today))
    }

    // ───────────────────────── web 1741-1743: ‹ › 오늘 ─────────────────────────

    @Test
    fun cov20_calendarMonthNavigation_prevNextYearWrapAndToday() {
        val prev = prevMonth(today)
        runBlocking { koinGet<EntryRepository>().upsert(Entry(id = "pm", createdAt = at(prev, 12), name = "지난달 원두 테스트", dose = "15")) }
        launchApp()
        openCalendar()
        clickNode(hasContentDescription("이전 달") and hasClickAction())
        waitForText(monthLabel(prev))
        clickNode(dayCell(12))
        waitUntil("last month's record opens directly (one record, no blend)") { onDetailOf("지난달 원두 테스트") }
        back()
        waitForText(monthLabel(prev))

        clickNode(hasContentDescription("다음 달") and hasClickAction())
        clickNode(hasContentDescription("다음 달") and hasClickAction())
        waitForText(monthLabel(nextMonth(today)))
        assertFalse("nothing recorded next month", has(dayCell(12)))

        // thirteen months back from next month crosses the year boundary
        repeat(13) { clickNode(hasContentDescription("이전 달") and hasClickAction()) }
        waitForText(monthLabel(LocalDate(today.year - 1, today.month, 1)))
        clickText("오늘")
        waitForText(monthLabel(today))
    }

    // ───────────────────────── web 1028-1045 / 1745-1760: filters ─────────────────────────

    private fun seedFilterRecords() = runBlocking {
        val repo = koinGet<EntryRepository>()
        val m = today
        val p = prevMonth(today)
        repo.upsert(Entry(id = "b1", createdAt = at(m, 3), name = "필터 원두 기록", dose = "15"))
        repo.upsert(Entry(id = "c1", createdAt = at(m, 7), category = Category.CAFE, name = "필터 카페 원두", cafeName = "필터 카페"))
        repo.upsert(Entry(id = "k1", createdAt = at(m, 11), category = Category.CUPPING, cuppingType = CuppingType.PUBLIC, name = "필터 퍼블릭 커핑", cuppingPlace = "필터 퍼블릭 커핑", cuppingBeans = listOf(CuppingBean(name = "퍼블릭 원두"))))
        repo.upsert(Entry(id = "k2", createdAt = at(m, 15), category = Category.CUPPING, cuppingType = CuppingType.HOME, name = "필터 홈커핑 원두", cuppingBeans = listOf(CuppingBean(name = "필터 홈커핑 원두"))))
        repo.upsert(Entry(id = "k3", createdAt = at(m, 19), category = Category.CUPPING, cuppingType = CuppingType.CLASS, name = "필터 수업 커핑", cuppingPlace = "필터 수업 커핑", cuppingBeans = listOf(CuppingBean(name = "수업 원두"))))
        repo.upsert(Entry(id = "c0", createdAt = at(p, 20), category = Category.CAFE, name = "지난달 카페 원두", cafeName = "지난달 카페"))
        repo.upsert(Entry(id = "k0", createdAt = at(p, 20, 15), category = Category.CUPPING, cuppingType = CuppingType.CLASS, name = "지난달 수업 커핑", cuppingPlace = "지난달 수업 커핑", cuppingBeans = listOf(CuppingBean(name = "지난달 원두"))))
    }

    private fun assertCells(what: String, marked: List<Int>, blank: List<Int>) {
        marked.forEach { d -> waitFor(dayCell(d), "$what: day $d marked") }
        blank.forEach { d -> assertFalse("$what: day $d has nothing", has(dayCell(d))) }
    }

    private fun listShows(what: String, shown: List<String>, hidden: List<String>) {
        shown.forEach { n ->
            scrollListTo(anchor, row(n))
            assertTrue("$what lists '$n'", has(row(n)))
        }
        hidden.forEach { n -> assertFalse("$what does not list '$n'", has(row(n))) }
    }

    @Test
    fun cov21_calendarCategoryCuppingTypeAndScopeFilters() {
        seedFilterRecords()
        launchApp()
        openCalendar()
        val label = monthLabel(today)
        assertCells("전체", listOf(3, 7, 11, 15, 19), emptyList())
        scrollListTo(anchor, hasText("커피 공부"))

        clickText("커핑")
        assertCells("커핑", listOf(11, 15, 19), listOf(3, 7))
        scrollListTo(anchor, hasText("$label 커핑 기록"))
        assertTrue("퍼블릭 is the default cupping type", isSelected(button("퍼블릭")))
        listShows("퍼블릭 / 월별", listOf("필터 퍼블릭 커핑"), listOf("필터 홈커핑 원두", "필터 수업 커핑", "필터 카페 원두"))
        clickText("홈커핑")
        listShows("홈커핑 / 월별", listOf("필터 홈커핑 원두"), listOf("필터 퍼블릭 커핑", "필터 수업 커핑"))
        clickText("수업")
        listShows("수업 / 월별", listOf("필터 수업 커핑"), listOf("지난달 수업 커핑", "필터 퍼블릭 커핑"))
        clickText("전체 보기")
        waitForText("전체 커핑 기록")
        listShows("수업 / 전체", listOf("필터 수업 커핑", "지난달 수업 커핑"), listOf("필터 홈커핑 원두"))
        assertTopToBottom("newest first", row("필터 수업 커핑"), row("지난달 수업 커핑"))
        clickText("월별 보기")
        waitForText("$label 커핑 기록")

        clickText("카페")
        assertCells("카페", listOf(7), listOf(3, 11, 15, 19))
        scrollListTo(anchor, hasText("$label 카페 기록"))
        assertFalse("no cupping type chips for 카페", has(button("홈커핑")))
        listShows("카페 / 월별", listOf("필터 카페 원두"), listOf("지난달 카페 원두", "필터 퍼블릭 커핑"))
        clickText("전체 보기")
        waitForText("전체 카페 기록")
        listShows("카페 / 전체", listOf("필터 카페 원두", "지난달 카페 원두"), emptyList())

        clickText("원두")
        assertCells("원두", listOf(3), listOf(7, 11, 15, 19))
        scrollListTo(anchor, hasText("$label 추출 기록 · 최신순"))
        listShows("원두", listOf("필터 원두 기록"), listOf("필터 카페 원두"))

        clickText("전체")
        assertCells("전체 again", listOf(3, 7, 11, 15, 19), emptyList())
    }

    // ───────────────────────── web 8086-8200: roadmap ─────────────────────────

    private fun checkboxOf(itemText: String) = node(hasText(itemText) and hasClickAction() and !hasSetTextAction()).onParent().let { parent ->
        val kids = parent.fetchSemanticsNode().children
        val idx = kids.indexOfFirst { k -> k.config.getOrNull(SemanticsProperties.Text)?.any { it.text == itemText } == true }
        parent.onChildAt(idx - 1)
    }

    private fun addItem(text: String) {
        val input = field("새 항목 추가 후 Enter")
        scrollListTo(anchor, input)
        node(input).performScrollTo()
        node(input).performTextInput(text)
        settle(1)
        node(field(text)).performImeAction()
        settle()
    }

    private fun indicator(title: String) = hasText("지금은 \"$title\" 단계예요")

    @Test
    fun cov22_roadmap_addPhase_itemsCheckEditDelete_currentPhaseFollowsTheDday() {
        runBlocking { koinGet<SettingsRepository>().setDdayStart(Dates.plusDays(today, -40)) }
        launchApp()
        openCalendar()
        scrollListTo(anchor, hasText("D-41"))
        waitFor(indicator(RoadmapDefaults.STARTER_TITLE), "starter phase is current on D-41")

        scrollListTo(anchor, button("+ 단계 추가"))
        clickText("+ 단계 추가")
        typeInto("예: 추출 기초 다지기", "추출 기초 테스트 단계")
        typeInto("예: 1~3개월", "한 달")
        replaceIn("41", "30")
        replaceIn("71", "60")
        clickText("저장")
        scrollListTo(anchor, indicator("추출 기초 테스트 단계"))
        assertTrue("📍 marks the current phase", has(hasText("📍 추출 기초 테스트 단계")))
        assertFalse(has(hasText("📍 ${RoadmapDefaults.STARTER_TITLE}")))
        val phase = roadmap().single { it.id != RoadmapDefaults.STARTER_ID }
        assertEquals(listOf("추출 기초 테스트 단계", "한 달", "30", "60"), listOf(phase.title, phase.range, phase.dayStart.toString(), phase.dayEnd.toString()))

        // the new phase is open: add two items, check one, edit the other inline, delete it
        addItem("커핑 노트 10개")
        addItem("추출 일지 20잔")
        scrollListTo(anchor, hasText("0/2"))
        checkboxOf("추출 일지 20잔").performClickSettled()
        scrollListTo(anchor, hasText("1/2"))
        assertEquals(listOf(false, true), roadmap().single { it.id == phase.id }.items.map { it.done })

        clickNode(hasText("커핑 노트 10개") and hasClickAction() and !hasSetTextAction())
        replaceIn("커핑 노트 10개", "커핑 노트 12개")
        clickText("확인")
        waitUntil("inline edit saved") { roadmap().single { it.id == phase.id }.items.first().text == "커핑 노트 12개" }
        waitFor(hasText("커핑 노트 12개") and hasClickAction() and !hasSetTextAction())

        clickNode(button("✕"), 0)
        waitForText("\"커핑 노트 12개\" 항목을 삭제할까요?")
        clickNode(dialogButton("삭제"))
        scrollListTo(anchor, hasText("1/1"))
        assertEquals(listOf("추출 일지 20잔"), roadmap().single { it.id == phase.id }.items.map { it.text })

        // the indicator follows the D-day: past the phase's end the starter phase is current again
        runBlocking { koinGet<SettingsRepository>().setDdayStart(Dates.plusDays(today, -69)) }
        scrollListTo(anchor, hasText("D-70"))
        waitFor(indicator(RoadmapDefaults.STARTER_TITLE), "starter current on D-70")
        assertTrue(has(hasText("📍 ${RoadmapDefaults.STARTER_TITLE}")))
        runBlocking { koinGet<SettingsRepository>().setDdayStart(Dates.plusDays(today, -29)) }
        scrollListTo(anchor, hasText("D-30"))
        waitFor(indicator("추출 기초 테스트 단계"), "the phase starts on its first day (dayStart inclusive)")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickSettled() {
        runCatching { performScrollTo() }
        performClick()
        settle()
    }

    /**
     * Critic gap: CalendarViewModel.updatePhase reads the whole phase and upserts it from a separate coroutine per tap,
     * so two checks made before the first write lands overwrite each other.
     */
    @Test
    fun cov23_roadmap_twoQuickChecks_bothAreSaved() {
        runBlocking {
            val repo = koinGet<RoadmapRepository>()
            repo.ensureSeeded()
            repo.upsert(repo.getAll().single().copy(items = listOf(RoadmapItem("i1", "빠른 체크 하나"), RoadmapItem("i2", "빠른 체크 둘"))))
        }
        launchApp()
        openCalendar()
        val header = hasText(RoadmapDefaults.STARTER_TITLE) and hasClickAction()
        scrollListTo(anchor, header)
        clickNode(header)
        scrollListTo(anchor, hasText("빠른 체크 둘") and hasClickAction())
        val first = checkboxOf("빠른 체크 하나").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val second = checkboxOf("빠른 체크 둘").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnUiThread { first(); second() } // two taps inside one database round trip
        settle(6)
        waitUntil("both checks stored, but the phase holds ${roadmap().single().items.map { it.text to it.done }}", timeoutMs = 3_000) {
            roadmap().single().items.all { it.done }
        }
        scrollListTo(anchor, hasText("2/2"))
    }

    // ───────────────────────── web 8031-8070: 커핑 리뷰 모음 ─────────────────────────

    @Test
    fun cov24_cuppingReviews_emptyStateListOrderExcerptAndNavigation() {
        runBlocking { koinGet<EntryRepository>().upsert(Entry(id = "nr", createdAt = at(today, 1), category = Category.CUPPING, name = "리뷰 없는 커핑", cuppingPlace = "리뷰 없는 커핑")) }
        launchApp()
        openCalendar()
        val reviewsTab = button("커핑 리뷰 모음")
        scrollListTo(anchor, reviewsTab)
        clickNode(reviewsTab)
        scrollListTo(anchor, hasText("전체 리뷰가 적힌 커핑 기록이 아직 없어요.\n커핑의 전체 경험을 기록하면 이곳에 연결돼요."))

        val older = LocalDate(2026, 8, 20)
        val newer = LocalDate(2026, 9, 2)
        runBlocking {
            val repo = koinGet<EntryRepository>()
            repo.upsert(Entry(id = "rv1", createdAt = Dates.toMillis(older, 14, 0), category = Category.CUPPING, name = "리뷰 퍼블릭 커핑", cuppingPlace = "리뷰 퍼블릭 커핑",
                notes = "첫째 줄\n\n둘째   줄   " + "가".repeat(120), cuppingBeans = listOf(CuppingBean(name = "리뷰 케냐"), CuppingBean(name = "리뷰 에티오피아"))))
            repo.upsert(Entry(id = "rv2", createdAt = Dates.toMillis(newer, 14, 0), category = Category.CUPPING, cuppingType = CuppingType.HOME, name = "리뷰 홈커핑 원두",
                notes = "짧은 리뷰", cuppingBeans = listOf(CuppingBean(name = "리뷰 홈커핑 원두"))))
        }
        val card1 = row("리뷰 퍼블릭 커핑")
        val card2 = row("리뷰 홈커핑 원두")
        waitFor(card1, "reviews listed once the records arrive")
        scrollListTo(anchor, card1)
        assertTopToBottom("newest first", card2, card1)
        assertFalse("a cupping without an overall review is not listed", has(row("리뷰 없는 커핑")))
        val node1 = node(card1).fetchSemanticsNode()
        val texts = node1.config[SemanticsProperties.Text].map { it.text }
        assertTrue(texts.toString(), texts.contains("2026년 8월 20일"))
        assertTrue("whitespace collapsed and cut at 115 characters: $texts", texts.contains("첫째 줄 둘째 줄 " + "가".repeat(105) + "…"))
        assertTrue(texts.containsAll(listOf("리뷰 케냐", "리뷰 에티오피아", "전체 커핑 리뷰에서 보기 →")))

        clickNode(card1)
        waitUntil("the cupping's detail") { onDetailOf("리뷰 퍼블릭 커핑") }
        back()
        scrollListTo(anchor, card1)
        assertTrue("still on 커핑 리뷰 모음", isSelected(reviewsTab))
    }

    // ───────────────────────── web 5355-5420: 기타 tab sections and sort ─────────────────────────

    private fun equipment(id: String, type: String, name: String, created: Long, status: String = MiscStatus.OWNED) =
        MiscItem(id = id, type = type, name = name, status = status, createdAt = created)

    private fun nonClickable(text: String): SemanticsMatcher = hasText(text) and !hasClickAction()

    @Test
    fun cov25_equipment_ownedCuriousSections_andFixedOrRecentSort() {
        val t = 1_780_000_000_000L
        runBlocking {
            koinGet<MiscRepository>().upsertAll(listOf(
                equipment("d1", MiscType.DRIPPER, "테스트 드리퍼 보유 A", t + 1),
                equipment("d2", MiscType.DRIPPER, "테스트 드리퍼 궁금", t + 2, status = MiscStatus.CURIOUS),
                equipment("d3", MiscType.DRIPPER, "테스트 드리퍼 보유 B", t + 3, status = ""),
                equipment("k1", MiscType.KETTLE, "테스트 케틀", t + 4),
                MiscItem(id = "s1", type = MiscType.SOURCE, name = "기타에 안 보이는 로스터리", scope = Scope.DOMESTIC, createdAt = t + 5),
            ))
        }
        launchApp()
        tab("tab-misc")
        waitForText("전체 장비")
        // 지정순 (default): grouped in the fixed type order, newest first inside a group, every card with its type badge
        assertTrue(isSelected(button("지정순")))
        assertTopToBottom(
            "지정순", nonClickable("드리퍼"), nonClickable("테스트 드리퍼 보유 B"), nonClickable("테스트 드리퍼 궁금"),
            nonClickable("테스트 드리퍼 보유 A"), nonClickable("케틀"), nonClickable("테스트 케틀"),
        )
        assertEquals("group label + 3 badges", 4, count(nonClickable("드리퍼")))
        assertFalse("roasteries live in the 원두 tab", has(hasText("기타에 안 보이는 로스터리")))

        clickText("최신순")
        waitUntil("flat list without group labels") { count(nonClickable("드리퍼")) == 3 }
        assertTopToBottom("최신순", nonClickable("테스트 케틀"), nonClickable("테스트 드리퍼 보유 B"), nonClickable("테스트 드리퍼 궁금"), nonClickable("테스트 드리퍼 보유 A"))

        clickText("드리퍼")
        waitForText("보유 드리퍼")
        assertFalse("the sort toggle is only on 전체", has(button("최신순")))
        assertTopToBottom("보유 / 궁금한", hasText("보유 드리퍼"), nonClickable("테스트 드리퍼 보유 B"), nonClickable("테스트 드리퍼 보유 A"), hasText("궁금한 드리퍼"), nonClickable("테스트 드리퍼 궁금"))
        assertEquals("only the section title, no type badges on a type tab", 1, count(nonClickable("드리퍼")))

        clickText("케틀")
        waitForText("보유 케틀")
        waitForText("궁금한 케틀을(를) 추가해보세요.")
        assertTrue(has(nonClickable("테스트 케틀")))

        clickText("필터")
        waitForText("아직 등록한 필터이(가) 없어요.")
        assertFalse("no sections for a type with nothing registered", has(hasText("보유 필터")))
    }
}
