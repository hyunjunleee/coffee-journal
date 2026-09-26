package com.coffeejournal.ui.bean.a

import com.coffeejournal.domain.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals

/** beanA-7 (web 'ko' sorts), beanA-11 (country formatting), beanA-12 (roastery suffix), beanB-5 (breakdown counts). */
class BeanParityFixesTest {
    @Test fun noteCloudAlphaSort_putsHangulFirstAndIgnoresCase() {
        val records = listOf(rec("1", expectedNotes = "Stone fruit, 자스민, acerola, Bergamot, Banana, 가나"))
        val cloud = NoteStats.aggregate(records, NoteStats.EXPECTED).values
        assertEquals(
            listOf("가나", "자스민", "acerola", "Banana", "Bergamot", "Stone fruit"), // "apple" would be folded into 사과
            NoteStats.filterAndSort(cloud, "", NoteStats.SORT_ALPHA).map { it.label },
        )
    }

    @Test fun processTieBreaks_useTheKoreanOrder() {
        val honey = listOf(
            rec("w", process = "허니(White)"), rec("b", process = "허니(black)"),
            rec("k", process = "허니(카라멜)"), rec("a", process = "허니(Aged)"),
        )
        // one record each, so the name decides: Hangul first, then Latin without case
        assertEquals(listOf("카라멜", "Aged", "black", "White"), ProcessStats.honeyGroups(honey).map { it.first }.filter { it in setOf("카라멜", "Aged", "black", "White") })
        val search = ProcessStats.search(listOf(rec("1", process = "Zeta honey"), rec("2", process = "alpha honey"), rec("3", process = "허니 zz")), "zz")
        assertEquals(listOf("허니 zz"), search.map { it.label })
        val mixed = ProcessStats.search(listOf(rec("1", process = "Zeta x"), rec("2", process = "alpha x"), rec("3", process = "가 x")), " x")
        assertEquals(listOf("가 x", "alpha x", "Zeta x"), mixed.map { it.label })
    }

    @Test fun varietyByCountry_sortsCountriesLikeTheWeb() {
        val records = listOf(
            rec("1", variety = "Geisha", country = "Wakanda"), // unknown country: kept as typed
            rec("2", variety = "Geisha", country = "케냐"),
            rec("3", variety = "Geisha", country = "atlantis"),
            rec("4", variety = "Geisha", country = "Brazil"),
        )
        assertEquals(listOf("브라질(Brazil)", "케냐(Kenya)", "atlantis", "Wakanda"), VarietyStats.byCountry(records).map { it.country })
    }

    @Test fun origins_countUnknownCountryAsOne() {
        val records = listOf(rec("1", country = "Ethiopia"), rec("2", country = "에티오피아"), rec("3", country = ""), rec("4", country = " "))
        // web: new Set(records.map(formatCountryBilingual)) → {에티오피아(Ethiopia), 국가 미상}
        assertEquals(2, VarietyStats.origins(records))
    }

    @Test fun kindWithPlace_showsTheNamesRoasteryForHomeBrews() {
        assertEquals("직접 내림 · 프릳츠", BeanFormat.kindWithPlace(rec("1", name = "에티오피아 구지 (프릳츠)")))
        assertEquals("직접 내림 · 프릳츠", BeanFormat.kindWithPlace(rec("2", name = "구지 (프릳츠, 노르딕)", category = "")))
        assertEquals("직접 내림", BeanFormat.kindWithPlace(rec("3", name = "에티오피아 구지")))
        assertEquals("카페 · 글리치", BeanFormat.kindWithPlace(rec("4", name = "케냐 (리브레)", category = Category.CAFE, cafeName = "글리치")))
        assertEquals("커핑 · 커피플랜트", BeanFormat.kindWithPlace(rec("5", name = "파나마 (리브레)", category = Category.CUPPING, cuppingPlace = "커피플랜트", parentEntryId = "s")))
    }

    @Test fun categoryCounts_countBrewsPerBeanAndVisitsOneByOne() {
        val records = listOf(
            rec("1", name = "벤사 (리브레)"), rec("2", name = "벤사"), rec("3", name = "구지", category = ""),
            rec("4", name = "케냐", category = Category.CAFE, cafeName = "듀잇"), rec("5", name = "케냐", category = Category.CAFE, cafeName = "듀잇"),
            rec("6", name = "파나마", category = Category.CUPPING, parentEntryId = "s"),
        )
        assertEquals(listOf(Category.BEAN to 2, Category.CAFE to 2, Category.CUPPING to 1), BeanFormat.categoryCounts(records))
        assertEquals("2종", BeanFormat.countUnit(Category.BEAN, 2))
        assertEquals("1번", BeanFormat.countUnit(Category.CUPPING, 1))
        assertEquals(emptyList(), BeanFormat.categoryCounts(emptyList()))
    }
}
