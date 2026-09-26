package com.coffeejournal.coverage

import androidx.compose.ui.geometry.Offset
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.domain.reference.Champions
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.WorldMapData
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.RecipeSteps
import com.coffeejournal.ui.bean.a.ProcessStats
import com.coffeejournal.ui.bean.b.WorldMapGeometry
import com.coffeejournal.ui.calendar.CalFilter
import com.coffeejournal.ui.calendar.CalendarGrid
import com.coffeejournal.ui.calendar.YearMonth
import com.coffeejournal.ui.form.CuppingBeanForm
import com.coffeejournal.ui.form.FormMapper
import com.coffeejournal.ui.form.StepForm
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.LocalDate
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Critic gap #7, the rules under the flows covered by the coverage flow tests (CoverageFlowTest*.kt), checked
 * against the web handlers without a device: recipe launchers (script3.js 6143-6300), the steps summary and diff
 * (6760-6930), cupping bean edits, the 허니 detail (3983-4016, 4147-4190), map hit testing (2195-2302) and the
 * calendar filters (1028-1045, 1520-1660).
 */
class CoverageRulesTest {
    private val now = Dates.toMillis(LocalDate(2026, 9, 25), 12, 0)

    // ───────────── recipe launchers ─────────────

    /** Web updateStepsSummary overwrites f-time with the computed value, so a recipe's own time must agree with its steps. */
    @Test fun cafeRecipeTimesAgreeWithTheirSteps() {
        CafeRecipes.all.forEach { r ->
            val computed = RecipeSteps.formatSec(RecipeSteps.summary(r.steps).totalTimeSec)
            if (r.time.isNotBlank()) assertEquals(r.time, computed, r.id)
            val applied = FormMapper.applyCafeRecipe(FormMapper.newState(FormMode.EXTRACT, null, now), r)
            assertEquals(computed, applied.time, "${r.id}: 총 추출시간 after applying")
            assertEquals(r.steps, applied.steps.map { it.toStep() }, "${r.id}: the log holds the recipe's steps")
            assertEquals(RecipeRef(r.name, r.steps), applied.appliedRecipeRef)
            assertEquals(r.tempRange?.let { "권장 범위: $it" } ?: "", applied.tempHint)
            assertTrue(RecipeSteps.diff(applied.steps.map { it.toStep() }, applied.appliedRecipeRef).isEmpty(), "${r.id}: ✓ 그대로 부었어요")
        }
    }

