package com.coffeejournal.ui.extract.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.domain.rules.BrewMath
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.FitText
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.SubTabs
import com.coffeejournal.ui.theme.deriveOffMain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.koin.compose.viewmodel.koinViewModel

/** Derived off the main thread (records × period × today); null until the first result. */
class StatsViewModel(entries: EntryRepository, pantry: PantryRepository, today: Flow<LocalDate> = Dates.todayFlow()) : ViewModel() {
    private val _period = MutableStateFlow(StatsPeriod.MONTH)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    val summary: StateFlow<StatsSummary?> = combine(entries.observeAll(), pantry.observeAll(), _period, today.distinctUntilChanged()) { e, p, per, d ->
        Inputs(e, p, per, d)
    }.deriveOffMain { StatsCalc.compute(it.entries, it.pantry, it.period, it.today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class Inputs(
        val entries: List<com.coffeejournal.domain.model.Entry>,
        val pantry: List<com.coffeejournal.domain.model.PantryItem>,
        val period: StatsPeriod,
        val today: LocalDate,
    )

    fun setPeriod(p: StatsPeriod) { _period.value = p }
}

private val PERIOD_LABELS = StatsPeriod.entries.associate { it.name to it.label }

/** Route.Stats — 통계 (feature-plan-v2 §2.4), opened from the home action row. */
@Composable
fun StatsScreen(nav: NavHostController) {
    val vm = koinViewModel<StatsViewModel>()
    val period by vm.period.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Ink.bg).statusBarsPadding()) {
        ScreenTitleBar(title = "통계", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(12.dp))
            SubTabs(items = PERIOD_LABELS.keys.toList(), selected = period.name, onSelect = { vm.setPeriod(StatsPeriod.valueOf(it)) }, labels = PERIOD_LABELS)
            val s = summary
            if (s != null && s.period == period) StatsBody(s)
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun StatsBody(s: StatsSummary) {
    Text(StatsText.rangeLine(s), style = AppType.monoSmall, modifier = Modifier.padding(top = 8.dp))
    if (s.isEmpty) {
        Spacer(Modifier.height(12.dp))
        EmptyNote("이 기간에는 아직 기록이 없어요. 오늘 내린 커피부터 남겨보세요.")
        return
    }
    Tiles(s)

    SectionLabel(if (s.daily) "잔 수 · 일별" else "잔 수 · 월별")
    CupBarsChart(s.bars, s.daily, StatsText.cupsDescription(s))
    Legend("집 추출" to Ink.accent, "카페" to Ink.cafe)

    SectionLabel("원두 사용량 · 지출")
    Text(StatsText.gramsLine(s), style = AppType.body)
    Text(StatsText.beanSpendLine(s), style = AppType.body, modifier = Modifier.padding(top = 2.dp))
    if (s.spending.cafesPriced > 0) Text("카페 ${StatsCalc.won(s.spending.cafe)} (${s.spending.cafesPriced}잔)", style = AppType.body, modifier = Modifier.padding(top = 2.dp))
    HintText("원두 지출은 봉투 가격 ÷ 용량 × 원두량이에요. 가격이나 용량이 없는 기록은 같은 원두의 첫 기록·보관함 봉투에서 가져와요.")

    Ranking("많이 마신 산지", s.origins, "기록된 산지가 없어요.")
    Ranking("가공 방식", s.processes, "기록된 가공 방식이 없어요.")
    Ranking("품종", s.varieties, "기록된 품종이 없어요.")
    Ranking("로스터리", s.roasteries, "기록된 로스터리가 없어요.")

    SectionLabel("점수 추이")
    if (s.scores.isEmpty()) {
        HintText("이 기간에 점수를 매긴 기록이 없어요.")
    } else {
        ScoreTrendChart(s.scores, StatsText.scoresDescription(s.scores))
        ScoreLegend(s.scores.any { it.cva }, s.scores.any { !it.cva })
    }
    SectionLabel("비율과 점수")
    if (s.ratioVsScore.isEmpty()) HintText("원두량·물량과 점수가 함께 있는 기록이 없어요.")
    else {
        ScatterChart(s.ratioVsScore, { "1:" + BrewMath.fmt1(it) }, StatsText.scatterDescription("비율", s.ratioVsScore) { "1:" + BrewMath.fmt1(it) }, "chart-ratio")
        Text("가로: 비율 1:x · 세로: 점수", style = AppType.faint)
        ScoreLegend(s.ratioVsScore.any { it.cva }, s.ratioVsScore.any { !it.cva })
    }
    SectionLabel("물 온도와 점수")
    if (s.tempVsScore.isEmpty()) HintText("물 온도와 점수가 함께 있는 기록이 없어요.")
    else {
        ScatterChart(s.tempVsScore, { BrewMath.fmt1(it) + "°C" }, StatsText.scatterDescription("물 온도", s.tempVsScore) { BrewMath.fmt1(it) + "°C" }, "chart-temp")
        Text("가로: 물 온도 °C · 세로: 점수", style = AppType.faint)
        ScoreLegend(s.tempVsScore.any { it.cva }, s.tempVsScore.any { !it.cva })
    }
}

@Composable
private fun ScoreLegend(hasCva: Boolean, hasSca: Boolean) {
    val items = listOfNotNull(("SCA 2004" to Ink.accent).takeIf { hasSca }, ("CVA" to Ink.cupping).takeIf { hasCva })
    if (items.size > 1 || hasCva) Legend(*items.toTypedArray(), hollow = setOf("CVA"), round = setOf("SCA 2004"))
}

@Composable
private fun Tiles(s: StatsSummary) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile("잔 수", "${s.cups}잔", "집 ${s.brewCount} · 카페 ${s.cafeCount}", Modifier.weight(1f))
        Tile("원두", "${Prices.trimNumber(kotlin.math.round(s.gramsUsed * 10) / 10.0)}g", "집 추출 원두량", Modifier.weight(1f))
        Tile("지출", StatsCalc.won(s.spending.beans + s.spending.cafe), "원두 + 카페", Modifier.weight(1f))
    }
    if (s.cuppingCount > 0) HintText("커핑 ${s.cuppingCount}회는 잔 수에 넣지 않았어요.")
}

