package com.coffeejournal.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.App
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.reference.RoadmapDefaults
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.FormMode
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.nav.appGraph
import com.coffeejournal.ui.theme.CoffeeJournalTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gap #8: the screens and states RouteScreenshotTest never rendered, scrolled pages of the long screens, an empty
 * first-run database, and the main screens on a 320 dp wide phone and at font scale 1.3 and 2.0. Doubles as a crash
 * check for every one of them; the PNGs are written only by :androidApp:recordRoborazziDebug.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotMatrixTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        startTestKoin(ApplicationProvider.getApplicationContext())
    }

    private fun seed() = SampleData.seed()

    private fun settle() { repeat(6) { Thread.sleep(250); compose.waitForIdle() } }

    /** Renders [content] with the configuration's font scale set to [fontScale] (Compose reads it through LocalDensity). */
    private fun render(fontScale: Float, content: @Composable () -> Unit) {
        if (fontScale != 1f) RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                content()
            }
        }
        settle()
    }

    private fun route(route: Route, fontScale: Float = 1f) = render(fontScale) {
        CoffeeJournalTheme {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = route) { appGraph(nav) }
        }
    }

    private fun app(fontScale: Float = 1f) = render(fontScale) { App() }

    private fun shot(file: String) = compose.onRoot().captureRoboImage("screenshots/matrix/$file.png")

    /** Dialogs and bottom sheets are windows of their own; this captures every window. */
    private fun screenShot(file: String) = captureScreenRoboImage("screenshots/matrix/$file.png")

    private fun click(matcher: SemanticsMatcher) {
        val n = compose.onAllNodes(matcher).onFirst()
        runCatching { n.performScrollTo() }
        n.performClick()
        settle()
    }

    private fun clickText(text: String) = click(hasText(text) and hasClickAction())

    /** Captures [count] viewports of the tallest vertical scroller, scrolling 85 % of a viewport between them. */
    private fun pages(prefix: String, count: Int) {
        repeat(count) { i ->
            if (i > 0) {
                val scroller = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .fetchSemanticsNodes().maxByOrNull { it.boundsInRoot.height } ?: return
                val by = scroller.boundsInRoot.height * 0.85f
                compose.runOnUiThread { scroller.config[SemanticsActions.ScrollBy].action?.invoke(0f, by) }
                settle()
            }
            shot("$prefix-p${i + 1}")
        }
    }

    private fun tab(tag: String) {
        compose.onNode(androidx.compose.ui.test.hasTestTag(tag)).performClick()
        settle()
    }

    // ───────────────────────── routes never rendered ─────────────────────────

    @Test fun videoForm() { seed(); route(Route.VideoForm("v1")); shot("50-video-form") }

    @Test fun noteDetail() { seed(); route(Route.NoteDetail(kind = "actual", noteKey = "자스민")); shot("51-note-detail") }

    @Test fun processDetail_honeyWithASubtype() {
        seed()
        runBlocking {
            val entries = GlobalContext.get().get<EntryRepository>()
            listOf("옐로 허니" to 12, "옐로 허니" to 14, "레드 허니" to 16).forEachIndexed { i, (sub, day) ->
                entries.upsert(
                    Entry(
                        id = "h$i", createdAt = Dates.toMillis(LocalDate(2026, 9, day), 9, 0), category = Category.BEAN,
                        name = "코스타리카 타라주 $sub", country = "코스타리카", process = sub,
                    )
                )
            }
        }
        route(Route.ProcessDetail("허니", "허니"))
        clickText("옐로 허니")
        shot("52-process-honey-yellow")
    }

    @Test fun recordForm_cafe() { seed(); route(Route.RecordForm(mode = FormMode.CAFE)); pages("53-form-cafe", 3) }

    @Test fun calendar_classes() { seed(); route(Route.Calendar); clickText("클래스"); shot("54-calendar-classes") }

    @Test fun calendar_roadmapExpanded() {
        seed()
        runBlocking {
            val repo = GlobalContext.get().get<RoadmapRepository>()
            val starter = repo.getAll().single { it.id == RoadmapDefaults.STARTER_ID }
            repo.upsert(
                starter.copy(
                    items = listOf(
                        RoadmapItem("r1", "커핑 5회 참여하기", done = true), RoadmapItem("r2", "핸드드립 30잔 기록"),
                        RoadmapItem("r3", "《커피 과학》 읽고 추출 변수 정리하기 — 온도, 분쇄도, 비율, 시간을 하나씩 바꿔 보기"),
                    )
                )
            )
        }
        // the D-day is set, so the current phase (the starter) opens by itself
        route(Route.Calendar)
        pages("55-calendar-roadmap", 3)
    }

    @Test fun calendar_dayPanel() {
        seed()
        // two records and a blend on one day of the month the calendar opens on
        val today = Dates.today()
        val day = if (today.day > 1) Dates.plusDays(today, -1) else today
        runBlocking {
            val entries = GlobalContext.get().get<EntryRepository>()
            entries.upsert(Entry(id = "d1", createdAt = Dates.toMillis(day, 8, 10), name = "케냐 기통가 AA (모모스)", dose = "15"))
            entries.upsert(Entry(id = "d2", createdAt = Dates.toMillis(day, 14, 0), category = Category.CAFE, name = "에티오피아 구지 내추럴", cafeName = "FELT 청계천"))
        }
        route(Route.Calendar)
        click(hasContentDescription("${day.month.number}월 ${day.day}일, ", substring = true) and hasClickAction())
        screenShot("56-calendar-day-panel")
    }

    @Test fun recordForm_flavorWheelOpen() {
        seed()
        route(Route.RecordForm(mode = FormMode.EXTRACT, entryId = "e1"))
        click(hasText("🎨 SCA Coffee Taster's Flavor Wheel") and hasClickAction())
        compose.onAllNodes(hasText("Start at the center", substring = true)).onFirst().performScrollTo()
        settle()
        pages("57-form-flavor-wheel", 3)
    }

    @Test fun datePicker() {
        seed()
        route(Route.PantryEditor("p2"))
        click(hasText("2026-09-20") and hasClickAction())
        screenShot("58-date-picker")
    }

    @Test fun timePicker() {
        seed()
        route(Route.RecordForm(mode = FormMode.EXTRACT, entryId = "e1"))
        click(hasText("08:30") and hasClickAction())
        screenShot("59-time-picker")
    }

    // ───────────────────────── first run: an empty database ─────────────────────────

    @Test fun firstRun_everyTab() {
        app()
        shot("60-empty-home")
        tab("tab-calendar"); shot("61-empty-calendar")
        tab("tab-bean"); shot("62-empty-bean")
        tab("tab-misc"); shot("63-empty-misc")
    }

    // ───────────────────────── long screens, page by page ─────────────────────────

    @Test fun recordForm_brew_pages() { seed(); route(Route.RecordForm(mode = FormMode.EXTRACT)); pages("64-form-brew", 8) }

    @Test fun recordForm_cupping_pages() { seed(); route(Route.RecordForm(mode = FormMode.CUPPING, entryId = "e5")); pages("65-form-cupping", 6) }

    @Test fun entryDetail_pages() { seed(); route(Route.EntryDetail("e1")); pages("66-detail-brew", 5) }

    @Test fun beanMap_pages() { seed(); route(Route.Bean); pages("67-bean-map", 6) }

    // ───────────────────────── 320 dp wide ─────────────────────────

    private fun mainScreens(prefix: String, fontScale: Float) {
        seed()
        app(fontScale)
        pages("$prefix-home", 2)
        tab("tab-calendar"); shot("$prefix-calendar")
        tab("tab-bean"); pages("$prefix-bean", 2)
        tab("tab-misc"); shot("$prefix-misc")
    }

    private fun formScreens(prefix: String, fontScale: Float) {
        seed()
        route(Route.RecordForm(mode = FormMode.EXTRACT, entryId = "e1"), fontScale)
        pages("$prefix-form", 10)
    }

    private fun detailScreens(prefix: String, fontScale: Float) {
        seed()
        route(Route.EntryDetail("e5"), fontScale)
        pages("$prefix-detail-cupping", 3)
    }

    private fun pantryScreens(prefix: String, fontScale: Float) {
        seed()
        route(Route.Pantry, fontScale)
        pages("$prefix-pantry", 2)
    }

    @Test @Config(qualifiers = NARROW) fun narrow_tabs() = mainScreens("70-w320", 1f)
    @Test @Config(qualifiers = NARROW) fun narrow_form() = formScreens("71-w320", 1f)
    @Test @Config(qualifiers = NARROW) fun narrow_detail() = detailScreens("72-w320", 1f)
    @Test @Config(qualifiers = NARROW) fun narrow_pantry() = pantryScreens("73-w320", 1f)

    /** Design v2 §1: the location picker, the 로스터리 map with a selected pin, and the roastery form's location field. */
    private fun mapPicker(prefix: String, fontScale: Float) {
        seed()
        route(Route.MapPicker(target = "roastery", name = "커피 리브레", point = "37.5446,127.0557"), fontScale)
        pages("$prefix-map-picker", 2)
    }

    private fun roasteryMap(prefix: String, fontScale: Float) {
        seed()
        route(Route.Bean, fontScale)
        click(hasText("로스터리") and hasClickAction())
        click(hasContentDescription("커피 리브레, ", substring = true) and hasClickAction())
        pages("$prefix-roastery-map", 3)
    }

    private fun roasteryForm(prefix: String, fontScale: Float) {
        seed()
        route(Route.FlatItemForm(type = "source", itemId = "m4"), fontScale)
        pages("$prefix-roastery-form", 2)
    }

    @Test @Config(qualifiers = NARROW) fun narrow_mapPicker() = mapPicker("74-w320", 1f)
    @Test @Config(qualifiers = NARROW) fun narrow_roasteryMap() = roasteryMap("74-w320", 1f)
    @Test @Config(qualifiers = NARROW) fun narrow_roasteryForm() = roasteryForm("74-w320", 1f)
    @Test fun fontScale20_mapPicker() = mapPicker("94-fs20", 2f)
    @Test fun fontScale20_roasteryMap() = roasteryMap("94-fs20", 2f)
    @Test fun fontScale20_roasteryForm() = roasteryForm("94-fs20", 2f)

    // ───────────────────────── font scale 1.3 and 2.0 ─────────────────────────

    @Test fun fontScale13_tabs() = mainScreens("80-fs13", 1.3f)
    @Test fun fontScale13_form() = formScreens("81-fs13", 1.3f)
    @Test fun fontScale13_detail() = detailScreens("82-fs13", 1.3f)
    @Test fun fontScale20_tabs() = mainScreens("90-fs20", 2f)
    @Test fun fontScale20_form() = formScreens("91-fs20", 2f)
    @Test fun fontScale20_detail() = detailScreens("92-fs20", 2f)
    @Test fun fontScale20_pantry() = pantryScreens("93-fs20", 2f)

    private companion object {
        const val NARROW = "w320dp-h640dp-xhdpi"
    }
}
