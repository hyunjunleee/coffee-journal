package com.coffeejournal.android

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.ui.notify.ReminderPrefs
import com.coffeejournal.ui.notify.ReminderTexts
import com.coffeejournal.ui.notify.ReminderTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 알림 설정 (feature plan v2 §3), driven through the real app: reached from the bottom of the 기타 tab; turning
 * reminders on asks for the Android 13+ notification permission first, a refusal keeps the switch off with a hint,
 * and a new time reschedules the daily check.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ReminderSettingsFlowTest : CoverageFlowBase() {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = koinGet<ReminderPrefs>()
    private val master = hasText("알림 받기") and isToggleable()

    @Before
    fun setUpWork() {
        ReminderFixtures.initWorkManager(context)
        ReminderFixtures.denyNotifications(app)
    }

    private fun openSettings() {
        launchApp()
        tab("tab-misc")
        waitFor(hasTestTag("misc-list"))
        node(hasTestTag("misc-list")).performScrollToNode(button("알림 설정 →"))
        settle(1)
        // next to the existing credits link
        assertTrue(has(button("출처 · 오픈소스 라이선스 →")))
        tap(button("알림 설정 →"))
        waitForText(ReminderTexts.INTRO)
    }

    private fun switchState(title: String) = toggleState(hasText(title) and isToggleable())

    /** Answers the pending permission prompt as the system would. */
    private fun answerPermission(granted: Boolean) {
        val request = shadowOf(compose.activity).lastRequestedPermission
        assertTrue("the prompt asks for POST_NOTIFICATIONS", request.requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS))
        if (granted) ReminderFixtures.grantNotifications(app) else ReminderFixtures.denyNotifications(app)
        val result = if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            compose.activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(result))
        }
        settle()
    }

    @Test
    fun turningOn_asksForThePermission_andSchedulesTheDailyCheckWhenGranted() {
        openSettings()
        assertEquals(ToggleableState.Off, switchState("알림 받기"))
        // every kind starts on, at 09:00
        assertEquals(ToggleableState.On, switchState("피크 시작"))
        assertEquals(ToggleableState.On, switchState("원두 소진 임박"))
        assertEquals(ToggleableState.On, switchState("D-day 마일스톤"))
        assertTrue(has(hasText("09:00") and hasClickAction()))

        tap(master)
        answerPermission(granted = true)
        waitUntil("reminders on") { switchState("알림 받기") == ToggleableState.On }
        waitForText("매일 09:00쯤 확인해요.")
        assertTrue(runBlocking { prefs.load() }.enabled)
        waitUntil("daily check scheduled") { ReminderFixtures.scheduledTime(context) == "09:00" }

        // off again: the job goes
        tap(master)
        waitUntil("reminders off") { switchState("알림 받기") == ToggleableState.Off }
        waitUntil("daily check cancelled") { ReminderFixtures.scheduledTime(context) == null }
        assertFalse(runBlocking { prefs.load() }.enabled)
    }

    @Test
    fun aRefusedPermission_keepsTheSwitchOff_withAHintAndALinkToTheSettings() {
        openSettings()
        tap(master)
        answerPermission(granted = false)
        waitForText(ReminderTexts.REFUSED)
        assertEquals(ToggleableState.Off, switchState("알림 받기"))
        assertFalse(runBlocking { prefs.load() }.enabled)
        assertNull(ReminderFixtures.scheduledTime(context))

        // the link opens this app's notification settings
        drainStartedActivities()
        tap(button(ReminderTexts.OPEN_SETTINGS))
        val opened = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.action)
        assertEquals(app.packageName, opened.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    /** Drops activities started earlier in the test, so the next one read is the settings page. */
    private fun drainStartedActivities() {
        while (shadowOf(app).nextStartedActivity != null) Unit
    }

    @Test
    fun alreadyAllowed_turnsOnWithoutAPrompt() {
        ReminderFixtures.grantNotifications(app)
        openSettings()
        tap(master)
        waitUntil("reminders on") { switchState("알림 받기") == ToggleableState.On }
        assertNull("no prompt", shadowOf(compose.activity).lastRequestedPermission)
        waitUntil("daily check scheduled") { ReminderFixtures.scheduledTime(context) == "09:00" }
    }

    @Test
    fun permissionTakenAwayLater_showsWhyNothingArrives() {
        ReminderFixtures.grantNotifications(app)
        runBlocking { prefs.setEnabled(true) }
        ReminderFixtures.denyNotifications(app)
        openSettings()
        assertEquals(ToggleableState.On, switchState("알림 받기"))
        waitForText(ReminderTexts.BLOCKED)
    }

    @Test
    fun kindSwitches_areSaved() {
        openSettings()
        tap(hasText("원두 소진 임박") and isToggleable())
        waitUntil("low stock off") { switchState("원두 소진 임박") == ToggleableState.Off }
        tap(hasText("D-day 마일스톤") and isToggleable())
        waitUntil("dday off") { switchState("D-day 마일스톤") == ToggleableState.Off }
        val kinds = runBlocking { prefs.load() }.kinds
        assertTrue(kinds.peak)
        assertFalse(kinds.lowStock)
        assertFalse(kinds.dday)
        assertFalse(kinds.allows(ReminderKind.LOW_STOCK))
    }

    @Test
    fun aNewTime_reschedulesTheDailyCheck() {
        ReminderFixtures.grantNotifications(app)
        openSettings()
        tap(master)
        waitUntil("daily check at 09:00") { ReminderFixtures.scheduledTime(context) == "09:00" }

        tap(hasText("09:00") and hasClickAction())
        waitFor(isDialog())
        // the 24-hour clock face: pick 7 on the hour ring (the minutes stay :00)
        clickNode(hasContentDescription("7 hours") and hasClickAction())
        clickNode(dialogButton("확인"))
        waitGone(isDialog())
        waitForText("07:00")
        assertEquals(ReminderTime(7, 0), runBlocking { prefs.load() }.time)
        waitUntil("rescheduled at 07:00") { ReminderFixtures.scheduledTime(context) == "07:00" }
        waitForText("매일 07:00쯤 확인해요.")
    }

    @Test
    fun aTimeChosenWhileOff_isOnlySaved() {
        openSettings()
        tap(hasText("09:00") and hasClickAction())
        waitFor(isDialog())
        clickNode(hasContentDescription("21 hours") and hasClickAction())
        clickNode(dialogButton("확인"))
        waitForText("21:00")
        assertEquals(ReminderTime(21, 0), runBlocking { prefs.load() }.time)
        assertNull(ReminderFixtures.scheduledTime(context))
    }
}
