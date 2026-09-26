package com.coffeejournal.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.backup.BackupCodec
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.data.backup.ImportMode
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.notify.ReminderPrefs
import com.coffeejournal.ui.notify.ReminderTime
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reminder settings belong to the phone, not the journal: the settings export carries every other key but none of
 * the device keys, a file that has them (hand-edited, or from a later version) cannot switch reminders on, and 교체
 * keeps this phone's own.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ReminderBackupTest : FlowTestBase() {
    private val service get() = koinGet<BackupService>()
    private val settings get() = koinGet<SettingsRepository>()
    private val prefs get() = koinGet<ReminderPrefs>()

    private fun seed() = runBlocking {
        settings.setDdayStart(LocalDate(2026, 1, 1))
        settings.put("some-web-setting", "kept")
        prefs.setEnabled(true)
        prefs.setTime(ReminderTime(7, 30))
        prefs.markSent(listOf("peak:p1:2026-09-26"), LocalDate(2026, 9, 26))
    }

    @Test
    fun export_leavesTheDeviceKeysOut() = runBlocking {
        seed()
        val snap = service.snapshot()
        assertEquals(mapOf(SettingsRepository.KEY_DDAY_START to "2026-01-01", "some-web-setting" to "kept"), snap.settings)
        val json = service.export().json
        assertFalse(json.contains(SettingsRepository.DEVICE_PREFIX))
        assertTrue(json.contains("some-web-setting"))
    }

    @Test
    fun import_ignoresDeviceKeys_andReplaceKeepsThisPhonesOwn() = runBlocking {
        seed()
        val codec = BackupCodec()
        val file = """{"data":{"settings":{"brew-start-date":"2025-12-01","${ReminderPrefs.KEY_ENABLED}":"false","${ReminderPrefs.KEY_TIME}":"05:00","${ReminderPrefs.KEY_SENT}":"{}"}}}"""
        val merged = service.import(codec.decode(file), ImportMode.MERGE)
        assertTrue(merged.lines.map { it.text() }.toString(), merged.allOk)
        assertTrue(merged.lines.map { it.text() }.toString(), merged.lines.any { it.text() == "settings: ✓ 1개 복원됨" })
        assertEquals("2025-12-01", settings.get(SettingsRepository.KEY_DDAY_START))
        val after = prefs.load()
        assertTrue("reminders stay on", after.enabled)
        assertEquals(ReminderTime(7, 30), after.time)
        assertEquals(setOf("peak:p1:2026-09-26"), prefs.sent().keys)

        // 교체 swaps the journal's settings for the file's, but this phone's reminder settings stay
        service.import(codec.decode(file), ImportMode.REPLACE)
        assertEquals(null, settings.get("some-web-setting"))
        assertTrue(prefs.load().enabled)
        assertEquals(ReminderTime(7, 30), prefs.load().time)
        assertEquals(Dates.parseIsoDate("2026-09-26"), prefs.sent()["peak:p1:2026-09-26"])
    }
}
