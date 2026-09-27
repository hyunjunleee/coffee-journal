package com.coffeejournal.ui.notify

import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.domain.rules.ReminderKinds
import com.coffeejournal.domain.rules.Reminders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The phone's scheduler in memory: what waits, keyed by id, and how often the app asked. */
class FakeReminderScheduler(var allowed: Boolean = true) : ReminderScheduler {
    val waiting = linkedMapOf<String, PlannedReminder?>()
    var refuse: (PlannedReminder) -> Boolean = { false }
    var canNotifyCalls = 0

    override suspend fun canNotify(): Boolean {
        canNotifyCalls++
        return allowed
    }

    override suspend fun pending(): List<String> = waiting.keys.toList()

    override suspend fun remove(ids: List<String>) {
        ids.forEach { waiting.remove(it) }
    }

    override suspend fun add(planned: PlannedReminder): Boolean {
        if (refuse(planned)) return false
        waiting[planned.id] = planned
        return true
    }
}

/**
 * Schedule-ahead (iOS): the daily check's answer for each coming day ([ReminderData.dueOn]), planned once per reminder
 * within the window and the 64-notification cap, and kept in step with what the phone has already shown.
 */
class ReminderScheduleAheadTest {
    private val zone = TimeZone.of("Asia/Seoul")
    private val today = LocalDate(2026, 9, 26)
    private fun day(offset: Int) = Dates.plusDays(today, offset)
    private fun now(offset: Int, hour: Int, minute: Int = 0) = Dates.toMillis(day(offset), hour, minute, zone)

    // 2026-06-29 is D-1: today is D-90, D-100 is 10-06 (day 10), D-120 is 10-26 (day 30)
    private val ddayStart = LocalDate(2026, 6, 29)

    // light roast: the expected peak starts 14 days after roasting
    private fun peakBag(id: String, peakDay: Int, createdAt: Long = 1L) = PantryItem(
        id = id, name = "원두 $id", createdAt = createdAt, roastLevel = "라이트", roastDate = Dates.isoDate(day(peakDay - 14)),
    )

    private val opened = PantryItem(
        id = "o1", name = "게이샤 (리브레)", createdAt = 1L, weight = "200", status = PantryItem.STATUS_OPENED, openedAt = 1L,
    )

    // 11 brews of 16 g: 24 g of the 200 g bag left, under two usual cups
    private val brews = (1..11).map { n ->
        Entry(
            id = "e$n", createdAt = Dates.startOfDayMillis(day(-n), zone) + 9 * 3_600_000L,
            category = Category.BEAN, name = "게이샤", dose = "16",
        )
    }

    private val pantry = listOf(peakBag("p1", 0), peakBag("p2", 8), opened)
    private val data = ReminderData(brews, pantry, emptyList(), ddayStart)
    private val on = ReminderSettings(enabled = true)

    private val peakToday = "peak:p1:${day(0)}"
    private val peakLater = "peak:p2:${day(8)}"
    private val low = "low:pantry:o1"
    private val d90 = "dday:$ddayStart:90"
    private val d100 = "dday:$ddayStart:100"
    private val d120 = "dday:$ddayStart:120"

    private fun nine(offset: Int): LocalDateTime = day(offset).atTime(9, 0)

    private fun plan(nowMillis: Long, settings: ReminderSettings = on, sent: Set<String> = emptySet(), days: Int = ReminderPlan.DAYS) =
        ReminderPlan.plan(data, settings, sent, nowMillis, zone, days)

    // ── fires on day D ──

