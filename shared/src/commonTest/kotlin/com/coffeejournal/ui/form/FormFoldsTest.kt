package com.coffeejournal.ui.form

import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.nav.FormMode
import com.coffeejournal.ui.notify.MemorySettingsDao
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The record form's folding parts: which a kind of record has, how they are kept, and what a folded one shows. */
class FormFoldsTest {
    private val now = Dates.toMillis(LocalDate(2026, 9, 30), 9, 0)

    @Test fun eachKindOfRecord_foldsItsOwnParts() {
        assertEquals(
            listOf(FormFold.ORIGIN, FormFold.BAG_PHOTOS, FormFold.RECIPE, FormFold.SCORE, FormFold.NOTES, FormFold.MEMO),
            FormFold.parts(Category.BEAN),
        )
        // a café's recipe has its own fold, which also decides whether it is saved
        assertEquals(listOf(FormFold.ORIGIN, FormFold.SCORE, FormFold.NOTES, FormFold.MEMO), FormFold.parts(Category.CAFE))
        assertEquals(listOf(FormFold.CUPPING_BEAN_INFO, FormFold.CUPPING_BEAN_NOTES, FormFold.CUPPING_EVALUATION), FormFold.parts(Category.CUPPING))
        assertEquals("brew.score", FormFold.key(Category.BEAN, FormFold.SCORE))
        assertEquals("cafe.score", FormFold.key(Category.CAFE, FormFold.SCORE))
    }

    @Test fun nothingIsFolded_untilFolded_andOnlyForThatKind() {
        val folds = mapOf(FormFold.key(Category.BEAN, FormFold.SCORE) to true)
        assertTrue(FormFold.isFolded(folds, Category.BEAN, FormFold.SCORE))
        assertFalse(FormFold.isFolded(folds, Category.CAFE, FormFold.SCORE))
        assertFalse(FormFold.isFolded(emptyMap(), Category.BEAN, FormFold.MEMO))
        // a cupping bean's evaluation sheet starts folded, as it always did, until unfolded once
        assertTrue(FormFold.isFolded(emptyMap(), Category.CUPPING, FormFold.CUPPING_EVALUATION))
        assertFalse(FormFold.isFolded(mapOf("cupping.cuppingEvaluation" to false), Category.CUPPING, FormFold.CUPPING_EVALUATION))
    }

    @Test fun foldAll_andUnfoldAll_touchOneKindOnly() {
        val start = mapOf("cafe.memo" to true)
        val all = FormFold.all(start, Category.BEAN, folded = true)
        assertTrue(FormFold.parts(Category.BEAN).all { FormFold.isFolded(all, Category.BEAN, it) })
        assertTrue(FormFold.isFolded(all, Category.CAFE, FormFold.MEMO))
        val none = FormFold.all(all, Category.BEAN, folded = false)
        assertTrue(FormFold.parts(Category.BEAN).none { FormFold.isFolded(none, Category.BEAN, it) })
        assertTrue(FormFold.isFolded(none, Category.CAFE, FormFold.MEMO))
    }

    @Test fun keptAsOneLine_andAnUnreadableOneIsIgnored() {
        val folds = mapOf("brew.score" to true, "cafe.memo" to false, "cupping.cuppingBeanInfo" to true)
        assertEquals("brew.score=1,cafe.memo=0,cupping.cuppingBeanInfo=1", FormFold.encode(folds))
        assertEquals(folds, FormFold.decode(FormFold.encode(folds)))
        assertEquals(mapOf("brew.memo" to true), FormFold.decode("brew.memo=1,garbage,=1,brew.x=2"))
        assertEquals(emptyMap(), FormFold.decode(null))
    }

    @Test fun theStore_keepsTheFolds_onThisDevice() = runTest {
        val settings = SettingsRepository(MemorySettingsDao())
        val store = FormFoldStore(settings, this)
        assertEquals(emptyMap(), store.load())
        store.save(mapOf("brew.score" to true)).join()
        assertEquals(mapOf("brew.score" to true), FormFoldStore(settings, this).load())
        assertTrue(SettingsRepository.isDeviceKey(FormFoldStore.KEY), "not carried by backups")
    }

    @Test fun aFoldedPart_saysWhatItHolds() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(
            country = "에티오피아", region = "시다모", subRegion = "벤사 › 코코세", variety = "Heirloom", process = "워시드", roast = "라이트",
            roastery = "커피 리브레", dripper = "오리가미", dose = "15", water = "240", temp = "92", time = "2:30",
            actualNotes = listOf("자스민", "복숭아"), notes = "\n쥬시하고 깨끗하다.\n다음엔 1도 낮게",
        )
        assertEquals("에티오피아 · 시다모 › 벤사 › 코코세 · Heirloom · 워시드 · 라이트 · 커피 리브레", FormFold.originLine(s))
        assertEquals("오리가미 · 15g · 물 240g · 92°C · 2:30 · 단계 ${s.steps.size}개", FormFold.recipeLine(s))
        assertEquals("자스민, 복숭아", FormFold.notesLine(s))
        assertEquals("쥬시하고 깨끗하다.", FormFold.memoLine(s))
        assertEquals("", FormFold.bagPhotosLine(s))
        // an untouched sheet says nothing; a scored one its total
        assertEquals("", FormFold.scoreLine(s))
        val scored = s.copy(attributes = ScaScoring.defaultAttributes() + ("flavor" to 8.0))
        assertEquals("SCA 2004 ${ScaScoring.format2(ScaScoring.effectiveTotal(scored.attributes)!!)}", FormFold.scoreLine(scored))
    }

    @Test fun aFoldedCuppingBean_saysWhatItHolds() {
        val b = CuppingBeanForm(
            name = "케냐 키리냐가 AA", country = "케냐", region = "Kirinyaga", variety = "SL28", process = "워시드", roast = "라이트", rank = "1",
            expectedNotes = listOf("블랙커런트"), actualNotes = listOf("토마토", "자몽"),
        )
        assertEquals("케냐 · Kirinyaga · SL28 · 워시드 · 라이트 · 순위 1", FormFold.cuppingBeanInfoLine(b))
        assertEquals("예상 블랙커런트 · 마신 토마토, 자몽", FormFold.cuppingBeanNotesLine(b))
        assertEquals("", FormFold.cuppingBeanNotesLine(CuppingBeanForm()))
        assertEquals("SCA 2004 · 2개 항목", FormFold.cuppingEvaluationLine(b.copy(evaluationScores = mapOf("aroma" to 8.0, "flavor" to 7.75))))
        assertEquals("", FormFold.cuppingEvaluationLine(b))
    }
}
