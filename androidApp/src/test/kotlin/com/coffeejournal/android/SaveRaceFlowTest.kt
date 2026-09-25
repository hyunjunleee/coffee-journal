package com.coffeejournal.android

import androidx.compose.ui.test.hasText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.db.BookDao
import com.coffeejournal.data.db.BookEntity
import com.coffeejournal.data.db.TransactionRunner
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.reference.RoadmapDefaults
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.calendar.CalendarViewModel
import com.coffeejournal.ui.extract.PantryEditorViewModel
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.nav.appGraph
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Gap #11: read-modify-write races (rapid roadmap taps, a pantry bag changed while its editor is open), and item 7:
 * system back while a form's save runs.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SaveRaceFlowTest : FlowTestBase() {

    private val store = ViewModelStore()

    @After
    fun clearViewModels() = store.clear()

    private inline fun <reified V : ViewModel> vm(crossinline create: () -> V): V =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        })[V::class.java]

    /**
     * Runs the main looper (the test thread) until [condition] holds; no Compose content is involved, so this does
     * not wait for Compose to become idle.
     */
    private fun pump(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            ShadowLooper.idleMainLooper()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(10)
        }
    }

    // ───────────────────────── gap #11: roadmap ─────────────────────────

    private fun starterItems(): List<RoadmapItem> = runBlocking {
        koinGet<RoadmapRepository>().getAll().single { it.id == RoadmapDefaults.STARTER_ID }.items
    }

    @Test
    fun roadmap_rapidTapsOnOnePhase_allLand() {
        runBlocking {
            val repo = koinGet<RoadmapRepository>()
            repo.ensureSeeded()
            val starter = repo.getAll().single()
            repo.upsert(starter.copy(items = listOf(RoadmapItem("a", "커핑 5회"), RoadmapItem("b", "핸드드립 30잔"), RoadmapItem("c", "책 한 권"))))
        }
        val vm = vm { CalendarViewModel(koinGet(), koinGet(), koinGet(), koinGet()) }
        val phase = RoadmapDefaults.STARTER_ID
        // four taps before any of their writes has finished (each one used to read the phase as it was before them all)
        vm.toggleItem(phase, "a")
        vm.toggleItem(phase, "b")
        vm.addItem(phase, "라떼아트 연습")
        vm.deleteItem(phase, "c")
        pump("all four writes") { starterItems().let { it.size == 3 && it.any { i -> i.text == "라떼아트 연습" } } }
        val items = starterItems()
        assertEquals(listOf("커핑 5회", "핸드드립 30잔", "라떼아트 연습"), items.map { it.text })
        assertEquals(listOf(true, true, false), items.map { it.done })

        // a toggle and an edit of another item in the same instant
        vm.toggleItem(phase, "a")
        vm.editItem(phase, "b", "핸드드립 50잔")
        pump("both") { starterItems().let { l -> !l[0].done && l[1].text == "핸드드립 50잔" } }
        assertEquals(listOf(false, true, false), starterItems().map { it.done })
    }

    // ───────────────────────── gap #11: pantry editor ─────────────────────────

    @Test
    fun pantryEditor_keepsWhatChangedElsewhereWhileItWasOpen() {
        val pantry = koinGet<PantryRepository>()
        val bag = PantryItem(id = "p1", name = "과테말라 안티구아", roastery = "모모스", weight = "200", createdAt = 1_780_000_000_000L)
        runBlocking { pantry.upsert(bag) }
        val editor = vm { PantryEditorViewModel("p1", pantry) }
        pump("editor loaded") { editor.form.value.loaded && editor.form.value.name == "과테말라 안티구아" }

        // meanwhile the bag is opened on the pantry list and a record is linked to it
        val openedAt = Dates.nowMillis()
        runBlocking { pantry.upsert(bag.copy(status = PantryItem.STATUS_OPENED, openedAt = openedAt, sourceEntryId = "e9")) }

        editor.update { copy(notes = "냉동 보관") }
        editor.save()
        pump("saved") { editor.form.value.saved }
        val row = runBlocking { pantry.getById("p1") }!!
        assertEquals("냉동 보관", row.notes)
        assertEquals("the bag stays opened", PantryItem.STATUS_OPENED, row.status)
        assertEquals(openedAt, row.openedAt)
        assertEquals("e9", row.sourceEntryId)
        assertEquals(bag.createdAt, row.createdAt)
    }

    // ───────────────────────── item 7: system back while saving ─────────────────────────

    private fun systemBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    @Test
    fun recordForm_systemBackWaitsForTheSave() {
        val real = koinGet<TransactionRunner>()
        val gate = CompletableDeferred<Unit>()
        loadKoinModules(module {
            single<TransactionRunner> {
                object : TransactionRunner {
                    override suspend fun <R> write(block: suspend () -> R): R { gate.await(); return real.write(block) }
                }
            }
        })
        launchApp()
        clickText("+ 새 기록 추가")
        waitForText("새 기록")
        typeInto("예: 콜롬비아 라 플라타 게이샤 워시드", "뒤로가기 테스트 원두")
        clickText("저장")
        waitForText("저장 중...")

        systemBack()
        assertTrue("still on the form while the save runs", has(hasText("저장 중...")))
        systemBack()
        assertTrue(has(hasText("저장 중...")))

        gate.complete(Unit)
        waitUntil("the new record's detail") { has(hasText("뒤로가기 테스트 원두")) && has(button("수정")) && has(button("삭제")) }
        assertEquals(1, runBlocking { koinGet<EntryRepository>().getAll() }.size)
        // back works again once the form is gone
        systemBack()
        waitForText("+ 새 기록 추가")
    }

    @Test
    fun bookForm_systemBackAndTitleBackWaitForTheSave() {
        val gate = CompletableDeferred<Unit>()
        val db = koinGet<AppDatabase>()
        val realBooks = db.bookDao()
        val gatedBooks = object : BookDao by realBooks {
            override suspend fun upsert(item: BookEntity) { gate.await(); realBooks.upsert(item) }
        }
        loadKoinModules(module { single { StudyRepository(gatedBooks, db.videoDao(), db.classDao()) } })
        compose.setContent {
            com.coffeejournal.ui.theme.CoffeeJournalTheme {
                val nav = androidx.navigation.compose.rememberNavController()
                androidx.navigation.compose.NavHost(navController = nav, startDestination = Route.Calendar) { appGraph(nav) }
                androidx.compose.runtime.LaunchedEffect(Unit) { nav.navigate(Route.BookForm()) }
            }
        }
        waitForText("책 추가")
        typeInto("예: 커핑 바이블", "뒤로가기 책")
        clickText("저장")
        settle()

        systemBack()
        assertTrue("system back waits", has(hasText("책 추가")))
        back()
        assertTrue("the title-bar back waits too", has(hasText("책 추가")))

        gate.complete(Unit)
        waitGone(hasText("책 추가"))
        assertEquals(listOf("뒤로가기 책"), runBlocking { koinGet<StudyRepository>().getBooks() }.map { it.title })
    }
}