@Composable
private fun Tile(title: String, value: String, sub: String, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title $value, $sub" },
    ) {
        Text(title, style = AppType.monoSmall)
        FitText(value, style = AppType.displayNumber.copy(fontSize = AppType.displayNumber.fontSize * 0.75f), minFontSize = AppType.monoSmall.fontSize)
        FitText(sub, style = AppType.faint)
    }
}

@Composable
private fun Ranking(title: String, items: List<Ranked>, empty: String) {
    SectionLabel(title)
    if (items.isEmpty()) HintText(empty)
    else Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$title 상위: " + items.joinToString(", ") { "${it.name} ${it.count}회" } }.testTag("rank-$title")) {
        RankedBars(items)
    }
}

/** The sentences TalkBack reads for each chart and the lines under the numbers (pure, tested). */
internal object StatsText {
    fun rangeLine(s: StatsSummary): String =
        (s.from?.let { Dates.ymdCompact(it) } ?: "") + " ~ " + Dates.ymdCompact(s.to)

    fun cupsDescription(s: StatsSummary): String {
        val unit = if (s.daily) "일별" else "월별"
        val top = s.bars.maxByOrNull { it.total }?.takeIf { it.total > 0 }
        val parts = if (s.daily) emptyList() else s.bars.map { "${it.label} ${it.total}잔" }
        val best = top?.let { if (s.daily) "가장 많이 마신 날은 ${s.to.month.number}월 ${it.label}일 ${it.total}잔" else "가장 많이 마신 달은 ${it.label} ${it.total}잔" }
        return listOfNotNull("$unit 잔 수 막대 차트", "모두 ${s.cups}잔, 집 추출 ${s.brewCount}잔, 카페 ${s.cafeCount}잔", parts.takeIf { it.isNotEmpty() }?.joinToString(", "), best)
            .joinToString(". ") + "."
    }

    fun gramsLine(s: StatsSummary): String {
        val avg = if (s.brewCount > 0) s.gramsUsed / s.brewCount else 0.0
        return "원두 ${Prices.trimNumber(kotlin.math.round(s.gramsUsed * 10) / 10.0)}g · 집 추출 ${s.brewCount}잔" +
            if (s.brewCount > 0) ", 한 잔 평균 ${BrewMath.fmt1(avg)}g" else ""
    }

    fun beanSpendLine(s: StatsSummary): String {
        val sp = s.spending
        if (sp.brewsPriced == 0) return "원두 지출: 가격을 알 수 있는 기록이 없어요."
        return "원두 ${StatsCalc.won(sp.beans)} (${sp.brewsPriced}잔" + (if (sp.brewsUnpriced > 0) ", 가격 모름 ${sp.brewsUnpriced}잔 제외" else "") + ")"
    }

    fun scoresDescription(points: List<ScorePoint>): String {
        val parts = mutableListOf("점수 추이 차트")
        val sca = points.filter { !it.cva }
        if (sca.isNotEmpty()) {
            parts += "SCA 2004 점수 ${sca.size}개, 최저 ${ScaScoring.format2(sca.minOf { it.score })}, 최고 ${ScaScoring.format2(sca.maxOf { it.score })}, " +
                "최근 ${ScaScoring.format2(sca.maxBy { it.createdAt }.score)}"
        }
        val cva = points.filter { it.cva }
        if (cva.isNotEmpty()) {
            parts += "CVA 점수 ${cva.size}개, 최저 ${ScaScoring.format2(cva.minOf { it.score })}, 최고 ${ScaScoring.format2(cva.maxOf { it.score })}"
        }
        return parts.joinToString(". ") + "."
    }

    fun scatterDescription(what: String, points: List<ScatterPoint>, fmt: (Double) -> String): String {
        val best = points.maxBy { it.y }
        val title = if (what == "비율") "비율과 점수" else "${what}와 점수"
        return "$title 산점도. 기록 ${points.size}개, $what ${fmt(points.minOf { it.x })}부터 ${fmt(points.maxOf { it.x })}까지. " +
            "가장 높은 점수 ${ScaScoring.format2(best.y)}${if (best.cva) "(CVA)" else ""}는 $what ${fmt(best.x)}."
    }
}
