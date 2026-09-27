package com.coffeejournal.ui.notify

import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.BeanStock
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Reminder
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.domain.rules.ReminderKinds
import com.coffeejournal.domain.rules.Reminders
import com.coffeejournal.ui.extract.DrinkingState
import com.coffeejournal.ui.extract.ExtractGrouping
import com.coffeejournal.ui.nav.LaunchTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

/** Inputs the reminder rules take from the home tab, derived exactly as the home tab derives them. */
object ReminderInputs {
    /** The beans the 마시는 중 card shows ([ExtractGrouping.drinking]) with the grams it says are left. */
    fun inUse(entries: List<Entry>, pantry: List<PantryItem>, blends: List<Blend>, today: LocalDate): List<BeanStock> =
        when (val drinking = ExtractGrouping.drinking(entries, pantry, blends, emptyList(), today)) {
            DrinkingState.None -> emptyList()
            is DrinkingState.OpenedBags -> drinking.cards.map { card ->
                BeanStock(
                    id = "pantry:${card.item.id}",
                    beanKey = BeanNames.coreBeanName(card.item.name),
                    name = card.item.name.trim().ifBlank { "이름 없음" },
                    remainingGrams = card.remainingGrams,
                    bagGrams = card.bagGrams,
                    remainingLine = card.remainingLine,
                )
            }
            is DrinkingState.RecentBean -> {
                val card = drinking.card
                val key = BeanNames.coreBeanName(card.entry.name)
                listOf(
                    BeanStock(
                        id = "bean:$key",
                        beanKey = key,
                        name = BeanNames.displayName(card.entry.name),
                        remainingGrams = card.remainingGrams,
                        bagGrams = card.bagGrams,
                        remainingLine = card.remainingLine,
                    )
                )
            }
        }
}

/** Where tapping a reminder's notification opens the app: the pantry for a bag, home for the D-day. */
val ReminderKind.launchTarget: LaunchTarget
    get() = when (this) {
        ReminderKind.PEAK, ReminderKind.LOW_STOCK -> LaunchTarget.PANTRY
        ReminderKind.DDAY -> LaunchTarget.HOME
    }

/**
 * The journal as the reminder rules read it, read once, so every day asked about sees the same data: the daily check
 * asks about today, schedule-ahead (iOS, [ReminderPlan]) about each of the coming days.
 */
class ReminderData(
    val entries: List<Entry>,
    val pantry: List<PantryItem>,
    val blends: List<Blend>,
    val ddayStart: LocalDate?,
) {
    /**
     * What the daily check would send on [day] for [kinds] if nothing changed before then, minus [sent]: [Reminders]
     * with the home card's beans in use ([ReminderInputs.inUse]) as of [day].
     */
    fun dueOn(day: LocalDate, kinds: ReminderKinds, sent: Set<String>): List<Reminder> = Reminders.due(
        pantry = pantry,
        entries = entries,
        inUse = ReminderInputs.inUse(entries, pantry, blends, day),
        ddayStart = ddayStart,
        today = day,
        kinds = kinds,
        sent = sent,
    )
}

/**
 * The daily check, independent of how a platform schedules it or shows a notification: reads the journal and the
 * settings, asks [Reminders] what is due and remembers what was posted, so nothing is sent twice.
 */
class ReminderCheck(
    private val entries: EntryRepository,
    private val pantry: PantryRepository,
    private val blends: BlendRepository,
    private val settings: SettingsRepository,
    private val prefs: ReminderPrefs,
) {
    private val lock = Mutex()

    /** The journal as it is now. */
    suspend fun data(): ReminderData = ReminderData(
        entries = entries.getAll(),
        pantry = pantry.getAll(),
        blends = blends.getAll(),
        ddayStart = Dates.parseIsoDate(settings.get(SettingsRepository.KEY_DDAY_START)),
    )

    /**
     * Emits at once and again after every change to what the rules read: records, the pantry, blends, the D-day start
     * and the reminder settings (not the log of sent reminders). Schedule-ahead plans again on each.
     */
    fun changes(): Flow<Unit> = combine(
        entries.observeAll(), pantry.observeAll(), blends.observeAll(), settings.observeDdayStart(), prefs.observe(),
    ) { e, p, b, d, s -> listOf(e, p, b, d, s) }.distinctUntilChanged().map { }

    /** What is due [today] under the saved settings (nothing while reminders are off), minus what was already sent. */
    suspend fun dueToday(today: LocalDate): List<Reminder> {
        val s = prefs.load()
        if (!s.enabled) return emptyList()
        return data().dueOn(today, s.kinds, prefs.sent().keys)
    }

    /**
     * Posts what is due [today] through [post] and remembers each reminder [post] reports as shown. Nothing happens
     * while notifications cannot be shown ([canNotify]): those reminders stay due and go out once they can, the same day.
     */
    suspend fun run(today: LocalDate, canNotify: Boolean, post: suspend (Reminder) -> Boolean): List<Reminder> = lock.withLock {
        if (!canNotify) return@withLock emptyList()
        val shown = dueToday(today).filter { post(it) }
        if (shown.isNotEmpty()) prefs.markSent(shown.map { it.key }, today)
        shown
    }
}
