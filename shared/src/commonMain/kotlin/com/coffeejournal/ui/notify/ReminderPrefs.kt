package com.coffeejournal.ui.notify

import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.domain.rules.ReminderKinds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Local time of the daily check ("09:00" by default). */
data class ReminderTime(val hour: Int, val minute: Int = 0) {
    init {
        require(hour in 0..23 && minute in 0..59) { "not a time of day: $hour:$minute" }
    }

    override fun toString(): String = "${Dates.pad2(hour)}:${Dates.pad2(minute)}"

    /** Milliseconds from [nowMillis] to the next [hour]:[minute] in [zone]: later today, or tomorrow once it has passed. */
    fun millisUntilNext(nowMillis: Long, zone: TimeZone = Dates.systemZone): Long {
        val today = Dates.toLocalDate(nowMillis, zone)
        val todayAt = Dates.toMillis(today, hour, minute, zone)
        val next = if (todayAt > nowMillis) todayAt else Dates.toMillis(Dates.plusDays(today, 1), hour, minute, zone)
        return next - nowMillis
    }

    companion object {
        val DEFAULT = ReminderTime(9, 0)

        /** "HH:MM"; null for anything else. */
        fun parse(text: String?): ReminderTime? {
            val m = Regex("""^(\d{1,2}):(\d{2})$""").find(text?.trim() ?: return null) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            return if (h in 0..23 && min in 0..59) ReminderTime(h, min) else null
        }
    }
}

/** 알림 설정: the master switch (off until the user turns it on), the kinds and the time of the daily check. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val kinds: ReminderKinds = ReminderKinds(),
    val time: ReminderTime = ReminderTime.DEFAULT,
)

/**
 * The reminder settings, the reminders already sent and (iOS) those scheduled ahead, kept in [SettingsRepository]
 * under device keys ([SettingsRepository.DEVICE_PREFIX]): they belong to this phone, so a backup neither carries nor
 * restores them.
 */
class ReminderPrefs(private val settings: SettingsRepository) {
    private val sentLock = Mutex()

    fun observe(): Flow<ReminderSettings> = combine(
        settings.observe(KEY_ENABLED),
        settings.observe(KEY_PEAK),
        settings.observe(KEY_LOW_STOCK),
        settings.observe(KEY_DDAY),
        settings.observe(KEY_TIME),
    ) { enabled, peak, low, dday, time -> decode(enabled, peak, low, dday, time) }

    suspend fun load(): ReminderSettings = decode(
        settings.get(KEY_ENABLED), settings.get(KEY_PEAK), settings.get(KEY_LOW_STOCK), settings.get(KEY_DDAY), settings.get(KEY_TIME),
    )

    suspend fun setEnabled(on: Boolean) = settings.put(KEY_ENABLED, on.toString())

    suspend fun setKind(kind: ReminderKind, on: Boolean) = settings.put(kindKey(kind), on.toString())

    suspend fun setTime(time: ReminderTime) = settings.put(KEY_TIME, time.toString())

    /** Keys of the reminders already sent, with the day each was sent. */
    suspend fun sent(): Map<String, LocalDate> = decodeSent(settings.get(KEY_SENT))

    /** Remembers [keys] as sent [today] and forgets entries older than [KEEP_SENT_DAYS] (their events are long past). */
    suspend fun markSent(keys: Collection<String>, today: LocalDate) = sentLock.withLock {
        val oldest = Dates.plusDays(today, -KEEP_SENT_DAYS)
        val merged = sent().filterValues { it >= oldest } + keys.associateWith { today }
        settings.put(KEY_SENT, json.encodeToString(stringMapSerializer, merged.mapValues { Dates.isoDate(it.value) }))
    }

    /**
     * Schedule-ahead (iOS): the reminders handed to the phone to show later, by key, with the local date and time each
     * is set for. Once that time has passed the phone has shown it, so the next sync records it as sent.
     */
    suspend fun scheduled(): Map<String, LocalDateTime> {
        val raw = settings.get(KEY_SCHEDULED)
        if (raw.isNullOrBlank()) return emptyMap()
        val map = runCatching { json.decodeFromString(stringMapSerializer, raw) }.getOrNull() ?: return emptyMap()
        return map.mapNotNull { (k, v) -> runCatching { LocalDateTime.parse(v) }.getOrNull()?.let { k to it } }.toMap()
    }

    /** Replaces [scheduled]; an unchanged plan is not written again. */
    suspend fun setScheduled(plan: Map<String, LocalDateTime>) {
        if (plan == scheduled()) return
        settings.put(KEY_SCHEDULED, json.encodeToString(stringMapSerializer, plan.mapValues { it.value.toString() }))
    }

    private fun decode(enabled: String?, peak: String?, low: String?, dday: String?, time: String?) = ReminderSettings(
        enabled = enabled == "true",
        kinds = ReminderKinds(peak = peak != "false", lowStock = low != "false", dday = dday != "false"),
        time = ReminderTime.parse(time) ?: ReminderTime.DEFAULT,
    )

    private fun decodeSent(raw: String?): Map<String, LocalDate> {
        if (raw.isNullOrBlank()) return emptyMap()
        val map = runCatching { json.decodeFromString(stringMapSerializer, raw) }.getOrNull() ?: return emptyMap()
        return map.mapNotNull { (k, v) -> Dates.parseIsoDate(v)?.let { k to it } }.toMap()
    }

    companion object {
        const val KEY_ENABLED = SettingsRepository.DEVICE_PREFIX + "reminders.enabled"
        const val KEY_PEAK = SettingsRepository.DEVICE_PREFIX + "reminders.peak"
        const val KEY_LOW_STOCK = SettingsRepository.DEVICE_PREFIX + "reminders.lowStock"
        const val KEY_DDAY = SettingsRepository.DEVICE_PREFIX + "reminders.dday"
        const val KEY_TIME = SettingsRepository.DEVICE_PREFIX + "reminders.time"
        const val KEY_SENT = SettingsRepository.DEVICE_PREFIX + "reminders.sent"
        const val KEY_SCHEDULED = SettingsRepository.DEVICE_PREFIX + "reminders.scheduled"

        /** A year and a bit: a peak or a milestone is never due again once its day has passed. */
        const val KEEP_SENT_DAYS = 400

        fun kindKey(kind: ReminderKind): String = when (kind) {
            ReminderKind.PEAK -> KEY_PEAK
            ReminderKind.LOW_STOCK -> KEY_LOW_STOCK
            ReminderKind.DDAY -> KEY_DDAY
        }

        private val json = Json { ignoreUnknownKeys = true }
        private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
