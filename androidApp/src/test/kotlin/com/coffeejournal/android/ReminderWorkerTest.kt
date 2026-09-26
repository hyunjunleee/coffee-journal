package com.coffeejournal.android

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.coffeejournal.android.ReminderFixtures.LOW_BAG
import com.coffeejournal.android.ReminderFixtures.PEAK_BAG
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.ui.nav.LaunchTarget
import com.coffeejournal.ui.notify.AndroidReminderPlatform
import com.coffeejournal.ui.notify.ReminderNotifier
import com.coffeejournal.ui.notify.ReminderPlatform
import com.coffeejournal.ui.notify.ReminderPrefs
import com.coffeejournal.ui.notify.ReminderSchedule
import com.coffeejournal.ui.notify.ReminderTime
import com.coffeejournal.ui.notify.ReminderWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The daily reminder check on Android (feature plan v2 §3): the WorkManager job, the notifications it posts (Korean
 * channels, the screen a tap opens), and that it follows the settings, the permission and what was already sent.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class ReminderWorkerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val prefs get() = GlobalContext.get().get<ReminderPrefs>()
    private val platform get() = GlobalContext.get().get<ReminderPlatform>()

    @Before
    fun setUp() {
        startTestKoin(context)
        ReminderFixtures.initWorkManager(context)
        ReminderFixtures.grantNotifications(context)
    }

    @After
    fun tearDown() = stopKoin()

    private fun runWorker(): ListenableWorker.Result = runBlocking { TestListenableWorkerBuilder<ReminderWorker>(context).build().doWork() }

    private fun enable() = runBlocking { prefs.setEnabled(true) }

    @Test
    fun postsEachDueReminder_onItsKoreanChannel_andATapOpensTheRightScreen() {
        ReminderFixtures.seedDueToday()
        enable()
        assertEquals(ListenableWorker.Result.success(), runWorker())

        val posted = ReminderFixtures.notifications(context)
        assertEquals(
            listOf("Coffee D-30 · 30일 기념", "원두 소진 임박 · $LOW_BAG", "피크 시작 · $PEAK_BAG"),
            ReminderFixtures.titles(context),
        )
        val byTitle = posted.associateBy { it.extras.getString(android.app.Notification.EXTRA_TITLE) }
        val low = byTitle.getValue("원두 소진 임박 · $LOW_BAG")
        assertEquals("잔여량 24g/200g · 평소 16g 기준 1잔 남았어요.", low.extras.getString(android.app.Notification.EXTRA_TEXT))
        assertEquals(ReminderNotifier.Channel.LOW_STOCK.id, low.channelId)
        assertEquals(LaunchTarget.PANTRY, ReminderFixtures.target(low))
        assertEquals(LaunchTarget.PANTRY, ReminderFixtures.target(byTitle.getValue("피크 시작 · $PEAK_BAG")))
        assertEquals(LaunchTarget.HOME, ReminderFixtures.target(byTitle.getValue("Coffee D-30 · 30일 기념")))
        assertTrue(posted.all { it.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0 })

        // the channels carry Korean names and descriptions
        val channels = context.getSystemService(NotificationManager::class.java).notificationChannels.associateBy { it.id }
        assertEquals("피크 시작", channels.getValue(ReminderNotifier.Channel.PEAK.id).name)
        assertEquals("원두 소진 임박", channels.getValue(ReminderNotifier.Channel.LOW_STOCK.id).name)
        assertEquals("D-day 마일스톤", channels.getValue(ReminderNotifier.Channel.DDAY.id).name)
        assertEquals("마시는 중인 원두가 평소 원두량으로 2잔 이하 남았을 때", channels.getValue(ReminderNotifier.Channel.LOW_STOCK.id).description)
    }

    @Test
    fun aReminderIsSentOnce_evenWhenTheCheckRunsAgain() {
        ReminderFixtures.seedDueToday()
        enable()
        runWorker()
        assertEquals(3, ReminderFixtures.notifications(context).size)
        val sent = runBlocking { prefs.sent() }
        assertEquals(setOf(Dates.today()), sent.values.toSet())
        assertEquals(3, sent.size)

        ReminderFixtures.clearNotifications(context)
        runWorker()
        assertTrue("nothing is posted twice", ReminderFixtures.notifications(context).isEmpty())
    }

    @Test
    fun followsTheSettings_masterSwitchAndKinds() {
        ReminderFixtures.seedDueToday()
        // off by default: nothing, and nothing is remembered as sent
        runWorker()
        assertTrue(ReminderFixtures.notifications(context).isEmpty())
        assertTrue(runBlocking { prefs.sent() }.isEmpty())

        enable()
        runBlocking {
            prefs.setKind(ReminderKind.PEAK, false)
            prefs.setKind(ReminderKind.DDAY, false)
        }
        runWorker()
        assertEquals(listOf("원두 소진 임박 · $LOW_BAG"), ReminderFixtures.titles(context))

        // switched back on later the same day: the others still come, the low-stock one does not repeat
        ReminderFixtures.clearNotifications(context)
        runBlocking {
            prefs.setKind(ReminderKind.PEAK, true)
            prefs.setKind(ReminderKind.DDAY, true)
        }
        runWorker()
        assertEquals(listOf("Coffee D-30 · 30일 기념", "피크 시작 · $PEAK_BAG"), ReminderFixtures.titles(context))
    }

    @Test
    fun withoutThePermission_nothingIsPosted_andTheRemindersStayDue() {
        ReminderFixtures.seedDueToday()
        enable()
        ReminderFixtures.denyNotifications(context)
        assertFalse(platform.canNotify())
        runWorker()
        assertTrue(ReminderFixtures.notifications(context).isEmpty())
        assertTrue("not marked as sent", runBlocking { prefs.sent() }.isEmpty())

        // allowed again later that day: they go out
        ReminderFixtures.grantNotifications(context)
        runWorker()
        assertEquals(3, ReminderFixtures.notifications(context).size)
    }

    @Test
    fun aChannelSilencedInThePhoneSettings_isSkipped() {
        ReminderFixtures.seedDueToday()
        enable()
        ReminderNotifier(context).ensureChannels()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(ReminderNotifier.Channel.DDAY.id)
        manager.createNotificationChannel(
            android.app.NotificationChannel(ReminderNotifier.Channel.DDAY.id, "D-day 마일스톤", NotificationManager.IMPORTANCE_NONE)
        )
        runWorker()
        assertEquals(listOf("원두 소진 임박 · $LOW_BAG", "피크 시작 · $PEAK_BAG"), ReminderFixtures.titles(context))
    }

    @Test
    fun schedule_isOneDailyJob_keptForTheSameTime_replacedForANewOne() = runBlocking {
        platform.schedule(ReminderTime(9, 0))
        val first = ReminderFixtures.activeReminderWork(context).single()
        assertEquals("09:00", ReminderFixtures.scheduledTime(context))
        assertEquals(24 * 3_600_000L, first.periodicityInfo!!.repeatIntervalMillis)
        // the first run is at the next 09:00
        val expected = Dates.nowMillis() + ReminderTime(9, 0).millisUntilNext(Dates.nowMillis())
        assertTrue("next run ${first.nextScheduleTimeMillis} vs $expected", kotlin.math.abs(first.nextScheduleTimeMillis - expected) < 60_000L)

        // idempotent: the same time keeps the same job
        platform.schedule(ReminderTime(9, 0))
        assertEquals(first.id, ReminderFixtures.activeReminderWork(context).single().id)

        // a new time replaces it
        platform.schedule(ReminderTime(7, 30))
        val second = ReminderFixtures.activeReminderWork(context).single()
        assertNotEquals(first.id, second.id)
        assertEquals("07:30", ReminderFixtures.scheduledTime(context))
        // the old job is gone: cancelled, or already pruned by the replacement
        val old = androidx.work.WorkManager.getInstance(context).getWorkInfoById(first.id).get()
        assertTrue("old job: ${old?.state}", old == null || old.state == WorkInfo.State.CANCELLED)

        platform.cancel()
        assertTrue(ReminderFixtures.activeReminderWork(context).isEmpty())
    }

    @Test
    fun theScheduledJob_runsTheCheckWhenItsTimeComes() {
        ReminderFixtures.seedDueToday()
        enable()
        runBlocking { platform.schedule(ReminderTime(9, 0)) }
        val id = ReminderFixtures.activeReminderWork(context).single().id
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(id)
        val deadline = System.currentTimeMillis() + 10_000
        while (ReminderFixtures.notifications(context).size < 3 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(3, ReminderFixtures.notifications(context).size)
        // periodic: it stays scheduled for tomorrow
        assertEquals("09:00", ReminderFixtures.scheduledTime(context))
    }

    @Test
    fun appStartSync_followsTheSavedSwitch() = runBlocking {
        ReminderSchedule.sync(prefs, platform)
        assertNull("off: nothing scheduled", ReminderFixtures.scheduledTime(context))
        prefs.setEnabled(true)
        prefs.setTime(ReminderTime(6, 45))
        ReminderSchedule.sync(prefs, platform)
        assertEquals("06:45", ReminderFixtures.scheduledTime(context))
        prefs.setEnabled(false)
        ReminderSchedule.sync(prefs, platform)
        assertNull(ReminderFixtures.scheduledTime(context))
    }

    @Test
    @Config(sdk = [32])
    fun beforeAndroid13_noRuntimePermission_butTheAppSwitchCounts() {
        ReminderFixtures.denyNotifications(context)
        assertTrue("no POST_NOTIFICATIONS before API 33", platform.canNotify())
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        assertFalse(platform.canNotify())
    }

    @Test
    fun platformIsTheAndroidOne() {
        assertTrue(platform is AndroidReminderPlatform)
    }
}
