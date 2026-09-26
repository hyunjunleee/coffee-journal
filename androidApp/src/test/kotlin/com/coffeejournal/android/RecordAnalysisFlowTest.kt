package com.coffeejournal.android

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.reference.CvaForm
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ScaScoring
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Feature-plan-v2 §2.2–2.4 through the real app: the 추출 비교 table, the ratio / extraction-yield calculator with
 * 메모에 추가, a CVA tasting from the form to the detail and the home list, a CVA cupping bean, and the 통계 screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class RecordAnalysisFlowTest : CoverageFlowBase() {

    private fun inField(tag: String): SemanticsMatcher = hasSetTextAction() and hasAnyAncestor(hasTestTag(tag))

    private fun fieldText(tag: String): String =
        node(inField(tag)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

    private fun replaceField(tag: String, text: String) {
        bringIntoView(inField(tag))
        node(inField(tag)).performTextReplacement(text)
        settle(1)
    }

    private fun slider(description: String) = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and hasContentDescription(description)

    private fun setSlider(description: String, value: Float) {
        waitFor(slider(description))
        node(slider(description)).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        settle(1)
    }

    private val day get() = Dates.today()

    private fun seedGuatemala() = runBlocking {
        val repo = koinGet<EntryRepository>()
        fun brew(id: String, daysAgo: Int, temp: String, flavor: Double?, dose: String = "15") = Entry(
            id = id, createdAt = Dates.toMillis(Dates.plusDays(day, -daysAgo), 8, 30), category = Category.BEAN,
            name = "과테말라 안티구아", dose = dose, water = "240", temp = temp, grind = "24클릭", time = "2:30", actualNotes = "캐러멜",
            price = "16000", bagWeight = "200", country = "과테말라", process = "워시드", roastery = "모모스",
            attributes = ScaScoring.defaultAttributes() + (flavor?.let { mapOf("flavor" to it) } ?: emptyMap()),
        )
        repo.upsert(brew("g1", 3, "92", 8.0))
        repo.upsert(brew("g2", 2, "91", 8.5))
        repo.upsert(brew("g3", 1, "93", null, dose = "16"))
        koinGet<BeanMetaRepository>().setBest("과테말라 안티구아", "g1")
    }

    private fun openCompare() {
        launchApp()
        // a short phone composes the bean groups only once the home list reaches them
        scrollListTo(button("+ 새 기록 추가"), clickableWith("3개 기록"))
        tap(clickableWith("3개 기록")) // the bean group's header
        tap(button("📊 추출 비교"))
        waitForText("추출 비교")
        waitFor(hasTestTag("compare-table"))
    }

    @Test
    fun compare_bestRecipeFirst_differencesMarked_andColumnsOpenTheRecord() {
        seedGuatemala()
        openCompare()
        waitForText("기준: ⭐ 베스트 레시피 (첫 열) · 기준과 다른 값은 굵게, 바탕색으로 표시해요.")
        // TalkBack reads what differs and by how much
        assertTrue(has(hasContentDescription("온도 91°C, 기준과 다름 (−1°C)")))
        assertTrue(has(hasContentDescription("원두량 16g, 기준과 다름 (+1g)")))
        assertTrue(has(hasContentDescription("점수 38.50, 기준과 다름 (+0.5)")))
        assertTrue(has(hasContentDescription("온도 92°C, 기준")))
        assertTrue("same grind, not marked", has(hasContentDescription("분쇄도 24클릭")))
        // a column opens its record
        val g3 = runBlocking { koinGet<EntryRepository>().getById("g3")!! }
        tap(clickableWith(Dates.mdHm(g3.createdAt)))
        waitUntil("detail of g3") { onDetailOf("과테말라 안티구아") && has(hasText("93°C", substring = true)) }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun compare_fitsA320dpPhone_bySlidingTheColumns() {
        seedGuatemala()
        openCompare()
        val width = rootWidth()
        val reference = node(hasContentDescription("온도 92°C, 기준")).fetchSemanticsNode().boundsInRoot
        assertTrue("the reference column is on screen: $reference in $width", reference.right <= width + 1)
        // the last record is off to the right until the row is slid
        val last = hasContentDescription("온도 91°C, 기준과 다름 (−1°C)")
        bringIntoView(last)
        val slid = node(last).fetchSemanticsNode().boundsInRoot
        assertTrue("slid into view: $slid", slid.left >= 0 && slid.right <= width + 1)
        // the row labels stay put
        assertTrue(node(hasText("온도")).fetchSemanticsNode().boundsInRoot.left < slid.left)
    }

    @Test
    fun calculator_ratioAndExtractionYield_addedToTheNotes() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "계산기 원두")
        typeInto("20", "15")
        typeInto("320", "240")
        tap(button("🧮 비율 · 추출수율 계산기"))
        waitFor(inField("calc-ratio"))
        assertEquals("15", fieldText("calc-dose"))
        assertEquals("16", fieldText("calc-ratio"))
        assertEquals("240", fieldText("calc-water"))
        waitForText("원두량, TDS, 추출액 무게를 적으면 추출수율이 나와요.")
        bringIntoView(inField("calc-tds"))
        node(inField("calc-tds")).performTextInput("1.35")
        node(inField("calc-beverage")).performTextInput("200")
        settle()
        waitFor(hasTestTag("calc-ey") and hasText("추출수율 18%"))
        assertTrue(has(hasText("추출수율: 18~22% 안 · 차트의 이상적 균형 구역")))
        assertTrue(has(hasText("농도(TDS): 1.15~1.35% 안")))
        tap(button("메모에 추가"))
        val line = "[계산기] 원두 15g · 물 240g (1:16) · TDS 1.35% · 추출액 200g → 추출수율 18% (SCA 고전 추출 차트 18~22% 안)"
        waitFor(hasSetTextAction() and hasText(line))
        // 1:15 gives 225 g, which goes into the recipe
        replaceField("calc-ratio", "15")
        assertEquals("225", fieldText("calc-water"))
        tap(button("원두량 · 물량을 레시피에 넣기"))
        waitFor(field("225"))
        saveForm("계산기 원두")
        val saved = entries().single()
        assertEquals(line, saved.notes)
        assertEquals("225", saved.water)
        assertEquals("15", saved.dose)
    }

    @Test
    fun cvaTasting_isSaved_andShownOnTheDetailAndTheHomeList() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "CVA 원두")
        tap(button("CVA"))
        waitForText("SCA CVA · 묘사 + 정동 평가")
        setSlider("프레그런스 강도", 9f)
        CvaForm.sections.forEach { s -> setSlider("${s.ko} 품질 인상", 7f) }
        tap(button("꽃"), 0) // fragrance / aroma box
        tap(button("베리"), 0)
        tap(button("신맛"), 2) // main tastes (the olfactory lists come first)
        tap(button("1"), 0) // non-uniform cups
        tap(button("1"), 1) // defective cups
        waitForText("품질 인상 합 56 × 0.65625 + 52.75 − 균일하지 않은 컵 2점 × 1 → 0.25점 단위 87.50", substring = false)
        tap(button("페놀"))
        waitFor(hasTestTag("cva-score") and hasText("83.50"))
        saveForm("CVA 원두")
        waitForText("CVA 83.50 / 100")
        waitForText("SCA CVA · 묘사 + 정동 평가")
        assertFalse("no SCA 2004 block", has(hasText("SCA CUPPING FORM")))
        val saved = entries().single()
        assertEquals(7.0, saved.attributes["cva.affective.overall"])
        assertEquals(9.0, saved.attributes["cva.intensity.fragrance"])
        assertEquals("floral,berry", saved.attributeNotes["cva.cata.aroma"])
        assertEquals("phenolic", saved.attributeNotes["cva.defects"])
        assertEquals(83.5, CvaScoring.scoreOf(saved))
        assertEquals(null, ScaScoring.effectiveTotal(saved.attributes))
        back()
        waitForText("CVA 최고 83.50")
        tap(clickableWith("CVA 최고 83.50")) // opens the group
        waitForText("CVA 83.50 / 100")
    }

    @Test
    fun cvaCuppingBean_showsItsScore_andReopensInCva() {
        val cva = CvaAssessment(affective = CvaForm.sectionKeys.associateWith { 7 }, mouthfeel = listOf("smooth"), notes = mapOf("overall" to "균형이 좋음"))
        runBlocking {
            koinGet<EntryRepository>().upsert(
                Entry(
                    id = "cup", createdAt = Dates.toMillis(day, 14, 0), category = Category.CUPPING, name = "CVA 커핑", cuppingPlace = "CVA 커핑",
                    cuppingBeans = listOf(CuppingBean(name = "케냐 기통가", country = "케냐", evaluationScores = CvaScoring.toScores(cva), evaluation = CvaScoring.toTexts(cva))),
                )
            )
        }
        launchApp()
        openViaSearch("케냐 기통가")
        tap(clickableWith("케냐 기통가  ·  케냐  ·  CVA 89.50"))
        waitForText("CVA 정동 점수")
        assertTrue(has(hasText("부드러운 (벨벳같은, 매끄러운, 시럽같은)")))
        assertTrue(has(hasText("균형이 좋음")))
        tap(button("수정"))
        waitForText("기록 수정")
        waitFor(button("CVA"))
        assertTrue("the bean reopens on its CVA sheet", isSelected(button("CVA")))
        waitForText("SCA CVA · 묘사 + 정동 평가")
    }

    private fun seedStats() = runBlocking {
        seedGuatemala()
        koinGet<EntryRepository>().upsert(
            Entry(id = "cf", createdAt = Dates.toMillis(day, 15, 0), category = Category.CAFE, name = "파나마 게이샤", cafeName = "카페", price = "9,000", country = "파나마")
        )
    }

    @Test
    fun stats_fromTheHomeActionRow_chartsAreDescribed() {
        seedStats()
        launchApp()
        tap(button("통계"))
        waitForText("통계")
        waitFor(hasTestTag("chart-cups"))
        // the three brews are three days apart, so the start of the month may leave some out: use 전체
        tap(button("전체"))
        waitFor(hasContentDescription("잔 수 4잔, 집 3 · 카페 1"))
        assertTrue(has(hasContentDescription("월별 잔 수 막대 차트. 모두 4잔, 집 추출 3잔, 카페 1잔", substring = true)))
        // bean spending: 16000 ÷ 200 × (15 + 15 + 16) = 3,680 won, plus the café's 9,000
        assertTrue(has(hasContentDescription("지출 12,680원, 원두 + 카페")))
        assertTrue(has(hasText("원두 3,680원 (3잔)")))
        assertTrue(has(hasContentDescription("많이 마신 산지 상위: 과테말라 3회, 파나마 1회")))
        bringIntoView(hasTestTag("chart-scores"))
        assertTrue(has(hasContentDescription("점수 추이 차트. SCA 2004 점수 2개, 최저 38.00, 최고 38.50", substring = true)))
        assertTrue(has(hasContentDescription("물 온도와 점수 산점도. 기록 2개", substring = true)))
        assertTrue(has(hasContentDescription("비율과 점수 산점도. 기록 2개", substring = true)))
    }

    @Test
    fun stats_emptyJournal_saysSoInTheWebsTone() {
        launchApp()
        tap(button("통계"))
        waitForText("이 기간에는 아직 기록이 없어요. 오늘 내린 커피부터 남겨보세요.")
        tap(button("올해"))
        waitForText("이 기간에는 아직 기록이 없어요. 오늘 내린 커피부터 남겨보세요.")
        assertFalse(has(hasTestTag("chart-cups")))
    }
}
