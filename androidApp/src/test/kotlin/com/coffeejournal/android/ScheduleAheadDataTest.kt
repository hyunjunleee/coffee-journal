package com.coffeejournal.android

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.ui.notify.PlannedReminder
import com.coffeejournal.ui.notify.ReminderCheck
import com.coffeejournal.ui.notify.ReminderPrefs
import com.coffeejournal.ui.notify.ReminderScheduleAhead
import com.coffeejournal.ui.notify.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config

/**
 * Schedule-ahead (how iOS delivers the reminders; shared code) on the real database: it plans from the journal the
 * daily check reads, and ReminderCheck.changes() follows the journal and the reminder settings but not the reminders'
 * own bookkeeping (the sent log, the scheduled plan), so a sync never wakes the next one.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class ScheduleAheadDataTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val koin get() = GlobalContext.get()

    @Before
    fun setUp() = startTestKoin(context)

    @After
    fun tearDown() = stopKoin()

    private class RecordingScheduler : ReminderScheduler {
        val waiting = linkedMapOf<String, PlannedReminder>()
        override suspend fun canNotify() = true
        override suspend fun pending() = waiting.keys.toList()
        override suspend fun remove(ids: List<String>) = ids.forEach { waiting.remove(it) }
        override suspend fun add(planned: PlannedReminder): Boolean {
            waiting[planned.id] = planned
            return true
        }
    }

    @Test
    fun plansWhatTheDailyCheckWouldSend_fromTheDatabase() = runBlocking {
        val today = Dates.today()
        ReminderFixtures.seedDueToday(today)
        val prefs = koin.get<ReminderPrefs>()
        prefs.setEnabled(true)
        val check = koin.get<ReminderCheck>()
        val scheduler = RecordingScheduler()
        val planned = ReminderScheduleAhead(prefs, check::data, scheduler).sync(Dates.toMillis(today, 8, 0), Dates.systemZone)
        // today's nine o'clock: the three the Android check posts today, in the same words
        assertEquals(listOf(today.atTime(9, 0)), planned.map { it.at }.distinct())
        assertEquals(check.dueToday(today), planned.map { it.reminder })
        assertEquals(setOf(ReminderKind.PEAK, ReminderKind.LOW_STOCK, ReminderKind.DDAY), planned.map { it.reminder.kind }.toSet())
        assertEquals(planned.map { it.id }, scheduler.waiting.keys.toList())
        assertEquals(planned.associate { it.reminder.key to it.at }, prefs.scheduled())
    }

    @Test
    fun changes_followTheJournalAndTheSettings_notTheRemindersOwnBookkeeping() = runBlocking {
        val check = koin.get<ReminderCheck>()
        val prefs = koin.get<ReminderPrefs>()
        val settings = koin.get<SettingsRepository>()
        val seen = Channel<Unit>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { check.changes().collect { seen.send(Unit) } }
        suspend fun next() = withTimeout(5_000) { seen.receive() }
        suspend fun quiet(what: String) = assertNull(what, withTimeoutOrNull(1_000) { seen.receive() })
        try {
            next() // at once: the app start
            koin.get<EntryRepository>().upsert(Entry(id = "c1", createdAt = Dates.nowMillis(), category = Category.BEAN, name = "게이샤", dose = "15"))
            next()
            koin.get<PantryRepository>().upsert(PantryItem(id = "c2", name = "케냐 AA", createdAt = 1L))
            next()
            koin.get<BlendRepository>().upsert(Blend(id = "c3", name = "하우스", createdAt = 1L))
            next()
            settings.setDdayStart(LocalDate(2026, 1, 1))
            next()
            prefs.setKind(ReminderKind.PEAK, false)
            next()
            // what a sync writes itself: nothing new to plan
            prefs.markSent(listOf("peak:c2:2026-10-01"), Dates.today())
            prefs.setScheduled(mapOf("low:pantry:c2" to LocalDateTime(2026, 10, 1, 9, 0)))
            quiet("the sent log and the scheduled plan")
            // nor what the rules do not read
            settings.put(SettingsRepository.DEVICE_PREFIX + "test.other", "1")
            koin.get<MiscRepository>().upsert(MiscItem(id = "c4", type = MiscType.DRIPPER, name = "V60", createdAt = 1L))
            quiet("other settings and equipment")
        } finally {
            job.cancel()
        }
    }
}
