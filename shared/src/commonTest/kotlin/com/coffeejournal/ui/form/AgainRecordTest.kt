package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "같은 커피 다시 기록": a café record's coffee comes back as a new record of today, without that visit's tasting. */
class AgainRecordTest {
    private val visit = Dates.toMillis(LocalDate(2026, 9, 18), 15, 0)
    private val today = Dates.toMillis(LocalDate(2026, 9, 30), 10, 30)

    private val cafe = Entry(
        id = "e4", createdAt = visit, category = Category.CAFE, name = "브라질 세하도 내추럴 (프릳츠)", cafeName = " FELT 청계천 ",
        country = "브라질", region = "Cerrado Mineiro, Patrocínio", altitude = "1,100 masl", variety = "Mundo Novo / Catuai",
        process = "내추럴", roast = "미디엄", price = "6500", roastery = "프릳츠", expectedNotes = "헤이즐넛, 캐러멜",
        dripper = "칼리타 웨이브", dose = "18", water = "280", steps = listOf(RecipeStep(time = "0:00", water = "40")),
        actualNotes = "헤이즐넛, 초콜릿", notes = "고소하고 편안한 데일리.", bagPhotos = listOf("p1.jpg"),
        attributes = ScaScoring.defaultAttributes() + ("aroma" to 8.0), attributeNotes = mapOf("aroma" to "고소함"),
    )

    @Test fun theCoffeeComesBack_asANewCafeRecordOfToday() {
        val s = FormMapper.again(cafe, today, draftId = "new1")
        assertNull(s.editingId)
        assertEquals("new1", s.draftId)
        assertEquals(Category.CAFE, s.category)
        assertEquals(FormMode.CAFE, s.mode)
        assertEquals(today, s.createdAt)
        assertEquals(" FELT 청계천 ", s.cafeName)
        assertEquals("브라질 세하도 내추럴 (프릳츠)", s.name)
        assertEquals("6,500", s.price)
        assertEquals("프릳츠", s.roastery)
        assertEquals(listOf("헤이즐넛", "캐러멜"), s.expectedNotes)
        // the café's recipe comes along, unfolded
        assertTrue(s.cafeRecipeUsed)
        assertEquals("칼리타 웨이브", s.dripper)
        assertEquals(1, s.steps.size)
        assertEquals("2026.09.18 · FELT 청계천", s.againFrom)
        assertTrue(s.repeatBean)
    }

    @Test fun thatVisitsTasting_andPhotos_stayWithIt() {
        val s = FormMapper.again(cafe, today, draftId = "new1")
        assertTrue(s.actualNotes.isEmpty())
        assertEquals("", s.notes)
        assertEquals(ScaScoring.defaultAttributes(), s.attributes)
        assertTrue(s.attributeNotes.isEmpty())
        assertTrue(s.bagPhotos.none { it.hasImage }, "a photo file belongs to one record")
    }

    @Test fun saved_itIsASecondRecord_withTheOriginTextsAsTheFirstWroteThem() {
        val s = FormMapper.again(cafe, today, draftId = "new1")
        val saved = FormMapper.toEntry(s, s.draftId, null, emptyList(), today + 60_000)
        assertEquals("new1", saved.id)
        assertNotEquals(cafe.id, saved.id)
        assertEquals(today, saved.createdAt)
        assertEquals(Category.CAFE, saved.category)
        assertEquals("FELT 청계천", saved.cafeName)
        assertEquals(cafe.name, saved.name)
        assertEquals("6500", saved.price)
        assertEquals(cafe.region, saved.region)
        assertEquals(cafe.altitude, saved.altitude)
        assertEquals(cafe.variety, saved.variety)
        assertEquals("", saved.actualNotes)
        assertEquals("", saved.notes)
        assertTrue(saved.bagPhotos.isEmpty())
    }

    @Test fun aCafeBlend_comesBackWithAllItsBeans() {
        val blend = cafe.copy(
            beanMode = BeanMode.COMMERCIAL_BLEND,
            blendComponents = listOf(BlendComponent(name = "브라질 세하도", percent = "60"), BlendComponent(name = "에티오피아 구지", percent = "40")),
        )
        val s = FormMapper.again(blend, today)
        assertEquals("60", s.firstBeanPercent)
        assertEquals(1, s.blendBeans.size)
        assertEquals(BeanMode.COMMERCIAL_BLEND, s.effectiveBeanMode)
    }

    @Test fun aVisitWithoutRecipe_opensWithTheRecipeFolded() {
        val plain = cafe.copy(dripper = "", dose = "", water = "", steps = emptyList())
        val s = FormMapper.again(plain, today)
        assertFalse(s.cafeRecipeUsed)
        assertTrue(s.steps.isEmpty())
    }

    @Test fun theDraft_isKeptPerCopiedRecord_andClosingTheBannerIsNoInput() {
        assertEquals("device.draft.record.again.e4", RecordDrafts.keyFor(FormArgs(FormMode.CAFE, againFrom = "e4")))
        assertEquals("device.draft.record.new.cafe", RecordDrafts.keyFor(FormArgs(FormMode.CAFE)))
        val opened = FormMapper.again(cafe, today)
        assertFalse(FormDrafts.changed(opened, opened.copy(againFrom = "")))
        assertTrue(FormDrafts.changed(opened, opened.copy(notes = "오늘은 더 달았다")))
    }