    @Test
    fun dueOn_isTheDailyChecksAnswerForThatDay() {
        val all = ReminderKinds()
        for (offset in listOf(0, 1, 8, 10, 30)) {
            val d = day(offset)
            val check = Reminders.due(pantry, brews, ReminderInputs.inUse(brews, pantry, emptyList(), d), ddayStart, d, all)
            assertEquals(check, data.dueOn(d, all, emptySet()), "day $offset")
        }
        assertEquals(listOf(peakToday, low, d90), data.dueOn(day(0), all, emptySet()).map { it.key })
        assertEquals(listOf(low), data.dueOn(day(1), all, emptySet()).map { it.key })
        assertEquals(listOf(peakLater, low), data.dueOn(day(8), all, emptySet()).map { it.key })
        assertEquals(listOf(d100), data.dueOn(day(10), all, setOf(low)).map { it.key })
        assertEquals(setOf(ReminderKind.DDAY), data.dueOn(day(10), ReminderKinds(lowStock = false), emptySet()).map { it.kind }.toSet())
    }

    // ── the plan ──

    @Test
    fun beforeTheTime_todayIsTheFirstDay_andEachReminderIsPlannedOnceOnItsFirstDay() {
        val planned = plan(now(0, 8, 30))
        assertEquals(
            listOf(peakToday to nine(0), low to nine(0), d90 to nine(0), peakLater to nine(8), d100 to nine(10)),
            planned.map { it.reminder.key to it.at },
        )
        // the Korean copy the Android check posts, and one identifier per event
        val first = planned.first()
        assertEquals("피크 시작 · 원두 p1", first.reminder.title)
        assertEquals(ReminderPlan.ID_PREFIX + peakToday, first.id)
        assertEquals("원두 소진 임박 · 게이샤 (리브레)", planned[1].reminder.title)
        assertEquals("잔여량 24g/200g · 평소 16g 기준 1잔 남았어요.", planned[1].reminder.body)
        assertEquals("커피 처음 마신 날부터 90일째 되는 날이에요.", planned[2].reminder.body)
    }

    @Test
    fun fromTheTimeOn_tomorrowIsTheFirstDay_andTheWindowIsThirtyDays() {
        // at nine sharp the day's check has gone (ReminderTime.millisUntilNext): today's peak and D-90 are not planned
        val planned = plan(now(0, 9, 0))
        assertEquals(
            listOf(low to nine(1), peakLater to nine(8), d100 to nine(10), d120 to nine(30)),
            planned.map { it.reminder.key to it.at },
        )
        // D-120 is the thirtieth day from tomorrow; from today it is the thirty-first, out of the window
        assertTrue(plan(now(0, 8, 59)).none { it.reminder.key == d120 })
        // a shorter window
        assertEquals(listOf(low, peakLater), plan(now(0, 9, 0), days = 8).map { it.reminder.key })
        assertEquals(listOf(low), plan(now(0, 9, 0), days = 7).map { it.reminder.key })
    }

    @Test
    fun sentKeysAreNotPlanned_andTheSettingsCount() {
        assertEquals(listOf(d90, peakLater, d100), plan(now(0, 8, 0), sent = setOf(peakToday, low)).map { it.reminder.key })
        assertTrue(plan(now(0, 8, 0), settings = on.copy(enabled = false)).isEmpty())
        val peaksOnly = on.copy(kinds = ReminderKinds(lowStock = false, dday = false))
        assertEquals(listOf(peakToday, peakLater), plan(now(0, 8, 0), settings = peaksOnly).map { it.reminder.key })
        // an evening time: still ahead at ten in the morning, so today's reminders go out at 21:30
        val evening = plan(now(0, 10, 0), settings = on.copy(time = ReminderTime(21, 30)))
        assertEquals(day(0).atTime(21, 30), evening.first().at)
        assertEquals(listOf(peakToday, low, d90), evening.filter { it.at.date == day(0) }.map { it.reminder.key })
    }

