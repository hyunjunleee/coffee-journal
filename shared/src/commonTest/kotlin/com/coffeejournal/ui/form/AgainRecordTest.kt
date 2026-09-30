package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
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

    @Test fun theBanner_saysWhereItCameFrom() {
        assertEquals(
            "✓ 2026.09.18 · FELT 청계천 기록에서 카페·원두·가격·레시피를 불러왔어요. 오늘 마신 느낌과 점수만 적으면 돼요.",
            AgainTexts.banner("2026.09.18 · FELT 청계천", withRecipe = true),
        )
        assertTrue(AgainTexts.banner("2026.09.18", withRecipe = false).contains("카페·원두·가격을 불러왔어요"))
    }
}
