package com.coffeejournal.ui.notify

import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSCalendarIdentifierGregorian
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iOS glue around the shared schedule-ahead, without UNUserNotificationCenter (a test run has no app for it): the
 * trigger's date and time, and the platform's calls into [ReminderScheduleAhead]. Runs on the macOS CI's simulator.
 */
class IosReminderPlatformTest {
    @Test
    fun theTrigger_isTheLocalGregorianDateAndTime() {
        val c = triggerComponents(LocalDateTime(2026, 10, 6, 21, 30))
        assertEquals(2026L, c.year)
        assertEquals(10L, c.month)
        assertEquals(6L, c.day)
        assertEquals(21L, c.hour)
        assertEquals(30L, c.minute)
        assertEquals(NSCalendarIdentifierGregorian, c.calendar?.calendarIdentifier)
        // no zone: the phone's own, wherever it is when the time comes
        assertNull(c.timeZone)
    }

    @Test
    fun scheduleAndCancel_goThroughTheSharedSync_andCanNotifyIsTheCachedAnswer() = runTest {
        val prefs = ReminderPrefs(SettingsRepository(MemorySettingsDao()))
        prefs.setEnabled(true)
        val peakDay = Dates.plusDays(Dates.today(), 2)
        val bag = PantryItem(id = "p1", name = "케냐 AA", createdAt = 1L, peakStart = Dates.isoDate(peakDay))
        val scheduler = FakeReminderScheduler()
        val platform = IosReminderPlatform(IosNotifications(), ReminderScheduleAhead(prefs, { ReminderData(emptyList(), listOf(bag), emptyList(), null) }, scheduler))
        // nothing read yet: not allowed, so turning reminders on asks first
        assertFalse(platform.canNotify())
        assertFalse(platform.canNotifyChanges().first())
        platform.schedule(ReminderTime.DEFAULT)
        assertEquals(listOf(ReminderPlan.ID_PREFIX + "peak:p1:$peakDay"), scheduler.waiting.keys.toList())
        assertEquals(setOf("peak:p1:$peakDay"), prefs.scheduled().keys)
        prefs.setEnabled(false)
        platform.cancel()
        assertTrue(scheduler.waiting.isEmpty())
        assertTrue(prefs.scheduled().isEmpty())
    }
}