    @Test fun theBanner_saysWhereItCameFrom_andWhatCameAlong() {
        assertEquals(
            "✓ 2026.09.18 · FELT 청계천 기록에서 카페·원두·가격·레시피를 불러왔어요. 오늘 느낀 맛과 점수만 적으면 돼요.",
            AgainTexts.banner("2026.09.18 · FELT 청계천", Category.CAFE, withRecipe = true),
        )
        assertTrue(AgainTexts.banner("2026.09.18", Category.CAFE, withRecipe = false).contains("카페·원두·가격을 불러왔어요"))
        assertTrue(AgainTexts.banner("2026.09.21", Category.BEAN, withRecipe = false).contains("원두 정보·레시피를 불러왔어요"))
        assertTrue(AgainTexts.banner("2026.09.06", Category.CUPPING, withRecipe = false).contains("커핑 종류·장소·원두 정보를 불러왔어요"))
        assertEquals("같은 커피 다시 기록", AgainTexts.button(Category.BEAN))
        assertEquals("같은 원두로 다시 커핑", AgainTexts.button(Category.CUPPING))
    }

    @Test fun aBrew_comesBackWithItsBeanAndRecipe_asABrew() {
        val brew = Entry(
            id = "e1", createdAt = visit, category = Category.BEAN, name = "에티오피아 예가체프 워카 첼베사", roastery = "커피 리브레",
            country = "에티오피아", dripper = "오리가미", grind = "코만단테 24클릭", dose = "15", water = "240", temp = "92",
            steps = listOf(RecipeStep(time = "0:00", water = "40", wait = "30")), actualNotes = "자스민", notes = "좋았다",
            bagPhotos = listOf("bag.jpg"), attributes = ScaScoring.defaultAttributes() + ("flavor" to 8.5),
        )
        val s = FormMapper.again(brew, today, draftId = "new2")
        assertEquals(FormMode.EXTRACT, s.mode)
        assertEquals(Category.BEAN, s.category)
        assertEquals(today, s.createdAt)
        assertEquals("오리가미", s.dripper)
        assertEquals("코만단테 24클릭", s.grind)
        assertEquals("15", s.dose)
        assertEquals(1, s.steps.size)
        assertEquals("2026.09.18", s.againFrom)
        assertTrue(s.repeatBean, "the bag belongs to the first record of the bean")
        assertTrue(s.actualNotes.isEmpty())
        assertEquals("", s.notes)
        assertEquals(ScaScoring.defaultAttributes(), s.attributes)
        assertTrue(s.bagPhotos.none { it.hasImage })
        val saved = FormMapper.toEntry(s, s.draftId, null, emptyList(), today)
        assertEquals(Category.BEAN, saved.category)
        assertEquals(brew.name, saved.name)
        assertEquals("", saved.cafeName)
    }

    @Test fun aCupping_comesBackWithItsBeans_withoutThatDaysNotesScoresAndRanks() {
        val cupping = Entry(
            id = "e5", createdAt = visit, category = Category.CUPPING, name = "커피플랜트 퍼블릭 커핑", cuppingType = CuppingType.PUBLIC,
            cuppingPlace = "커피플랜트 성수", notes = "케냐가 압도적이었다.",
            cuppingBeans = listOf(
                CuppingBean(
                    id = "cb1", name = "케냐 키리냐가 AA", country = "케냐", variety = "SL28", process = "워시드", rank = "1",
                    expectedNotes = "블랙커런트", actualNotes = "토마토", memo = "산미가 밝다", evaluationScores = mapOf("aroma" to 8.0),
                ),
                CuppingBean(id = "cb2", name = "파나마 보케테 게이샤", rank = "2", actualNotes = "자스민"),
            ),
        )
        val s = FormMapper.again(cupping, today, draftId = "new3")
        assertEquals(FormMode.CUPPING, s.mode)
        assertEquals(Category.CUPPING, s.category)
        assertEquals(CuppingType.PUBLIC, s.cuppingType)
        assertEquals("커피플랜트 성수", s.cuppingPlace)
        assertEquals("", s.cuppingNotes)
        assertEquals("2026.09.18 · 커피플랜트 성수", s.againFrom)
        assertFalse(s.repeatBean)
        assertEquals(listOf("케냐 키리냐가 AA", "파나마 보케테 게이샤"), s.cuppingBeans.map { it.name })
        val kenya = s.cuppingBeans.first()
        assertEquals("케냐", kenya.country)
        assertEquals("SL28", kenya.variety)
        assertEquals(listOf("블랙커런트"), kenya.expectedNotes)
        assertEquals("", kenya.id)
        assertEquals("", kenya.rank)
        assertEquals("", kenya.memo)
        assertTrue(kenya.actualNotes.isEmpty())
        assertTrue(kenya.evaluationScores.isEmpty())
        val saved = FormMapper.toEntry(s, s.draftId, null, emptyList(), today)
        assertEquals(2, saved.cuppingBeans.size)
        assertEquals("", saved.notes)
    }
}