    @Test
    fun neverMoreThanIosKeeps_theSoonestFirst() {
        // 70 bags peaking over the 30 days: three a day for the first 10 days, two a day after
        val bags = (0 until 70).map { i -> peakBag("b$i", i % 30, createdAt = i.toLong()) }
        val crowded = ReminderData(emptyList(), bags, emptyList(), null)
        val planned = ReminderPlan.plan(crowded, on, emptySet(), now(0, 8, 0), zone)
        assertEquals(ReminderPlan.MAX_PENDING, planned.size)
        assertEquals(64, planned.size)
        assertEquals(planned.sortedBy { it.at }, planned)
        // 30 in the first ten days and 34 over the next seventeen: the plan ends on day 26, the rest waits for a later sync
        assertEquals(nine(26), planned.last().at)
        assertEquals(bags.filter { it.id.drop(1).toInt() % 30 <= 26 }.map { "peak:${it.id}:${day(it.id.drop(1).toInt() % 30)}" }.toSet(), planned.map { it.reminder.key }.toSet())
        assertEquals(64, planned.map { it.id }.toSet().size)
    }

    // ── syncing with the phone ──

    private val settings = SettingsRepository(MemorySettingsDao())
    private val prefs = ReminderPrefs(settings)
    private val scheduler = FakeReminderScheduler()
    private var loads = 0
    private val ahead = ReminderScheduleAhead(prefs, { loads++; data }, scheduler)

    private fun ours() = scheduler.waiting.keys.filter { it.startsWith(ReminderPlan.ID_PREFIX) }.map { it.removePrefix(ReminderPlan.ID_PREFIX) }

    @Test
    fun sync_schedulesThePlan_inPlaceOfOnlyItsOwnEarlierOnes() = runTest {
        prefs.setEnabled(true)
        scheduler.waiting[ReminderPlan.ID_PREFIX + "peak:gone:2026-10-01"] = null
        scheduler.waiting["brew-timer.done"] = null
        val planned = ahead.sync(now(0, 8, 30), zone)
        assertEquals(listOf(peakToday, low, d90, peakLater, d100), planned.map { it.reminder.key })
        assertEquals(listOf(peakToday, low, d90, peakLater, d100), ours())
        // another notification of the app is left alone
        assertTrue("brew-timer.done" in scheduler.waiting)
        assertEquals(planned.associate { it.reminder.key to it.at }, prefs.scheduled())
        // nothing is recorded as sent before its time
        assertTrue(prefs.sent().isEmpty())
        // syncing again changes nothing
        assertEquals(planned, ahead.sync(now(0, 8, 45), zone))
        assertEquals(listOf(peakToday, low, d90, peakLater, d100), ours())
        assertTrue(prefs.sent().isEmpty())
    }

    @Test
    fun whatThePhoneHasShown_isRecordedAsSent_andNeverPlannedAgain() = runTest {
        prefs.setEnabled(true)
        ahead.sync(now(0, 8, 30), zone)
        // the next morning: today's three went out at nine without the app
        val planned = ahead.sync(now(1, 10, 0), zone)
        assertEquals(mapOf(peakToday to day(0), low to day(0), d90 to day(0)), prefs.sent())
        // the window now starts the day after (ten o'clock is past nine) and reaches D-120
        assertEquals(listOf(peakLater to nine(8), d100 to nine(10), d120 to nine(30)), planned.map { it.reminder.key to it.at })
        assertEquals(listOf(peakLater, d100, d120), ours())
        assertEquals(planned.associate { it.reminder.key to it.at }, prefs.scheduled())
        // the daily check (Android) reads the same log: it would not send them either
        assertTrue(data.dueOn(day(0), ReminderKinds(), prefs.sent().keys).isEmpty())
    }

    @Test
    fun withoutPermission_nothingWaits_andItComesBackWhenAllowed() = runTest {
        prefs.setEnabled(true)
        ahead.sync(now(0, 8, 30), zone)
        scheduler.allowed = false
        assertTrue(ahead.sync(now(0, 8, 40), zone).isEmpty())
        assertTrue(ours().isEmpty())
        assertTrue(prefs.scheduled().isEmpty())
        assertTrue(prefs.sent().isEmpty())
        scheduler.allowed = true
        assertEquals(5, ahead.sync(now(0, 8, 50), zone).size)
        assertEquals(5, ours().size)
    }