    /** Web champ-apply touches dose / water / temp / temp-hint / dripper only: a cafe recipe applied before stays. */
    @Test fun championAfterACafeRecipeKeepsItsStepsAndReference() {
        val glitch = CafeRecipes.all.single { it.id == "glitch" }
        val withCafe = FormMapper.applyCafeRecipe(FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "x", filter = "내 필터"), glitch)
        val champ = FormMapper.applyChampion(withCafe, Champions.all.single { it.year == 2019 })
        assertEquals(listOf("16", "240", "94", "오리가미", ""), listOf(champ.dose, champ.water, champ.temp, champ.dripper, champ.tempHint))
        assertEquals(withCafe.steps, champ.steps)
        assertEquals(withCafe.appliedRecipeRef, champ.appliedRecipeRef)
        assertEquals(withCafe.copy(dose = "16", water = "240", temp = "94", dripper = "오리가미", tempHint = "", openLauncher = null), champ)
    }

    // ───────────── steps log ─────────────

    @Test fun yourHomeEdits_summaryWarningAndDiffLikeTheWeb() {
        val yourHome = CafeRecipes.all.single { it.id == "yourhome" }
        val base = FormMapper.applyCafeRecipe(FormMapper.newState(FormMode.EXTRACT, null, now), yourHome)
        val steps = base.steps.toMutableList()
        steps[2] = steps[2].copy(water = "200")
        steps[3] = steps[3].copy(wait = "80")
        steps[1] = steps[1].copy(pour = true, water = "10")
        val edited = FormMapper.withStepsTime(base.copy(steps = steps))
        assertEquals("2:40", edited.time)
        val live = edited.steps.map { it.toStep() }
        val summary = RecipeSteps.summary(live)
        assertEquals("총 3차 추출 · 합계 물량 260g · 총 시간 2:40", RecipeSteps.summaryLine(summary))
        assertEquals(listOf("⚠ 레시피의 물량(240g)과 맞지 않습니다. (합계 260g)"), RecipeSteps.warnings(summary, edited.water, edited.time))
        // web compareStepsToRecipe: a pour added where the recipe waits has no reference water, so no diff part
        assertEquals(listOf("2차 푸어: 물량 190g→200g (+10g)", "드로우다운: 대기 70s→80s (+10s)"), RecipeSteps.diff(live, edited.appliedRecipeRef))
        // toggling the pour off drops its water (web: input.value = '')
        assertEquals("", StepForm(time = "0:10", wait = "40", pour = false, water = "10").toStep().water)
        // an added row after the drawdown carries the total time, the diff only compares the recipe's length
        val longer = FormMapper.withStepsTime(edited.copy(steps = edited.steps + StepForm(time = "2:40", wait = "20", note = "추가 대기")))
        assertEquals("3:00", longer.time)
        assertEquals(RecipeSteps.diff(live, edited.appliedRecipeRef), RecipeSteps.diff(longer.steps.map { it.toStep() }, edited.appliedRecipeRef))
    }

    // ───────────── cupping edit: remove the first bean, add one ─────────────

    @Test fun cuppingEditRemoveFirstAddOne_keepsEveryFieldOfTheOthers() {
        val stored = Entry(
            id = "E", createdAt = now, category = Category.CUPPING, cuppingType = CuppingType.PUBLIC, cuppingPlace = "커피플랜트 성수", name = "커피플랜트 성수",
            notes = "케냐가 압도적", cuppingBeans = listOf(
                CuppingBean(id = "E-0", name = "케냐", country = "케냐", rank = "1", process = "워시드", roast = "라이트", actualNotes = "블랙커런트", evaluationScores = mapOf("acidity" to 9.0)),
                CuppingBean(id = "E-1", name = "파나마 게이샤", country = "파나마", region = "Boquete", process = "허니(레드 허니)", roast = "라이트", price = "15000", evaluation = mapOf("flavor" to "자스민")),
                CuppingBean(id = "E-2", name = "하우스 블렌드", beanMode = "blend", blendComponentsText = "브라질 60 · 콜롬비아 40", process = "카보닉", roast = "미디엄 다크", memo = "고소함"),
            ),
        )
        val form = FormMapper.fromEntry(stored, FormMode.CUPPING)
        val edited = form.copy(cuppingBeans = form.cuppingBeans.drop(1) + CuppingBeanForm(name = "르완다", country = "르완다", roast = "미디엄", actualInput = "무화과"))
        val saved = FormMapper.toEntry(edited, "E", stored, emptyList(), now + 1)
        assertEquals(stored.cuppingBeans.drop(1), saved.cuppingBeans.take(2), "every field of the kept beans, ids included")
        val added = saved.cuppingBeans[2]
        assertEquals(listOf("르완다", "르완다", "미디엄", "무화과", ""), listOf(added.name, added.country, added.roast, added.actualNotes, added.id))
        assertEquals(stored.copy(cuppingBeans = saved.cuppingBeans), saved)
    }

    // ───────────── 허니 detail ─────────────

    @Test fun honeySubtypesFromFormValuesAndOldRecords() {
        val entries = listOf(
            Entry(id = "new", createdAt = now, name = "코스타리카", process = FormMapper.processValue("허니", "레드 허니")),
            Entry(id = "h1", createdAt = now - 5, name = "과테말라", process = "허니(옐로우 허니)"),
            Entry(id = "h2", createdAt = now - 4, category = Category.CAFE, name = "카페 레드", cafeName = "테스트 카페", process = "Red Honey"),
            Entry(id = "h3", createdAt = now - 3, name = "브라질", process = "허니(블랙)"),
            Entry(id = "h4", createdAt = now - 2, category = Category.CUPPING, cuppingBeans = listOf(CuppingBean(name = "엘살바도르", process = "허니"))),
            Entry(id = "w1", createdAt = now - 1, name = "워시드", process = "워시드"),
        )
        val matching = ProcessStats.breakdown(BeanRecords.flatten(entries), "허니", "허니").matching
        val groups = ProcessStats.honeyGroups(matching)
        assertEquals(listOf("레드 허니" to 2, "블랙" to 1, "세부 미기록" to 1, "옐로 허니" to 1), groups.map { it.first to it.second.size })
        assertEquals(listOf("new", "h2"), groups.first().second.map { it.entryId }, "newest first")
    }

    // ───────────── coffee map hit testing ─────────────

    /** The flow test taps these: a spot of Brazil far from its dots, and the dots themselves. */
    @Test fun countrySpotAndRegionDotsResolveLikeTheWebClicks() {
        val polygons = WorldMapGeometry.parseAll()
        val brazil = polygons.single { it.name == "Brazil" }
        val dots = CoffeeCountries.all.flatMap { c -> c.regions.map { Offset(it.x, it.y) } }
        fun clearance(p: Offset) = dots.minOf { val dx = it.x - p.x; val dy = it.y - p.y; sqrt(dx * dx + dy * dy) }
        val spots = (0..40).flatMap { i -> (0..40).map { j -> Offset(brazil.bounds.left + brazil.bounds.width * i / 40f, brazil.bounds.top + brazil.bounds.height * j / 40f) } }
            .filter { brazil.contains(it) }
        val best = spots.maxBy(::clearance)
        // the app widens a dot's tap area to 14 dp: on a 379 dp wide phone canvas that is 14 / (379 / 788) ≈ 29 viewBox units
        assertTrue(clearance(best) > 14f / (379f / WorldMapData.VIEW_W), "a spot of Brazil ${clearance(best)} units from any dot")
        assertEquals("Brazil", WorldMapGeometry.hitCountry(polygons, best)?.name)
        assertNull(WorldMapGeometry.hitRegion(best, 14f / (379f / WorldMapData.VIEW_W)))
        CoffeeCountries.byEn.getValue("Ethiopia").regions.forEach { r ->
            assertEquals(r.name, WorldMapGeometry.hitRegion(Offset(r.x, r.y))?.region?.name, "tapping ${r.name} itself")
        }
    }

    // ───────────── calendar filters ─────────────

    @Test fun categoryFilterDecidesWhichSquaresAreMarked() {
        val sep = YearMonth(2026, 9)
        fun at(day: Int) = Dates.toMillis(LocalDate(2026, 9, day), 10, 0)
        val entries = listOf(
            Entry(id = "b", createdAt = at(3), name = "원두"),
            Entry(id = "c", createdAt = at(7), category = Category.CAFE, name = "카페"),
            Entry(id = "k", createdAt = at(11), category = Category.CUPPING, cuppingType = CuppingType.HOME, name = "커핑"),
        )
        val blends = listOf(Blend(id = "bl", name = "블렌드", date = "2026-09-15", createdAt = 0))
        fun marked(filter: String) = CalendarGrid.build(sep, entries, blends, filter, null, LocalDate(2026, 9, 25)).cells.filter { it.hasContent }.map { it.date.day }
        assertEquals(listOf(3, 7, 11, 15), marked(CalFilter.ALL))
        assertEquals(listOf(11), marked(CalFilter.CUPPING), "the cupping type chips filter the list, not the grid")
        assertEquals(listOf(7), marked(CalFilter.CAFE))
        assertEquals(listOf(3, 15), marked(CalFilter.BEAN), "lab blends count as 원두")
    }
}
