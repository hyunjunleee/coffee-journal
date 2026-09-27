package com.coffeejournal.ui.notify

import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Reminder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime

/** One reminder handed to the phone ahead of time, to be shown at [at] (the phone's local date and time). */
data class PlannedReminder(val reminder: Reminder, val at: LocalDateTime) {
    /** The notification's identifier: [ReminderPlan.ID_PREFIX] and the reminder's key, one per event. */
    val id: String get() = ReminderPlan.ID_PREFIX + reminder.key
}

/**
 * Schedule-ahead, for a phone that runs no daily check in the background (iOS): the reminders the daily check would
 * send at the chosen time on each of the next [DAYS] days, worked out now from the current data with the same rules
 * ([ReminderData.dueOn]), each on the first of those days it would go out.
 *
 * Peaks and D-day milestones are fixed dates. 원두 소진 임박 follows the records: the grams left are what the logged
 * brews leave, so it is planned for the next reminder time once the records bring a bag to two cups or fewer, and
 * each sync plans it again from the records as they are then.
 */
object ReminderPlan {
    /** How many daily reminder times ahead are planned. */
    const val DAYS = 30

    /** iOS keeps only the 64 soonest notifications an app has waiting; later ones would be dropped silently. */
    const val MAX_PENDING = 64

    /** Marks this app's scheduled reminders among the phone's waiting notifications. */
    const val ID_PREFIX = "coffee-journal.reminder."

    /**
     * The reminders for the [days] reminder times from [nowMillis] on (today's time while it is still ahead, as
     * [ReminderTime.millisUntilNext] finds it for the Android check), soonest first, at most [limit]. Nothing while
     * reminders are off; a key in [sent] is never planned, and each key is planned once, on its first day.
     */
    fun plan(
        data: ReminderData,
        settings: ReminderSettings,
        sent: Set<String>,
        nowMillis: Long,
        zone: TimeZone = Dates.systemZone,
        days: Int = DAYS,
        limit: Int = MAX_PENDING,
    ): List<PlannedReminder> {
        if (!settings.enabled || days <= 0 || limit <= 0) return emptyList()
        val time = settings.time
        val first = Dates.toLocalDate(nowMillis + time.millisUntilNext(nowMillis, zone), zone)
        val planned = mutableListOf<PlannedReminder>()
        val taken = sent.toMutableSet()
        for (i in 0 until days) {
            val day = Dates.plusDays(first, i)
            for (reminder in data.dueOn(day, settings.kinds, taken)) {
                planned += PlannedReminder(reminder, day.atTime(time.hour, time.minute))
                taken += reminder.key
                if (planned.size == limit) return planned
            }
        }
        return planned
    }
}

/**
 * The phone's store of notifications to show later, as schedule-ahead uses it (iOS: UNUserNotificationCenter). Kept
 * this thin so everything around it runs in the common tests.
 */
interface ReminderScheduler {
    /** Notifications from this app can be shown now. Also refreshes what [ReminderPlatform.canNotify] answers. */
    suspend fun canNotify(): Boolean

    /** Identifiers of this app's notifications still waiting to be shown. */
    suspend fun pending(): List<String>

    /** Withdraws the waiting notifications [ids]. */
    suspend fun remove(ids: List<String>)

    /** Schedules [planned] for its time, replacing a waiting one with the same id; false when the phone refused it. */
    suspend fun add(planned: PlannedReminder): Boolean
}

/**
 * Keeps the phone's scheduled reminders in step with the journal and the settings ([sync]), and remembers them in
 * [ReminderPrefs.scheduled]. The phone shows them without the app running, so a sync first records every reminder
 * whose time has passed as sent, in the same log the daily check keeps ([ReminderPrefs.markSent]): it is not planned
 * again. What is still ahead is planned again from the current data each time, so a changed record, bag or setting
 * moves or withdraws it.
 */
class ReminderScheduleAhead(
    private val prefs: ReminderPrefs,
    private val data: suspend () -> ReminderData,
    private val scheduler: ReminderScheduler,
    private val days: Int = ReminderPlan.DAYS,
    private val limit: Int = ReminderPlan.MAX_PENDING,
) {
    private val lock = Mutex()

    /**
     * Records what was shown, then schedules [ReminderPlan.plan] in place of this app's earlier reminders. Nothing is
     * scheduled while reminders are off or notifications cannot be shown. Returns what is scheduled now.
     */
    suspend fun sync(nowMillis: Long = Dates.nowMillis(), zone: TimeZone = Dates.systemZone): List<PlannedReminder> = lock.withLock {
        markShown(nowMillis, zone)
        val canNotify = scheduler.canNotify()
        val settings = prefs.load()
        val plan = if (settings.enabled && canNotify) {
            ReminderPlan.plan(data(), settings, prefs.sent().keys, nowMillis, zone, days, limit)
        } else {
            emptyList()
        }
        replaceWith(plan)
    }

    /** Records what was shown and withdraws the rest (reminders turned off). */
    suspend fun clear(nowMillis: Long = Dates.nowMillis(), zone: TimeZone = Dates.systemZone) {
        lock.withLock {
            markShown(nowMillis, zone)
            replaceWith(emptyList())
        }
    }

    /**
     * Syncs once [changes] and [wakeups] have been quiet for [SETTLE_MS]: [changes] emits at first (the app start) and
     * after every change to what a plan reads (ReminderCheck.changes), [wakeups] when the app is back in the
     * foreground. A sync that fails is tried again at the next one.
     */
    @OptIn(FlowPreview::class)
    suspend fun follow(changes: Flow<Unit>, wakeups: Flow<Unit>) {
        merge(changes, wakeups)
            .debounce(SETTLE_MS)
            .collect {
                try {
                    sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // the reminders already scheduled stay; the next change or return to the app plans again
                }
            }
    }

    /** Every scheduled reminder whose time has passed was shown by the phone: sent on its day. */
    private suspend fun markShown(nowMillis: Long, zone: TimeZone) {
        prefs.scheduled()
            .filterValues { Dates.toMillis(it, zone) <= nowMillis }
            .entries
            .groupBy({ it.value.date }, { it.key })
            .forEach { (day, keys) -> prefs.markSent(keys, day) }
    }

    private suspend fun replaceWith(plan: List<PlannedReminder>): List<PlannedReminder> {
        val ids = plan.mapTo(HashSet()) { it.id }
        val stale = scheduler.pending().filter { it.startsWith(ReminderPlan.ID_PREFIX) && it !in ids }
        if (stale.isNotEmpty()) scheduler.remove(stale)
        val (added, refused) = plan.partition { scheduler.add(it) }
        // a refused one may still wait under its id from an earlier sync; it must not show unrecorded
        if (refused.isNotEmpty()) scheduler.remove(refused.map { it.id })
        prefs.setScheduled(added.associate { it.reminder.key to it.at })
        return added
    }

    companion object {
        /** A burst of writes (a restore, a record saved with its bag) plans once. */
        const val SETTLE_MS = 800L
    }
}