    @Test
    fun turnedOff_recordsWhatWasShown_andWithdrawsTheRest() = runTest {
        prefs.setEnabled(true)
        ahead.sync(now(0, 8, 30), zone)
        prefs.setEnabled(false)
        ahead.clear(now(0, 12, 0), zone)
        assertEquals(setOf(peakToday, low, d90), prefs.sent().keys)
        assertTrue(ours().isEmpty())
        assertTrue(prefs.scheduled().isEmpty())
        // a sync while off does the same and reads no data
        loads = 0
        assertTrue(ahead.sync(now(0, 12, 5), zone).isEmpty())
        assertEquals(0, loads)
    }

    @Test
    fun aRefusedNotification_isNotRemembered_soItIsNeverTakenAsShown() = runTest {
        prefs.setEnabled(true)
        scheduler.waiting[ReminderPlan.ID_PREFIX + low] = null // from an earlier sync
        scheduler.refuse = { it.reminder.kind == ReminderKind.LOW_STOCK }
        val planned = ahead.sync(now(0, 8, 30), zone)
        assertEquals(listOf(peakToday, d90, peakLater, d100), planned.map { it.reminder.key })
        assertEquals(listOf(peakToday, d90, peakLater, d100), ours())
        ahead.sync(now(0, 10, 0), zone)
        assertEquals(setOf(peakToday, d90), prefs.sent().keys)
        // accepted again: planned for the next nine o'clock
        scheduler.refuse = { false }
        assertEquals(low to nine(1), ahead.sync(now(0, 10, 5), zone).first().let { it.reminder.key to it.at })
    }

    @Test
    fun aChangedRecord_movesOrWithdrawsWhatIsAhead() = runTest {
        prefs.setEnabled(true)
        var current = data
        val live = ReminderScheduleAhead(prefs, { current }, scheduler)
        assertTrue(low in live.sync(now(0, 8, 30), zone).map { it.reminder.key })
        // a record deleted before nine: the bag is no longer low, so its reminder is withdrawn
        current = ReminderData(brews.drop(3), pantry, emptyList(), ddayStart)
        assertTrue(low !in live.sync(now(0, 8, 40), zone).map { it.reminder.key })
        assertTrue(low !in ours())
        // a D-day start moved by a day moves the milestones with it
        current = ReminderData(brews, pantry, emptyList(), Dates.plusDays(ddayStart, 1))
        val moved = live.sync(now(0, 8, 50), zone).filter { it.reminder.kind == ReminderKind.DDAY }
        assertEquals(listOf(nine(1), nine(11)), moved.map { it.at })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun follow_syncsAtStart_afterChangesSettle_andBackInTheForeground() = runTest {
        prefs.setEnabled(true)
        val changes = MutableSharedFlow<Unit>()
        val wakeups = MutableSharedFlow<Unit>()
        backgroundScope.launch { ahead.follow(changes, wakeups) }
        runCurrent()
        changes.emit(Unit) // the first value: the app start
        advanceTimeBy(ReminderScheduleAhead.SETTLE_MS - 1)
        assertEquals(0, scheduler.canNotifyCalls)
        advanceTimeBy(2)
        assertEquals(1, scheduler.canNotifyCalls)
        // a burst of saves plans once
        repeat(3) { changes.emit(Unit); advanceTimeBy(100) }
        advanceTimeBy(ReminderScheduleAhead.SETTLE_MS)
        assertEquals(2, scheduler.canNotifyCalls)
        wakeups.emit(Unit)
        advanceTimeBy(ReminderScheduleAhead.SETTLE_MS + 1)
        assertEquals(3, scheduler.canNotifyCalls)
        // a sync that fails leaves the loop running
        scheduler.refuse = { error("the phone said no") }
        wakeups.emit(Unit)
        advanceTimeBy(ReminderScheduleAhead.SETTLE_MS + 1)
        assertEquals(4, scheduler.canNotifyCalls)
        scheduler.refuse = { false }
        wakeups.emit(Unit)
        advanceTimeBy(ReminderScheduleAhead.SETTLE_MS + 1)
        assertEquals(5, scheduler.canNotifyCalls)
    }
}
