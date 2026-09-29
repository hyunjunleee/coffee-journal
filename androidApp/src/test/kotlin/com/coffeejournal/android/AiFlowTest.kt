package com.coffeejournal.android

import android.app.Application
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.ui.ai.AiErrors
import com.coffeejournal.ui.ai.AiKeySlot
import com.coffeejournal.ui.ai.AiPrefs
import com.coffeejournal.ui.ai.AiProvider
import com.coffeejournal.ui.ai.AiTexts
import com.coffeejournal.ui.ai.AskStage
import com.coffeejournal.ui.ai.NoteHelperService
import com.coffeejournal.ui.ai.SearchDepth
import com.coffeejournal.ui.ai.StageStatus
import com.coffeejournal.ui.guide.GuideTexts
import com.coffeejournal.ui.guide.KeyHowTos
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.nav.appGraph
import com.coffeejournal.ui.platform.installUrlOpener
import com.coffeejournal.ui.theme.CoffeeJournalTheme
import com.coffeejournal.ui.theme.Display
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.Motion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AI 노트 도우미 (not on the web): 설정's AI section, the answer screen from 노트 상세 and from the record form, and
 * what the user sees when a key is missing or a service says no. The services are faked ([FakeAiHttp], synthetic
 * replies in [AiReplies]); the keys live in an in-memory store (Robolectric has no Android Keystore).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class AiFlowTest : CoverageFlowBase() {
    private val http get() = AiSetup.http
    private val secrets get() = AiSetup.secrets
    private val prefs get() = koinGet<AiPrefs>()
    private val chipPlaceholder = "노트 추가 후 Enter (예: 오렌지)"

    @Before
    fun installOpener() = installUrlOpener(ApplicationProvider.getApplicationContext<Application>())

    private fun lastOpenedUrl(): String? {
        val intent = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity ?: return null
        assertEquals(Intent.ACTION_VIEW, intent.action)
        return intent.dataString
    }

    private fun openSettings() {
        tap(hasTestTag("open-settings"))
        waitFor(hasTestTag("ai-settings"))
        waitForText(AiTexts.INTRO)
    }

    /** 원두 › 커피 노트 › 내가 느낀 노트 › "자스민 N" → 노트 상세. */
    private fun openJasmineNote() {
        tab("tab-bean")
        clickText("커피 노트")
        clickText("내가 느낀 노트")
        val chip = SemanticsMatcher("note chip '자스민 N'") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any { Regex("^자스민 \\d+$").matches(it.text) } == true
        } and hasClickAction()
        scrollListTo(hasText("플레이버 휠"), chip)
        clickNode(chip)
        waitForText("“자스민”와 함께 기록된 노트 조합")
    }

    /** The answer screen on its own (for the error states). */
    private fun openHelper(route: Route.NoteHelper) {
        compose.setContent {
            CoffeeJournalTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = route) { appGraph(nav) }
            }
        }
        settle()
    }

    private fun paragraphs(): List<AnnotatedString> = compose.onAllNodes(hasTestTag("answer-paragraph"), useUnmergedTree = true)
        .fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].single() }

    private fun colorOf(p: AnnotatedString, part: String): Color? {
        val at = p.text.indexOf(part)
        return p.spanStyles.lastOrNull { it.start <= at && at < it.end && it.item.color != Color.Unspecified }?.item?.color
    }

    private fun requestBody(i: Int): String = http.requests[i].body!!

    // ───────────────────────── 설정 › AI 노트 도우미 ─────────────────────────

    @Test
    fun settings_optionsKeysMaskedAndChecked_guideLinks_modelPresets_keptOffTheBackup() {
        launchApp()
        openSettings()
        // Gemini 무료 + Tavily by default: its description and both key fields
        assertTrue(isSelected(button(AiProvider.GEMINI_TAVILY.label)))
        assertEquals(4, AiProvider.entries.count { has(button(it.label)) })
        assertTrue(has(hasText(AiProvider.GEMINI_TAVILY.summary)))
        assertTrue(has(field(AiKeySlot.GEMINI.placeholder)) && has(field(AiKeySlot.TAVILY.placeholder)))

        // 키 받는 법: each key's how-to opens full screen over 설정, with its pages as links, and closes back to it
        assertFalse(has(hasTestTag("key-guide")))
        tap(button(GuideTexts.open(AiKeySlot.GEMINI.label)))
        waitFor(hasTestTag("key-guide") and hasAnyDescendant(hasText(KeyHowTos.GEMINI.title)))
        assertTrue(has(hasText(KeyHowTos.GEMINI.intro)))
        assertTrue(KeyHowTos.GEMINI.sections.all { has(hasText(it.heading)) })
        val page = KeyHowTos.GEMINI.sections.flatMap { it.items }.first { it.links.isNotEmpty() }.links.first()
        tap(button("${page.first} ↗"))
        assertEquals(page.second, lastOpenedUrl())
        tap(hasTestTag("key-guide-close"))
        waitGone(hasTestTag("key-guide"))
        tap(button(GuideTexts.open(AiKeySlot.TAVILY.label)))
        waitFor(hasTestTag("key-guide") and hasAnyDescendant(hasText(KeyHowTos.TAVILY.title)))
        tap(hasTestTag("key-guide-close"))
        waitGone(hasTestTag("key-guide"))
        waitFor(field(AiKeySlot.GEMINI.placeholder))

        // a pasted key is saved and then shown only by its last four characters
        typeInto(AiKeySlot.GEMINI.placeholder, " AQ.test-gemini-a1b2\n")
        tap(button(AiTexts.SAVE))
        waitFor(hasText("저장됨 …a1b2"))
        assertEquals("AQ.test-gemini-a1b2", secrets.values[NoteHelperService.secretName(AiKeySlot.GEMINI)])
        assertFalse(has(hasText("AQ.test-gemini-a1b2", substring = true)))
        typeInto(AiKeySlot.TAVILY.placeholder, "tvly-wrong-9z9z")
        tap(button(AiTexts.SAVE))
        waitFor(hasText("저장됨 …9z9z"))

        // 키 확인: the cheapest request each; the Tavily one is refused
        http.on("generativelanguage") { AiReplies.gemini("네") }.on("api.tavily.com", status = 401) { AiReplies.TAVILY_401 }
        tap(button(AiTexts.CHECK), 0)
        waitFor(hasTestTag("key-check-GEMINI") and hasText("정상"))
        tap(button(AiTexts.CHECK), 1)
        waitFor(hasTestTag("key-check-TAVILY") and hasText("키가 틀려요"))
        assertTrue(has(hasText("Unauthorized: missing or invalid API key.")))
        assertEquals(2, http.requests.size)
        assertTrue("\"maxOutputTokens\":8" in requestBody(0))
        assertTrue("\"search_depth\":\"basic\"" in requestBody(1))
        assertTrue(has(hasText(AiTexts.TAVILY_CHECK_HINT)))

        // another option: its own key field, model presets, a model of one's own
        tap(button(AiProvider.CLAUDE.label))
        waitFor(field(AiKeySlot.CLAUDE.placeholder))
        assertFalse(has(field(AiKeySlot.TAVILY.placeholder)) || has(hasText("저장됨 …9z9z")))
        waitFor(button(GuideTexts.open(AiKeySlot.CLAUDE.label)))
        assertFalse(has(button(GuideTexts.open(AiKeySlot.TAVILY.label))))
        tap(button("claude-sonnet-5"))
        waitUntil("model saved") { runBlocking { prefs.load() }.model(AiProvider.CLAUDE) == "claude-sonnet-5" }
        replaceIn("claude-sonnet-5", "claude-haiku-4-5")
        waitUntil("typed model saved") { runBlocking { prefs.load() }.model(AiProvider.CLAUDE) == "claude-haiku-4-5" }
        assertEquals(AiProvider.CLAUDE, runBlocking { prefs.load() }.provider)

        // 지우기 asks first
        tap(button(AiProvider.GEMINI_TAVILY.label))
        waitFor(hasText("저장됨 …a1b2"))
        tap(button(AiTexts.CLEAR), 0)
        waitForText("저장한 Gemini API 키를 이 휴대폰에서 지울까요?")
        clickNode(dialogButton(AiTexts.CLEAR))
        waitGone(hasText("저장됨 …a1b2"))
        assertNull(secrets.values[NoteHelperService.secretName(AiKeySlot.GEMINI)])

        // this phone's settings: never in a backup
        val json = runBlocking { koinGet<BackupService>().export().json }
        assertFalse(json.contains("device.ai") || json.contains("tvly-") || json.contains("AQ."))
    }

    @Test
    fun searchSettings_depthAndPeople_onlyForTavily_withTheirCredits_keptOnThisPhone_andUsedForTheNextQuestion() {
        launchApp()
        openSettings()
        val depth = { label: String -> button(label) and hasAnyAncestor(hasTestTag("search-depth")) }
        val people = { label: String -> button(label) and hasAnyAncestor(hasTestTag("people")) }
        val credits = { n: Int -> hasTestTag("tavily-credits") and hasText(AiTexts.credits(n)) }
        // Gemini 무료 + Tavily: 정밀 and 사람들 의견 켬 by default, 3 credits a question
        waitFor(depth("정밀"))
        assertEquals(listOf("기본", "정밀", "정밀+기본"), SearchDepth.entries.map { it.label })
        SearchDepth.entries.forEach { assertTrue(it.label, has(depth(it.label))) }
        assertTrue(isSelected(depth("정밀")) && isSelected(people(AiTexts.PEOPLE_ON)))
        assertTrue(has(hasText(AiTexts.SEARCH_DEPTH_HINT)) && has(hasText(AiTexts.PEOPLE_HINT)))
        assertTrue(has(credits(3)))
        assertTrue(has(hasText("질문 한 번에 약 3크레딧 · 무료 1,000크레딧이면 한 달 약 330번")))

        // each choice is saved at once, and the line follows it
        tap(depth("정밀+기본"))
        waitFor(credits(4))
        waitUntil("depth saved") { runBlocking { prefs.load() }.searchDepth == SearchDepth.PRECISE_BASIC }
        tap(people(AiTexts.PEOPLE_OFF))
        waitFor(credits(3))
        waitUntil("people saved") { !runBlocking { prefs.load() }.people }
        tap(depth("기본"))
        waitFor(credits(1) and hasText("한 달 약 1,000번", substring = true))
        assertTrue(isSelected(depth("기본")) && isSelected(people(AiTexts.PEOPLE_OFF)))

        // the other options search on their own: no choice there
        tap(button(AiProvider.OPENAI.label))
        waitFor(field(AiKeySlot.OPENAI.placeholder))
        assertFalse(has(hasTestTag("search-depth")) || has(hasTestTag("people")) || has(hasTestTag("tavily-credits")))
        tap(button(AiProvider.GEMINI_TAVILY.label))
        waitFor(depth("기본"))
        assertTrue(isSelected(depth("기본")) && isSelected(people(AiTexts.PEOPLE_OFF)))
        // this phone's settings only
        runBlocking { koinGet<BackupService>().export().json }.let { json ->
            assertFalse(json.contains("searchDepth") || json.contains("device.ai.people"))
        }

        // the next question: one basic search of the English query, no blog search, no people line
        AiSetup.ready(AiProvider.GEMINI_TAVILY)
        AiSetup.tavilyAnswers(AiReplies.DESCRIBE_ANSWER, AiReplies.DESCRIBE_QUERIES)
        back()
        openNewForm()
        tap(button(AiTexts.ASK_FROM_FORM))
        typeInto(AiTexts.DESCRIBE_PLACEHOLDER, "잘 익은 자두 같아요")
        tap(button(AiTexts.DESCRIBE_SEND))
        waitFor(hasTestTag("note-candidates"))
        assertEquals(3, http.requests.size)
        assertTrue("\"search_depth\":\"basic\",\"max_results\":5,\"chunks_per_source\":3" in requestBody(1))
        assertFalse(AiReplies.BLOG_SEARCH in requestBody(1))
        assertFalse("사람들의 말" in requestBody(2))
        assertTrue(has(hasTestTag("answer-queries") and hasText("검색어: ripe plum bitter finish tasting notes specialty coffee")))

        // 정밀+기본 with 사람들 의견: basic then advanced for the same query, then the Korean blog search
        runBlocking { prefs.setSearchDepth(SearchDepth.PRECISE_BASIC); prefs.setPeople(true) }
        tap(button(AiTexts.ASK_AGAIN))
        waitUntil("asked again") { http.requests.size == 8 }
        waitFor(hasTestTag("answer-queries") and hasText("검색어: ripe plum bitter finish tasting notes specialty coffee · 커피 원두 후기 잘 익은 자두 같아요"))
        assertTrue(AiReplies.QUERY_STEP in requestBody(3))
        assertTrue("\"query\":\"ripe plum bitter finish tasting notes specialty coffee\",\"search_depth\":\"basic\"" in requestBody(4))
        assertTrue("\"query\":\"ripe plum bitter finish tasting notes specialty coffee\",\"search_depth\":\"advanced\"" in requestBody(5))
        assertTrue("\"query\":\"커피 원두 후기 잘 익은 자두 같아요\",\"search_depth\":\"basic\"" in requestBody(6))
        assertTrue("\"include_domains\":[\"blog.naver.com\",\"tistory.com\",\"brunch.co.kr\",\"cafe.naver.com\"]" in requestBody(6))
        assertTrue("비슷하게 느낀 사람들의 말" in requestBody(7))
        // the blog pages come last and read as personal opinions
        waitFor(hasTestTag("source-5") and hasText("brunch.co.kr") and hasText(AiTexts.PERSONAL))
    }

    @Test
    fun blogSearchFails_theAnswerComesFromTheMainPages_withANote() {
        AiSetup.ready(AiProvider.GEMINI_TAVILY)
        http.on("generativelanguage", bodyPart = AiReplies.QUERY_STEP) { AiReplies.gemini(AiReplies.NOTE_QUERIES) }
            .on("api.tavily.com", status = 500, bodyPart = AiReplies.BLOG_SEARCH) { "{}" }
            .on("api.tavily.com") { AiReplies.TAVILY }
            .on("generativelanguage") { AiReplies.gemini("- ${AiReplies.NOTE_SENTENCE_1}[1]") }
        openHelper(Route.NoteHelper(mode = "note", query = "자스민"))
        waitFor(hasTestTag("source-3"))
        assertTrue(has(hasText(AiTexts.BLOGS_FAILED)))
        assertFalse(has(hasTestTag("note-helper-error")) || has(hasTestTag("source-4")))
        assertEquals(4, http.requests.size)
        assertTrue(AiReplies.BLOG_SEARCH in requestBody(2))
        assertFalse("사람들의 느낌" in requestBody(3))
        assertTrue(has(hasTestTag("answer-queries") and hasText("검색어: jasmine tasting note specialty coffee · 커피 원두 자스민 노트 후기")))
    }

    // ───────────────────────── mode A from 노트 상세 ─────────────────────────

    @Test
    fun noteDetail_notice_thenAnswerWithMarksBadgesGreySentenceAndSources_noticeOnlyOnce() {
        SampleData.seed()
        AiSetup.ready(AiProvider.GEMINI_TAVILY, consented = false)
        AiSetup.tavilyAnswers()
        launchApp()
        openJasmineNote()
        tap(button(AiTexts.ASK_FROM_NOTE))

        // what is sent where, before anything is sent
        waitFor(hasTestTag("ai-consent"))
        AiTexts.consent(AiProvider.GEMINI_TAVILY).forEach { assertTrue(it, has(hasText(it))) }
        assertTrue(http.requests.isEmpty())
        clickNode(dialogButton(AiTexts.CONSENT_SEND))
        waitFor(hasTestTag("source-3"))
        assertTrue(runBlocking { prefs.load() }.consents.contains(AiProvider.GEMINI_TAVILY))

        // by default: Gemini writes an English query from the note, Tavily searches it (advanced), then Korean blog
        // posts with a Korean query (basic), and Gemini answers from both, with the people line
        assertEquals(4, http.requests.size)
        assertTrue("향미 노트: \\\"자스민\\\"" in requestBody(0) && AiReplies.QUERY_STEP in requestBody(0))
        assertTrue("\"query\":\"jasmine tasting note specialty coffee\",\"search_depth\":\"advanced\"" in requestBody(1))
        assertTrue("\"query\":\"커피 원두 자스민 노트 후기\",\"search_depth\":\"basic\"" in requestBody(2) && AiReplies.BLOG_SEARCH in requestBody(2))
        assertTrue("사람들의 느낌: 커뮤니티·개인 블로그 글에서" in requestBody(3))
        assertTrue(has(hasTestTag("answer-queries") and hasText("검색어: jasmine tasting note specialty coffee · 커피 원두 자스민 노트 후기")))
        assertTrue(has(hasText("“자스민”")))
        assertTrue(has(hasText("Gemini 무료 + Tavily · gemini-3.5-flash-lite")))
        assertTrue(has(hasText(AiTexts.DISCLAIMER)))

        // the marks follow their sentences; the sentence no page backs stays, in grey
        val ps = paragraphs()
        assertEquals(
            listOf(
                "${AiReplies.NOTE_SENTENCE_1}[1]",
                "한 로스터리는 예가체프에 \"Jasmine, Bergamot, Black Tea\"라고 적었다.[2]",
                "${AiReplies.NOTE_SENTENCE_3}[2]",
                "한 블로그는 \"꽃차 같은 향\"이라고 적었다(개인 의견).[3]",
                "${AiReplies.PEOPLE_SENTENCE}[4]",
                AiReplies.UNCITED,
            ),
            ps.map { it.text.removePrefix("- ") },
        )
        assertEquals(Ink.text, colorOf(ps[0], "SCA는"))
        assertEquals(Ink.textFaint, colorOf(ps[5], AiReplies.UNCITED))

        // quotes checked against the cited page
        assertEquals(4, count(hasText(AiTexts.FOUND)))
        assertEquals(1, count(hasText(AiTexts.NOT_FOUND)))
        assertTrue(has(hasTestTag("quote-check") and hasAnyDescendant(hasText("“honey sweetness”")) and hasAnyDescendant(hasText(AiTexts.NOT_FOUND))))

        // sources: number, title, domain, kind
        assertTrue(has(hasTestTag("source-1") and hasText("sca.coffee") and hasText(AiTexts.INSTITUTION)))
        assertTrue(has(hasTestTag("source-2") and hasText("example-roaster.com")))
        assertFalse(has(hasTestTag("source-2") and hasText(AiTexts.INSTITUTION)))
        assertTrue(has(hasTestTag("source-3") and hasText("blog.naver.com") and hasText(AiTexts.PERSONAL)))
        // the blog search's pages come last: 개인 의견
        assertTrue(has(hasTestTag("source-4") and hasText("coffeelog.tistory.com") and hasText(AiTexts.PERSONAL)))
        assertTrue(has(hasTestTag("source-5") and hasText("brunch.co.kr") and hasText(AiTexts.PERSONAL)))
        assertFalse(has(hasTestTag("source-6")))
        assertEquals("개인 의견", AiTexts.PERSONAL)

        // a mark points at its source
        val marks = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("answer-paragraph")), useUnmergedTree = true)
        assertEquals(5, marks.fetchSemanticsNodes().size)
        marks[3].performClick()
        settle()
        waitUntil("[3] points at source 3") { isSelected(hasTestTag("source-3")) }
        assertFalse(isSelected(hasTestTag("source-1")))

        // a source opens its page
        tap(hasTestTag("source-1"))
        assertEquals("https://sca.coffee/sca-news/flavor-notes-jasmine", lastOpenedUrl())

        // the notice is asked once per option
        back()
        waitForText("“자스민”와 함께 기록된 노트 조합")
        tap(button(AiTexts.ASK_FROM_NOTE))
        waitFor(hasTestTag("source-3"))
        assertFalse(has(hasTestTag("ai-consent")))
        assertEquals(8, http.requests.size)
    }

    @Test
    fun searchQuery_corrected_askAgainWithIt_withoutTheQueryStep() {
        AiSetup.ready(AiProvider.GEMINI_TAVILY)
        AiSetup.tavilyAnswers()
        openHelper(Route.NoteHelper(mode = "note", query = "자스민"))
        waitFor(hasTestTag("answer-queries"))
        assertEquals(4, http.requests.size)

        // a panel on the screen (not a dialog) with the query
        tap(button(AiTexts.EDIT_QUERIES))
        waitFor(hasTestTag("edit-queries"))
        assertTrue(has(field("jasmine tasting note specialty coffee")))
        assertTrue(has(hasText(AiTexts.QUERIES_HINT)))
        replaceIn("jasmine tasting note specialty coffee", "  jasmine  tea earl grey tasting notes specialty coffee \n")
        tap(button(AiTexts.ASK_WITH_QUERIES))

        waitUntil("asked again") { http.requests.size == 7 }
        waitFor(hasTestTag("answer-queries") and hasText("검색어: jasmine tea earl grey tasting notes specialty coffee · 커피 원두 자스민 노트 후기"))
        // no query step: Tavily with the corrected query (advanced), the blog search as before, then Gemini
        assertTrue("\"query\":\"jasmine tea earl grey tasting notes specialty coffee\",\"search_depth\":\"advanced\"" in requestBody(4))
        assertTrue("\"query\":\"커피 원두 자스민 노트 후기\"" in requestBody(5))
        assertFalse(AiReplies.QUERY_STEP in requestBody(6))
        assertFalse(has(hasTestTag("edit-queries")))
        waitFor(hasTestTag("source-3"))
    }

    /** What TalkBack reads after the step's name, i.e. how the step is shown. */
    private fun stage(stage: AskStage): String? =
        node(hasTestTag("ask-stage-${stage.name}")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun waitStage(stage: AskStage, status: StageStatus) = waitUntil("${stage.name} $status") { stage(stage) == AiTexts.stageState(status) }

    @Test
    fun asking_showsEachStepAsItHappens_aCorrectedQuerySkipsTheFirst_otherServicesOneStep_stillDotsWithoutMotion() {
        AiSetup.ready(AiProvider.GEMINI_TAVILY)
        var search = http.hold("api.tavily.com")
        AiSetup.tavilyAnswers()
        openHelper(Route.NoteHelper(mode = "note", query = "자스민"))
        // the query is written; Tavily is still searching
        waitStage(AskStage.SEARCH, StageStatus.CURRENT)
        assertEquals(AiTexts.stageState(StageStatus.DONE), stage(AskStage.QUERY))
        assertEquals(AiTexts.stageState(StageStatus.LATER), stage(AskStage.ANSWER))
        assertTrue(has(hasText("✓ ${AiTexts.STAGE_QUERY}")))
        // the moving dots (a test clock runs no infinite animation: all three stay), on the line and on the step
        assertTrue(has(hasText(AiTexts.ASKING + ".\u00A0.\u00A0.")))
        assertTrue(has(hasText(AiTexts.STAGE_SEARCH + ".\u00A0.\u00A0.")))
        search.complete(Unit)
        waitFor(hasTestTag("answer-queries"))
        assertFalse(has(hasTestTag("ask-stages")))

        // "이 검색어로 다시 묻기": no query step, shown struck out
        search = http.hold("api.tavily.com")
        tap(button(AiTexts.EDIT_QUERIES))
        tap(button(AiTexts.ASK_WITH_QUERIES))
        waitStage(AskStage.SEARCH, StageStatus.CURRENT)
        assertEquals(AiTexts.stageState(StageStatus.SKIPPED), stage(AskStage.QUERY))
        assertEquals(AiTexts.stageState(StageStatus.LATER), stage(AskStage.ANSWER))
        search.complete(Unit)
        waitFor(hasTestTag("answer-queries"))
        assertEquals(7, http.requests.size)

        // GPT searches and answers in one request: one step; with screen motion off, a still "…"
        Display.current = Display.current.copy(motion = Motion.OFF)
        AiSetup.ready(AiProvider.OPENAI)
        val gpt = http.hold("api.openai.com")
        tap(button(AiTexts.ASK_AGAIN))
        waitStage(AskStage.SEARCH_AND_ANSWER, StageStatus.CURRENT)
        assertTrue(has(hasText(AiTexts.STAGE_SEARCH_AND_ANSWER + "…")))
        assertTrue(has(hasText(AiTexts.ASKING + "…")))
        assertFalse(has(hasTestTag("ask-stage-QUERY")))
        assertFalse(has(hasText("›")))
        gpt.complete(Unit)
    }

    @Test
    fun notice_declined_sendsNothing_untilAskedAgain() {
        AiSetup.ready(AiProvider.OPENAI, consented = false)
        openHelper(Route.NoteHelper(mode = "note", query = "자스민"))
        waitFor(hasTestTag("ai-consent"))
        assertTrue(has(hasText("보내는 곳: OpenAI API(웹 검색 포함)")))
        clickNode(dialogButton("취소"))
        waitForText(AiTexts.DECLINED)
        assertTrue(http.requests.isEmpty())
        tap(button(AiTexts.ASK_AGAIN))
        waitFor(hasTestTag("ai-consent"))
    }

    // ───────────────────────── mode B from the record form ─────────────────────────

    @Test
    fun describe_fromTheForm_candidatesPicked_addedToTheNotesWithoutDuplicates() {
        AiSetup.ready(AiProvider.GEMINI_TAVILY)
        AiSetup.tavilyAnswers(AiReplies.DESCRIBE_ANSWER, AiReplies.DESCRIBE_QUERIES)
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "AI 노트 테스트 원두")
        typeInto(chipPlaceholder, "jasmine", 1)
        clickNode(button("추가"), 1)
        waitFor(hasContentDescription("jasmine 삭제"))

        // a small panel under the notes (not a dialog) for the taste in one's own words
        tap(button(AiTexts.ASK_FROM_FORM))
        waitFor(hasTestTag("ask-ai-panel"))
        assertTrue(has(hasText(AiTexts.DESCRIBE_HINT)))
        typeInto(AiTexts.DESCRIBE_PLACEHOLDER, "잘 익은 자두 같고 끝이 쌉쌀해요")
        tap(button(AiTexts.DESCRIBE_SEND))

        waitFor(hasTestTag("note-candidates"))
        // the taste in the user's words goes to the query step; the English query goes to Tavily
        assertTrue("맛 묘사: \\\"잘 익은 자두 같고 끝이 쌉쌀해요\\\"" in requestBody(0))
        assertTrue("\"query\":\"ripe plum bitter finish tasting notes specialty coffee\"" in requestBody(1))
        assertTrue("\"query\":\"커피 원두 후기 잘 익은 자두 같고 끝이 쌉쌀해요\"" in requestBody(2))
        assertTrue("플레이버 휠 용어: Black Tea, Floral" in requestBody(3))
        // the app's own terms found in the answer, in order
        val candidates = listOf("Dark Chocolate", "자두", "다크 초콜릿", "Jasmine", "Floral")
        val chip = { t: String -> button(t) and hasAnyAncestor(hasTestTag("note-candidates")) }
        candidates.forEach { assertTrue(it, has(chip(it))) }
        val lefts = candidates.map { node(chip(it)).fetchSemanticsNode().let { n -> n.positionInRoot.y * 10_000 + n.positionInRoot.x } }
        assertEquals(lefts.sorted(), lefts)
        assertFalse(has(button(AiTexts.ADD_TO_NOTES) and hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled)))

        tap(chip("자두"))
        tap(chip("Jasmine"))
        tap(button("${AiTexts.ADD_TO_NOTES} (2)"))

        // back in the form: 자두 added once, Jasmine not a second time next to "jasmine"; the panel is closed
        waitFor(hasContentDescription("자두 삭제"))
        assertFalse(has(hasTestTag("ask-ai-panel")))
        assertEquals(1, count(hasContentDescription("jasmine 삭제")))
        assertFalse(has(hasContentDescription("Jasmine 삭제")))
        saveForm("AI 노트 테스트 원두")
        assertEquals("jasmine, 자두", entries().single().actualNotes)
    }

    // ───────────────────────── keys and errors ─────────────────────────

    @Test
    fun missingKey_saysHowToGetOne_settingsOpensAtTheAiSection_thenTheAnswerComes() {
        SampleData.seed()
        AiSetup.tavilyAnswers()
        launchApp()
        openJasmineNote()
        tap(button(AiTexts.ASK_FROM_NOTE))
        waitForText("“Gemini 무료 + Tavily”로 물으려면 Gemini API 키 · Tavily API 키가 필요해요.", substring = true)
        assertTrue(http.requests.isEmpty())

        tap(button(AiErrors.LINK_KEYS))
        waitFor(field(AiKeySlot.GEMINI.placeholder))
        // 설정 opens scrolled to the AI section, not at its top
        waitUntil("AI section on screen") {
            val top = node(field(AiKeySlot.GEMINI.placeholder)).fetchSemanticsNode().positionInRoot.y
            top > 0 && top < rootHeight()
        }
        typeInto(AiKeySlot.GEMINI.placeholder, "AQ.new-key-1111")
        tap(button(AiTexts.SAVE), 0)
        waitFor(hasText("저장됨 …1111"))
        typeInto(AiKeySlot.TAVILY.placeholder, "tvly-new-2222")
        tap(button(AiTexts.SAVE))
        waitFor(hasText("저장됨 …2222"))

        back()
        waitFor(hasTestTag("ai-consent"))
        clickNode(dialogButton(AiTexts.CONSENT_SEND))
        waitFor(hasTestTag("source-3"))
        assertEquals("AQ.new-key-1111", http.requests[0].headers["x-goog-api-key"])
        assertEquals("Bearer tvly-new-2222", http.requests[1].headers["Authorization"])
        assertEquals("Bearer tvly-new-2222", http.requests[2].headers["Authorization"])
        assertEquals("AQ.new-key-1111", http.requests[3].headers["x-goog-api-key"])
    }

    @Test
    fun geminiSearch_freeProject429_saysTurnOnBillingOrPickTavily() {
        AiSetup.ready(AiProvider.GEMINI_SEARCH)
        http.on("generativelanguage", status = 429) { AiReplies.GEMINI_429 }
        openHelper(Route.NoteHelper(mode = "note", query = "자스민"))
        waitFor(hasTestTag("note-helper-error"))
        assertTrue(has(hasText(AiErrors.SEARCH_BILLING)))
        assertTrue(has(hasText("결제를 이미 켰다면 한도를 넘은 거예요. 잠시 뒤 다시 해 보세요.")))
        assertTrue(has(hasText("You exceeded your current quota, please check your plan and billing details.", substring = true)))
        assertTrue(has(button(AiErrors.LINK_PROVIDER)))
        assertTrue("\"google_search\"" in requestBody(0))
        // 다시 묻기 sends again
        http.clear().on("generativelanguage") { AiReplies.GROUNDED }
        tap(button(AiTexts.ASK_AGAIN))
        waitFor(hasTestTag("source-1"))
        assertTrue("Google's Search Suggestions are shown with the grounded answer", has(hasTestTag("search-suggestions"), unmerged = true))
        assertTrue(has(hasText(AiTexts.SUGGESTIONS)))
    }

    @Test
    fun modelNoLongerAvailable_404_saysPickAnother_withTheSettingsLink() {
        AiSetup.ready(AiProvider.GEMINI_TAVILY, model = "gemini-2.5-flash")
        http.on("api.tavily.com") { AiReplies.TAVILY }.on("generativelanguage", status = 404) { AiReplies.GEMINI_404 }
        openHelper(Route.NoteHelper(mode = "note", query = "자스민"))
        waitFor(hasTestTag("note-helper-error"))
        assertTrue(has(hasText("‘gemini-2.5-flash’ 모델은 쓸 수 없어요.")))
        assertTrue(has(hasText("새로 만든 키에는 더 이상 열어 주지 않는 모델이에요.", substring = true)))
        assertTrue(has(button(AiErrors.LINK_MODEL)))
        // the query step's 404 falls back to the fixed search quietly; the answer step reports it
        assertEquals(4, http.requests.size)
        assertTrue("\\\"자스민\\\" coffee flavor note meaning tasting notes" in requestBody(1))
        assertTrue(http.requests[3].url.endsWith("/gemini-2.5-flash:generateContent"))
    }

    @Test
    fun claude_answerWithCitedTextChecks_fallbacksOnlyForOpus5() {
        AiSetup.ready(AiProvider.CLAUDE)
        http.on("api.anthropic.com/v1/messages") { AiReplies.CLAUDE }
        openHelper(Route.NoteHelper(mode = "note", query = "자스민"))
        waitFor(hasTestTag("source-2"))
        assertTrue(has(hasText("Claude (Anthropic) · claude-opus-5")))
        // ✓ from the cited excerpt; nothing is marked "not found" for Claude
        assertEquals(1, count(hasText(AiTexts.FOUND)))
        assertEquals(0, count(hasText(AiTexts.NOT_FOUND)))
        assertTrue(has(hasTestTag("source-2") and hasText("reddit.com") and hasText(AiTexts.PERSONAL)))
        // the searches Claude ran, read-only
        assertTrue(has(hasTestTag("answer-queries") and hasText("검색어: jasmine coffee tasting note")))
        assertFalse(has(button(AiTexts.EDIT_QUERIES)))
        assertEquals("server-side-fallback-2026-07-01", http.requests[0].headers["anthropic-beta"])
        assertTrue("\"fallbacks\":\"default\"" in requestBody(0))
        assertEquals(Ink.textFaint, colorOf(paragraphs()[1], "출처 없이"))

        runBlocking { prefs.setModel(AiProvider.CLAUDE, "claude-sonnet-5") }
        tap(button(AiTexts.ASK_AGAIN))
        waitUntil("asked again") { http.requests.size == 2 }
        waitFor(hasTestTag("source-2"))
        assertNull(http.requests[1].headers["anthropic-beta"])
        assertFalse("fallbacks" in requestBody(1))
    }
}
