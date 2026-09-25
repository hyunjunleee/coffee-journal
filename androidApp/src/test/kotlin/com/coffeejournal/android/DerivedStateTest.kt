package com.coffeejournal.android

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.bean.BeanViewModel
import com.coffeejournal.ui.calendar.CalendarViewModel
import com.coffeejournal.ui.extract.DrinkingState
import com.coffeejournal.ui.extract.ExtractFilter
import com.coffeejournal.ui.extract.ExtractGrouping
import com.coffeejournal.ui.extract.ListedEntry
import com.coffeejournal.ui.extract.SmallPackFilter
import com.coffeejournal.ui.extract.ExtractViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

/**
 * Gap #10: with a large journal the home, calendar and bean view models derive their lists off the main thread, so
 * the main thread only publishes finished states. Gap #14: the D-day, the "N일째" text and the calendar's today cell
 * follow the date flow instead of a date read once.
 *
 * Every task the main dispatcher runs is timed with System.nanoTime (only the main-thread part: the derivation itself
 * runs elsewhere after the fix, and inside those tasks before it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class DerivedStateTest {

    /** Main dispatcher that runs on the main looper and records how long each task kept the main thread. */
    private class TimedMain : CoroutineDispatcher() {
        private val handler = Handler(Looper.getMainLooper())
        val taskNanos = CopyOnWriteArrayList<Long>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            handler.post {
                val t0 = System.nanoTime()
                block.run()
                taskNanos += System.nanoTime() - t0
            }
        }
        fun maxMillis(): Double = (taskNanos.maxOrNull() ?: 0L) / 1e6
    }

    private val main = TimedMain()
    private val store = ViewModelStore()
    private val collectors = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        startTestKoin(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        collectors.cancel()
        store.clear()
        Dispatchers.resetMain()
        stopKoin()
    }

    private inline fun <reified T : Any> get(): T = GlobalContext.get().get()

    private inline fun <reified V : ViewModel> vm(crossinline create: () -> V): V =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        })[V::class.java]

    /** Runs the main looper (this test thread) until [condition] holds. */
    private fun pump(what: String, timeoutMs: Long = 60_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            ShadowLooper.idleMainLooper()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(5)
        }
    }

    private fun <T> Flow<T>.keepCollected(): Job = collectors.launch { collect { } }

    // ───────────────────────── a large journal ─────────────────────────

    private val base = Dates.toMillis(LocalDate(2024, 1, 1), 8, 0)
    private val beans = listOf("에티오피아 예가체프", "케냐 키암부", "콜롬비아 우일라 게이샤", "과테말라 안티구아", "파나마 보케테 게이샤", "브라질 세하도", "르완다 기소비", "코스타리카 타라주")
    private val countries = listOf("에티오피아", "케냐", "콜롬비아", "과테말라", "파나마", "브라질", "르완다", "코스타리카")
    private val steps = listOf(RecipeStep("0:00", "50", "30", "뜸"), RecipeStep("0:30", "150", "30", "1차"), RecipeStep("1:00", "", "60", "드로우다운"))

    /** 1,000 records: 700 brews of 140 beans (a bag weight, steps, notes) and 300 cuppings of four beans each. */
    private fun seedLargeJournal() = runBlocking {
        val entries = (0 until 1000).map { i ->
            val createdAt = base + i * 14L * 3_600_000L
            if (i % 10 < 7) {
                val b = i % beans.size
                Entry(
                    id = "b$i", createdAt = createdAt, category = if (i % 13 == 0) Category.CAFE else Category.BEAN,
                    name = "${beans[b]} ${i % 140} (로스터리 ${i % 17})", country = countries[b], region = "지역 ${i % 23}",
                    variety = "Heirloom", farmProducer = "농장 ${i % 31}", process = "워시드", bagWeight = "200", dose = "15", water = "240",
                    expectedNotes = "자스민, 복숭아, 꿀", actualNotes = "자스민, 홍차", steps = steps,
                )
            } else {
                Entry(
                    id = "c$i", createdAt = createdAt, category = Category.CUPPING, name = "커핑 $i", cuppingType = CuppingType.HOME,
                    notes = "케냐가 좋았다",
                    cuppingBeans = (0 until 4).map { k ->
                        val b = (i + k) % beans.size
                        CuppingBean(name = "${beans[b]} 커핑 ${(i + k) % 60}", country = countries[b], region = "지역 $k", process = "내추럴", roast = "라이트")
                    },
                )
            }
        }
        get<EntryRepository>().upsertAll(entries)
    }

    /** Generous: before the fix every regroup ran inside one main-thread task (hundreds of ms for this journal). */
    private val mainTaskBudgetMs = 40.0

    private fun assertMainThreadStayedFree(what: String) {
        assertTrue("$what: longest main-thread task ${"%.1f".format(main.maxMillis())} ms (budget $mainTaskBudgetMs ms)", main.maxMillis() < mainTaskBudgetMs)
    }

    @Test
    fun home_largeJournal_groupsAndSearchesOffTheMainThread() {
        seedLargeJournal()
        val vm = vm { ExtractViewModel(get(), get(), get(), get(), get(), get(), today = flowOf(LocalDate(2026, 9, 25))) }
        vm.state.keepCollected()
        pump("home groups") { vm.state.value.loaded && vm.state.value.groups.isNotEmpty() }
        val groups = vm.state.value.groups.size
        assertTrue("grouped the brews ($groups groups)", groups >= 100)

        // typing a search: the field's own text is immediate, the search follows once typing pauses
        vm.setQuery("게")
        vm.setQuery("게이")
        vm.setQuery("게이샤")
        assertEquals("게이샤", vm.query.value)
        pump("search result") { vm.state.value.search?.query == "게이샤" }
        assertTrue("found the gesha records", vm.state.value.search!!.total > 0)

        vm.setFilterMode(ExtractFilter.SMALLPACK)
        pump("filter applied") { vm.state.value.controls.filterMode == ExtractFilter.SMALLPACK && vm.state.value.emptyText != null }
        assertMainThreadStayedFree("home")
        println("home: longest main-thread task ${"%.2f".format(main.maxMillis())} ms; one regroup + search on the test thread ${"%.1f".format(homeDerivationMillis())} ms")
    }

    /** What one emission used to cost the main thread: the home derivation for the seeded journal, run here directly. */
    private fun homeDerivationMillis(): Double {
        val all = runBlocking { get<EntryRepository>().getAll() }
        val t0 = System.nanoTime()
        val selection = ExtractGrouping.selectEntries(all, emptyList(), ExtractFilter.ALL, SmallPackFilter.ALL)
        ExtractGrouping.buildGroups(selection.brewEntries.map { ListedEntry(it) } + ExtractGrouping.projectHomeCuppings(all, selection.brewEntries), all)
        ExtractGrouping.search(all, "게이샤")
        return (System.nanoTime() - t0) / 1e6
    }

    @Test
    fun calendarAndBeanTab_largeJournal_deriveOffTheMainThread() {
        seedLargeJournal()
        val calendar = vm { CalendarViewModel(get(), get(), get(), get(), today = flowOf(LocalDate(2026, 9, 25))) }
        calendar.state.keepCollected()
        pump("calendar") { calendar.state.value.entryCount == 1000 }
        val previous = calendar.state.value.yearMonth.prev()
        calendar.prevMonth()
        pump("previous month") { calendar.state.value.grid.yearMonth == previous }

        val bean = vm { BeanViewModel(get(), get(), get(), get()) }
        bean.data.keepCollected()
        pump("bean records") { bean.data.value.loaded && bean.data.value.records.size == 700 + 300 * 4 }
        assertMainThreadStayedFree("calendar + bean tab")
        println("calendar + bean: longest main-thread task ${"%.2f".format(main.maxMillis())} ms")
    }

    // ───────────────────────── gap #14: midnight ─────────────────────────

    @Test
    fun home_ddayAndDrinkingText_followTheDateFlow() {
        runBlocking {
            get<SettingsRepository>().setDdayStart(LocalDate(2026, 9, 1))
            get<EntryRepository>().upsert(Entry(id = "e1", createdAt = Dates.toMillis(LocalDate(2026, 9, 20), 9, 0), name = "케냐 기통가 AA", bagWeight = "200", dose = "15"))
        }
        val today = MutableStateFlow(LocalDate(2026, 9, 24))
        val vm = vm { ExtractViewModel(get(), get(), get(), get(), get(), get(), today = today) }
        vm.state.keepCollected()
        pump("D-24") { vm.state.value.ddayLabel == "Coffee D-24" }
        assertEquals("마시는 중 · 5일째(2026.9.20.(일)~)", (vm.state.value.drinking as DrinkingState.RecentBean).card.eyebrow)

        today.value = LocalDate(2026, 9, 25) // midnight passes while the tab is open
        pump("D-25") { vm.state.value.ddayLabel == "Coffee D-25" }
        assertEquals("마시는 중 · 6일째(2026.9.20.(일)~)", (vm.state.value.drinking as DrinkingState.RecentBean).card.eyebrow)

        today.value = LocalDate(2026, 9, 30)
        pump("D-30 milestone") { vm.state.value.ddayMilestone?.text == "30일 기념" }
    }

    @Test
    fun calendar_todayCell_movesAtMidnight() {
        // two days of the month the calendar opens on (it opens on the real current month)
        val real = Dates.today()
        val before = if (real.day > 1) Dates.plusDays(real, -1) else real
        val after = Dates.plusDays(before, 1)
        val today = MutableStateFlow(before)
        val vm = vm { CalendarViewModel(get(), get(), get(), get(), today = today) }
        vm.state.keepCollected()
        fun todayCells() = vm.state.value.grid.cells.filter { it.isToday }.map { it.date }
        pump("today cell $before") { todayCells() == listOf(before) }
        today.value = after
        pump("today cell $after") { todayCells() == listOf(after) }
    }
}
