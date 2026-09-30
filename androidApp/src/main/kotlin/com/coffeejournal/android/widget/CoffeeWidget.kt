package com.coffeejournal.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.LaunchTarget
import com.coffeejournal.ui.notify.HomeWidgetFeed
import com.coffeejournal.ui.notify.LaunchIntents
import com.coffeejournal.ui.notify.WidgetSnapshot
import com.coffeejournal.ui.notify.WidgetSnapshots
import kotlinx.coroutines.flow.flowOf
import org.koin.core.context.GlobalContext

/**
 * Home-screen widget: the Coffee D-day, the bean being drunk with its remaining grams (the home tab's pill and
 * 마시는 중 card, same texts) and "+ 새 기록", which opens the app straight into the new-record chooser. Tapping anywhere
 * else opens the home tab. Archive look: ivory, ink, monospace numbers, square corners, hairline.
 */
class CoffeeWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val feed = GlobalContext.getOrNull()?.getOrNull<HomeWidgetFeed>()
        val first = feed?.current() ?: WidgetSnapshots.build(emptyList(), emptyList(), emptyList(), null, Dates.today())
        val openHome = WidgetActions.openHome(context)
        val newRecord = WidgetActions.newRecord(context)
        provideContent {
            // while this session runs, a save in the app shows up here at once
            val updates = remember { feed?.snapshots() ?: flowOf(first) }
            val snapshot by updates.collectAsState(first)
            CoffeeWidgetContent(snapshot, openHome = openHome, newRecord = newRecord)
        }
    }

    companion object {
        /** Two rows of cells: D-day and "+ 새 기록" on one line, the bean under them. */
        val COMPACT = DpSize(180.dp, 100.dp)

        /** Three rows or more: the pill's date line, the card's "마시는 중 · …" line and a full-width button. */
        val TALL = DpSize(180.dp, 170.dp)
    }
}

/** What the widget's taps do: open the app at home, or straight into a new brew record. */
object WidgetActions {
    fun openHome(context: Context): Action = actionStartActivity(LaunchIntents.open(context, LaunchTarget.HOME))

    fun newRecord(context: Context): Action = actionStartActivity(LaunchIntents.open(context, LaunchTarget.NEW_RECORD))
}

internal object WidgetColors {
    val ivory = Color(0xFFF5F4EF)
    val ink = Color(0xFF191916)
    val accent = Color(0xFF20201D)
    val muted = Color(0xFF5D5B54)
    val faint = Color(0xFF858177)
    val line = Color(0xFFC8C6BD)
}

private fun style(size: Float, color: Color, mono: Boolean = false, bold: Boolean = false) = TextStyle(
    color = ColorProvider(color),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
    fontFamily = if (mono) FontFamily.Monospace else null,
)

/** The widget's layout for [snapshot]; the compact or tall arrangement by the size the launcher gives it. */
@Composable
fun CoffeeWidgetContent(snapshot: WidgetSnapshot, openHome: Action, newRecord: Action) {
    val tall = LocalSize.current.height >= CoffeeWidget.TALL.height
    // hairline frame: the line colour shows through a 1dp inset
    Box(GlanceModifier.fillMaxSize().background(WidgetColors.line).padding(1.dp)) {
        Column(
            GlanceModifier.fillMaxSize().background(WidgetColors.ivory).padding(horizontal = 14.dp, vertical = 12.dp).clickable(openHome),
        ) {
            if (tall) {
                Dday(snapshot, big = true)
                Rule()
                Bean(snapshot, withEyebrow = true)
                Spacer(GlanceModifier.defaultWeight())
                NewRecordButton(newRecord, GlanceModifier.fillMaxWidth().height(36.dp))
            } else {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.defaultWeight()) { Dday(snapshot, big = false) }
                    Spacer(GlanceModifier.width(8.dp))
                    NewRecordButton(newRecord, GlanceModifier.height(30.dp))
                }
                Rule()
                CompactBean(snapshot)
            }
        }
    }
}

@Composable
private fun Dday(s: WidgetSnapshot, big: Boolean) {
    Column {
        if (s.ddayLabel != null) {
            Text(s.ddayLabel!!, style = style(if (big) 22f else 18f, WidgetColors.ink, mono = true, bold = true), maxLines = 1)
            val detail = if (big) listOfNotNull(s.ddaySince, s.milestone) else listOfNotNull(s.milestone)
            if (detail.isNotEmpty()) Text(detail.joinToString(" · "), style = style(10.5f, WidgetColors.muted, mono = true), maxLines = 1)
        } else {
            Text(s.ddayHint.orEmpty(), style = style(12f, WidgetColors.muted), maxLines = 2)
        }
    }
}

@Composable
private fun Bean(s: WidgetSnapshot, withEyebrow: Boolean) {
    Column(GlanceModifier.fillMaxWidth()) {
        if (s.beanName == null) {
            Text(s.emptyText.orEmpty(), style = style(12f, WidgetColors.muted), maxLines = 2)
            return@Column
        }
        if (withEyebrow && s.beanEyebrow != null) Text(s.beanEyebrow!!, style = style(10.5f, WidgetColors.faint, mono = true), maxLines = 1)
        val more = if (s.moreBeans > 0) " 외 ${s.moreBeans}" else ""
        Text(s.beanName + more, style = style(15f, WidgetColors.ink, bold = true), maxLines = 1)
        s.remainingLine?.let { Text(it, style = style(12f, WidgetColors.ink, mono = true), maxLines = 1) }
    }
}

/** Two rows of cells: the name and the grams share one line. */
@Composable
private fun CompactBean(s: WidgetSnapshot) {
    if (s.beanName == null) {
        Text(s.emptyText.orEmpty(), style = style(12f, WidgetColors.muted), maxLines = 2)
        return
    }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val more = if (s.moreBeans > 0) " 외 ${s.moreBeans}" else ""
        Text(s.beanName + more, style = style(14f, WidgetColors.ink, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        s.remainingLine?.let {
            Spacer(GlanceModifier.width(8.dp))
            Text(it, style = style(11.5f, WidgetColors.ink, mono = true), maxLines = 1)
        }
    }
}

@Composable
private fun Rule() {
    Spacer(GlanceModifier.height(8.dp))
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetColors.line)) {}
    Spacer(GlanceModifier.height(8.dp))
}

@Composable
private fun NewRecordButton(onClick: Action, modifier: GlanceModifier) {
    Box(
        modifier.background(WidgetColors.accent).padding(horizontal = 12.dp).clickable(onClick).semantics { contentDescription = "새 기록 추가" },
        contentAlignment = Alignment.Center,
    ) {
        Text(NEW_RECORD_LABEL, style = style(13f, WidgetColors.ivory, bold = true), maxLines = 1)
    }
}

const val NEW_RECORD_LABEL = "+ 새 기록"
