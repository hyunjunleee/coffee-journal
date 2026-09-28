package com.coffeejournal.ui.notify

import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import com.coffeejournal.ui.extract.DrinkingState
import com.coffeejournal.ui.extract.ExtractGrouping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.datetime.LocalDate

/**
 * What the home-screen widget shows, in the home tab's own words: the D-day pill ([ddayLabel], [ddaySince],
 * [milestone]) and the 마시는 중 card ([beanEyebrow], [beanName], [remainingLine]). Empty parts carry the text to show
 * instead ([ddayHint], [emptyText]).
 */
data class WidgetSnapshot(
    val ddayLabel: String?,
    val ddaySince: String?,
    val milestone: String?,
    val ddayHint: String?,
    val beanEyebrow: String?,
    val beanName: String?,
    val remainingLine: String?,
    /** Other opened bags the home card would scroll to. */
    val moreBeans: Int,
    val emptyText: String?,
)

object WidgetSnapshots {
    const val DDAY_HINT = "커피 처음 마신 날을 기록해두면 며칠째인지 보여드려요."
    const val EMPTY_BEAN = "아직 마시는 중인 원두가 없어요. 오늘 내린 커피부터 남겨보세요."

    /** The snapshot for [today]: the same rules as the D-day pill ([DdayRules]) and the card ([ExtractGrouping.drinking]). */
    fun build(entries: List<Entry>, pantry: List<PantryItem>, blends: List<Blend>, ddayStart: LocalDate?, today: LocalDate): WidgetSnapshot {
        val label = DdayRules.label(ddayStart, today)
        val base = WidgetSnapshot(
            ddayLabel = label,
            ddaySince = ddayStart?.takeIf { label != null }?.let { "${Dates.isoDate(it).replace('-', '.')} 첫 추출" },
            milestone = ddayStart?.takeIf { label != null }?.let { DdayRules.milestone(DdayRules.dayCount(it, today))?.text },
            ddayHint = if (label == null) DDAY_HINT else null,
            beanEyebrow = null, beanName = null, remainingLine = null, moreBeans = 0, emptyText = null,
        )
        return when (val drinking = ExtractGrouping.drinking(entries, pantry, blends, emptyList(), today)) {
            DrinkingState.None -> base.copy(emptyText = EMPTY_BEAN)
            is DrinkingState.OpenedBags -> drinking.cards.first().let { card ->
                base.copy(
                    beanEyebrow = card.eyebrow,
                    beanName = card.item.name.ifBlank { "이름 없음" },
                    remainingLine = card.remainingLine,
                    moreBeans = drinking.cards.size - 1,
                )
            }
            is DrinkingState.RecentBean -> base.copy(
                beanEyebrow = drinking.card.eyebrow,
                beanName = BeanNames.displayName(drinking.card.entry.name),
                remainingLine = drinking.card.remainingLine,
            )
        }
    }
}

/** Refreshes the home-screen widgets (Android: the Glance widget, registered by the app). */
fun interface HomeWidgets {
    suspend fun refresh()
}

/** The widget's data: one read for a new widget session, and a live flow while a session runs. */
class HomeWidgetFeed(
    private val db: AppDatabase,
    private val entries: EntryRepository,
    private val pantry: PantryRepository,
    private val blends: BlendRepository,
    private val settings: SettingsRepository,
) {
    suspend fun current(today: LocalDate = Dates.today()): WidgetSnapshot = WidgetSnapshots.build(
        entries.getAll(), pantry.getAll(), blends.getAll(),
        Dates.parseIsoDate(settings.get(SettingsRepository.KEY_DDAY_START)), today,
    )

    fun snapshots(today: Flow<LocalDate> = Dates.todayFlow()): Flow<WidgetSnapshot> = combine(
        entries.observeAll(), pantry.observeAll(), blends.observeAll(), settings.observeDdayStart(), today,
    ) { e, p, b, d, t -> WidgetSnapshots.build(e, p, b, d, t) }.distinctUntilChanged()

    /**
     * Fires after every write to the tables the widget reads (records, their cupping beans, the pantry, blends) and
     * when the D-day start changes. The tables are watched through Room's invalidation, so it holds no data itself;
     * of the settings only the D-day start counts, as the record form writes its draft there while it is typed.
     */
    fun changes(): Flow<Unit> = merge(
        db.invalidationTracker.createFlow(*WATCHED_TABLES, emitInitialState = false).map { },
        settings.observeDdayStart().distinctUntilChanged().drop(1).map { },
    )

    companion object {
        val WATCHED_TABLES = arrayOf("entries", "cupping_beans", "pantry_items", "blends")
    }
}

/**
 * The app-level observer that keeps the widget current while the app process lives: after a save (a burst of writes,
 * such as a restore, counts once) and when the date changes at midnight.
 */
object HomeWidgetSync {
    const val SETTLE_MS = 800L

    @OptIn(FlowPreview::class)
    suspend fun run(feed: HomeWidgetFeed, widgets: HomeWidgets, today: Flow<LocalDate> = Dates.todayFlow()) {
        merge(feed.changes(), today.drop(1).map { })
            .debounce(SETTLE_MS)
            .collect {
                try {
                    widgets.refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // a widget that cannot be drawn now is drawn at the next change; the app goes on
                }
            }
    }
}
