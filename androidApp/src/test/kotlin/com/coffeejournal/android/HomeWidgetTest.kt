package com.coffeejournal.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText as glanceText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.android.widget.CoffeeWidget
import com.coffeejournal.android.widget.CoffeeWidgetContent
import com.coffeejournal.android.widget.NEW_RECORD_LABEL
import com.coffeejournal.android.widget.WidgetActions
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import com.coffeejournal.ui.form.FormArgs
import com.coffeejournal.ui.form.RecordDrafts
import com.coffeejournal.ui.nav.LaunchTarget
import com.coffeejournal.ui.notify.HomeWidgetFeed
import com.coffeejournal.ui.notify.HomeWidgetSync
import com.coffeejournal.ui.notify.HomeWidgets
import com.coffeejournal.ui.notify.LaunchIntents
import com.coffeejournal.ui.notify.WidgetSnapshot
import com.coffeejournal.ui.notify.WidgetSnapshots
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home-screen widget (feature plan v2 §3): its layout for the snapshot the shared feed builds, that the snapshot
 * says what the home tab says, and that "+ 새 기록" carries the intent that opens the new-record form.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class HomeWidgetTest : FlowTestBase() {
    private val full = WidgetSnapshot(
        ddayLabel = "Coffee D-100", ddaySince = "2026.06.19 첫 추출", milestone = "100일 기념 ✦", ddayHint = null,
        beanEyebrow = "마시는 중 · 2026.9.21.~", beanName = "에티오피아 예가체프 워카 첼베사", remainingLine = "잔여량 170g/200g",
        moreBeans = 1, emptyText = null,
    )
    private val empty = WidgetSnapshots.build(emptyList(), emptyList(), emptyList(), null, Dates.today())

    private fun widget(snapshot: WidgetSnapshot, size: DpSize, checks: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(size)
            provideComposable { CoffeeWidgetContent(snapshot, WidgetActions.openHome(context), WidgetActions.newRecord(context)) }
            checks()
        }

    @Test
    fun tall_showsThePillAndTheCard() = widget(full, CoffeeWidget.TALL) {
        onNode(glanceText("Coffee D-100")).assertExists()
        onNode(glanceText("2026.06.19 첫 추출 · 100일 기념 ✦")).assertExists()
        onNode(glanceText("마시는 중 · 2026.9.21.~")).assertExists()
        onNode(glanceText("에티오피아 예가체프 워카 첼베사 외 1")).assertExists()
        onNode(glanceText("잔여량 170g/200g")).assertExists()
        onNode(glanceText(NEW_RECORD_LABEL)).assertExists()
    }

    @Test
    fun compact_keepsTheEssentials() = widget(full, CoffeeWidget.COMPACT) {
        onNode(glanceText("Coffee D-100")).assertExists()
        onNode(glanceText("100일 기념 ✦")).assertExists()
        onNode(glanceText("에티오피아 예가체프 워카 첼베사 외 1")).assertExists()
        onNode(glanceText("잔여량 170g/200g")).assertExists()
        onNode(glanceText("첫 추출")).assertDoesNotExist()
        onNode(glanceText("마시는 중 ·")).assertDoesNotExist()
    }

    @Test
    fun emptyJournal_showsTheHintsInTheAppsTone() = widget(empty, CoffeeWidget.TALL) {
        onNode(glanceText(WidgetSnapshots.DDAY_HINT)).assertExists()
        onNode(glanceText(WidgetSnapshots.EMPTY_BEAN)).assertExists()
        onNode(glanceText(NEW_RECORD_LABEL)).assertExists()
    }

    @Test
    fun newRecord_startsTheAppOnTheNewRecordForm_andTheRestOpensHome() {
        val newRecord = LaunchIntents.open(context, LaunchTarget.NEW_RECORD)
        assertEquals(LaunchTarget.NEW_RECORD, LaunchIntents.targetOf(newRecord))
        widget(full, CoffeeWidget.TALL) {
            onNode(hasContentDescription("새 기록 추가")).assertHasStartActivityClickAction(newRecord)
        }
        widget(full, CoffeeWidget.COMPACT) {
            onNode(hasContentDescription("새 기록 추가")).assertHasStartActivityClickAction(newRecord)
        }
        // a different target is a different PendingIntent (the intents differ in more than their extras)
        assertTrue(!newRecord.filterEquals(LaunchIntents.open(context, LaunchTarget.HOME)))
    }

    /** The feed the widget reads says what the home tab shows: the D-day pill and the 마시는 중 card. */
    @Test
    fun feed_matchesTheHomeTab() {
        SampleData.seed()
        val snapshot = runBlocking { koinGet<HomeWidgetFeed>().current() }
        launchApp()
        assertEquals(DdayRules.label(LocalDate(2026, 1, 1), Dates.today()), snapshot.ddayLabel)
        waitForText(snapshot.ddayLabel!!)
        waitForText(snapshot.beanName!!)
        waitForText(snapshot.remainingLine!!)
        assertTrue(has(hasText(snapshot.beanEyebrow!!)))
    }

    /** The app-level observer redraws the widget after saves to the tables it reads, once for a burst of writes. */
    @Test
    fun appObserver_redrawsAfterSaves_onceForABurst() = runBlocking {
        val refreshes = java.util.concurrent.atomic.AtomicInteger()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        scope.launch { HomeWidgetSync.run(koinGet(), HomeWidgets { refreshes.incrementAndGet() }, today = flowOf(Dates.today())) }
        fun waitFor(n: Int) {
            val deadline = System.currentTimeMillis() + 5_000
            while (refreshes.get() < n && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertEquals(n, refreshes.get())
        }
        try {
            Thread.sleep(300)
            val pantry = koinGet<PantryRepository>()
            repeat(5) { i -> pantry.upsert(PantryItem(id = "w$i", name = "원두 $i", createdAt = i.toLong())) }
            waitFor(1)
            Thread.sleep(HomeWidgetSync.SETTLE_MS * 2)
            assertEquals("a burst counts once", 1, refreshes.get())
            koinGet<EntryRepository>().upsert(Entry(id = "w-e", createdAt = Dates.nowMillis(), category = Category.BEAN, name = "원두 1", dose = "15"))
            waitFor(2)
            koinGet<SettingsRepository>().setDdayStart(LocalDate(2026, 1, 1))
            waitFor(3)
            // equipment is not on the widget, nor the record form's draft, written to the settings while it is typed
            koinGet<MiscRepository>().upsert(MiscItem(id = "w-m", type = MiscType.DRIPPER, name = "V60", createdAt = 1L))
            koinGet<SettingsRepository>().put(RecordDrafts.keyFor(FormArgs()), "{}")
            Thread.sleep(HomeWidgetSync.SETTLE_MS * 2)
            assertEquals(3, refreshes.get())
        } finally {
            scope.cancel()
        }
    }

    /** Renders the widget's RemoteViews as the launcher would and keeps a picture (recordRoborazziDebug). */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test
    fun render() {
        for ((name, size, snapshot) in listOf(
            Triple("62-widget-tall.png", DpSize(CoffeeWidget.TALL.width + 70.dp, CoffeeWidget.TALL.height + 10.dp), full),
            Triple("63-widget-compact.png", DpSize(CoffeeWidget.COMPACT.width + 70.dp, CoffeeWidget.COMPACT.height + 10.dp), full),
            Triple("64-widget-empty.png", DpSize(CoffeeWidget.TALL.width + 70.dp, CoffeeWidget.TALL.height + 10.dp), empty),
        )) {
            val views = runBlocking {
                GlanceRemoteViews().compose(context, size) {
                    CoffeeWidgetContent(snapshot, WidgetActions.openHome(context), WidgetActions.newRecord(context))
                }.remoteViews
            }
            val density = context.resources.displayMetrics.density
            val w = (size.width.value * density).toInt()
            val h = (size.height.value * density).toInt()
            val host = FrameLayout(context)
            val view = views.apply(context, host)
            host.addView(view, FrameLayout.LayoutParams(w, h))
            host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, w, h)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            host.draw(Canvas(bitmap))
            bitmap.captureRoboImage("screenshots/$name")
        }
    }

    private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
}
