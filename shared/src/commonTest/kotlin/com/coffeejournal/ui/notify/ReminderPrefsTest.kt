package com.coffeejournal.ui.notify

import com.coffeejournal.data.db.SettingEntity
import com.coffeejournal.data.db.SettingsDao
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.domain.rules.ReminderKinds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The settings table in memory. */
class MemorySettingsDao : SettingsDao {
    val rows = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observe(key: String): Flow<String?> = rows.map { it[key] }
    override suspend fun get(key: String): String? = rows.value[key]
    override suspend fun getAll(): List<SettingEntity> = rows.value.map { (k, v) -> SettingEntity(k, v) }
    override suspend fun put(entity: SettingEntity) { rows.value = rows.value + (entity.key to entity.value) }
    override suspend fun delete(key: String) { rows.value = rows.value - key }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

class ReminderPrefsTest {
    private val dao = MemorySettingsDao()
    private val settings = SettingsRepository(dao)
    private val prefs = ReminderPrefs(settings)

    @Test fun defaults_offEveryKindOnAtNine() = runTest {
        val s = prefs.load()
        assertFalse(s.enabled)
        assertEquals(ReminderKinds(peak = true, lowStock = true, dday = true), s.kinds)
        assertEquals(ReminderTime(9, 0), s.time)
        assertEquals(s, prefs.observe().first())
    }

    @Test fun savedValuesRoundTrip_underDeviceKeys() = runTest {
        prefs.setEnabled(true)
        prefs.setKind(ReminderKind.LOW_STOCK, false)
        prefs.setTime(ReminderTime(7, 30))
        val s = prefs.load()
        assertTrue(s.enabled)
        assertEquals(ReminderKinds(peak = true, lowStock = false, dday = true), s.kinds)
        assertEquals("07:30", s.time.toString())
        // every reminder key is a device key: a backup leaves them out
        assertTrue(dao.rows.value.keys.all { SettingsRepository.isDeviceKey(it) }, dao.rows.value.keys.toString())
        // a broken time falls back to the default
        settings.put(ReminderPrefs.KEY_TIME, "25:99")
        assertEquals(ReminderTime.DEFAULT, prefs.load().time)
    }

    @Test fun sentKeysAreRemembered_andOldOnesForgotten() = runTest {
        val day = LocalDate(2026, 9, 26)
        prefs.markSent(listOf("peak:p1:2026-09-26", "dday:2026-06-29:90"), day)
        prefs.markSent(listOf("low:pantry:p1"), Dates.plusDays(day, 2))
        assertEquals(setOf("peak:p1:2026-09-26", "dday:2026-06-29:90", "low:pantry:p1"), prefs.sent().keys)
        assertEquals(day, prefs.sent()["peak:p1:2026-09-26"])
        // past the keep window, the old entries go at the next write
        prefs.markSent(listOf("dday:2026-06-29:600"), Dates.plusDays(day, ReminderPrefs.KEEP_SENT_DAYS + 1))
        assertEquals(setOf("low:pantry:p1", "dday:2026-06-29:600"), prefs.sent().keys)
        // an unreadable value counts as nothing sent (it is rewritten at the next send)
        settings.put(ReminderPrefs.KEY_SENT, "{not json")
        assertTrue(prefs.sent().isEmpty())
    }

    @Test fun scheduledAheadRoundTrips_underADeviceKey() = runTest {
        assertTrue(prefs.scheduled().isEmpty())
        val plan = mapOf(
            "peak:p1:2026-09-26" to LocalDateTime(2026, 9, 26, 9, 0),
            "low:pantry:p1" to LocalDateTime(2026, 9, 27, 21, 30),
        )
        prefs.setScheduled(plan)
        assertEquals(plan, prefs.scheduled())
        assertTrue(SettingsRepository.isDeviceKey(ReminderPrefs.KEY_SCHEDULED))
        // an unchanged plan is not written again (a settings write would wake every observer of the table)
        val before = dao.rows.value
        prefs.setScheduled(plan.toList().reversed().toMap())
        assertTrue(before === dao.rows.value)
        prefs.setScheduled(emptyMap())
        assertTrue(prefs.scheduled().isEmpty())
        // unreadable values count as nothing scheduled
        settings.put(ReminderPrefs.KEY_SCHEDULED, """{"a":"not a time"}""")
        assertTrue(prefs.scheduled().isEmpty())
        settings.put(ReminderPrefs.KEY_SCHEDULED, "{not json")
        assertTrue(prefs.scheduled().isEmpty())
    }

    @Test fun reminderTime_parsesAndFindsTheNextRun() {
        assertEquals(ReminderTime(9, 0), ReminderTime.parse("09:00"))
        assertEquals(ReminderTime(7, 5), ReminderTime.parse("7:05"))
        assertNull(ReminderTime.parse("24:00"))
        assertNull(ReminderTime.parse("9시"))
        assertNull(ReminderTime.parse(null))
        val seoul = TimeZone.of("Asia/Seoul")
        val day = LocalDate(2026, 9, 26)
        val nine = ReminderTime(9, 0)
        val hour = 3_600_000L
        // before nine: later today; at or after nine: tomorrow
        assertEquals(hour / 2, nine.millisUntilNext(Dates.toMillis(day, 8, 30, seoul), seoul))
        assertEquals(24 * hour, nine.millisUntilNext(Dates.toMillis(day, 9, 0, seoul), seoul))
        assertEquals(23 * hour, nine.millisUntilNext(Dates.toMillis(day, 10, 0, seoul), seoul))
        // a minute past midnight, from late evening
        assertEquals(hour + 60_000L, ReminderTime(0, 1).millisUntilNext(Dates.toMillis(day, 23, 0, seoul), seoul))
    }
}
