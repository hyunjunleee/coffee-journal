package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.OriginRegions
import com.coffeejournal.domain.rules.Altitude
import com.coffeejournal.domain.rules.EnglishCase
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.RegionHierarchy
import com.coffeejournal.domain.rules.RegionText
import com.coffeejournal.domain.rules.VarietyText
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 국가 · 지역 · 세부 지역 and 재배 고도 in the bean forms: how they are typed, listed, saved and opened again. */
class OriginFieldsTest {
    private val now = Dates.toMillis(LocalDate(2026, 9, 29), 12, 0)

    private fun entryOf(state: FormState) = FormMapper.toEntry(state, "id1", null, emptyList(), now)

    // ───────────── the region text ─────────────

    @Test fun subRegion_isSavedAfterTheRegion_asTheWebsHierarchy_andOpensSplitAgain() {
        assertEquals("시다모, 벤사, 코코세", RegionText.join("시다모", "벤사 › 코코세"))
        listOf("벤사 > 코코세", "벤사 -> 코코세", "벤사→코코세", "벤사, 코코세", "벤사 / 코코세", " 벤사 ›  코코세 ").forEach {
            assertEquals("시다모, 벤사, 코코세", RegionText.join("시다모", it), it)
        }
        assertEquals("시다모", RegionText.join(" 시다모 ", ""))
        assertEquals("벤사, 코코세", RegionText.join("", "벤사 › 코코세"), "without a 지역 the places are kept")
        assertEquals("", RegionText.join("", " › "))
        assertEquals("아파네카-일라마테펙", RegionText.join("아파네카-일라마테펙", ""), "a hyphen is part of a name")
        assertEquals("시다모" to "벤사 › 코코세", RegionText.split("시다모, 벤사, 코코세"))
        assertEquals("Yirgacheffe" to "Gedeb › Worka Chelbesa", RegionText.split("Yirgacheffe, Gedeb, Worka Chelbesa"), "a web record")
        assertEquals("" to "", RegionText.split(""))
        assertEquals("시다모 › 벤사 › 코코세", RegionText.display("시다모, 벤사, 코코세"))
        // the coffee map still groups by the first part
        assertEquals("Sidamo", RegionHierarchy.parse(RegionText.join("시다모", "벤사 › 코코세")).primary)
    }

    @Test fun theRecordForm_savesRegionAndSubRegionTogether_andAltitudeWithItsUnit() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(
            name = "에티오피아 벤사", country = "에티오피아", region = "시다모", subRegion = "벤사 > 코코세", altitude = "1900-2100",
        )
        val e = entryOf(s)
        assertEquals("시다모, 벤사, 코코세", e.region)
        assertEquals("1900-2100m", e.altitude)
        val back = FormMapper.fromEntry(e, FormMode.EXTRACT)
        assertEquals(listOf("시다모", "벤사 › 코코세", "1900-2100"), listOf(back.region, back.subRegion, back.altitude))
        val again = entryOf(back)
        assertEquals(e.region to e.altitude, again.region to again.altitude, "saved again unchanged")
    }

    @Test fun cafeBlendBeans_andCuppingBeans_keepTheirSubRegions() {
        val bean = BeanForm(country = "케냐", region = "니에리", subRegion = "오타야", altitude = "1,800")
        val c = FormMapper.beanComponent(bean)
        assertEquals(listOf("니에리, 오타야", "1,800m"), listOf(c.region, c.altitude))
        val back = FormMapper.beanForm(c)
        assertEquals(listOf("케냐", "니에리", "오타야", "1,800"), listOf(back.country, back.region, back.subRegion, back.altitude))
        val cupping = FormMapper.cuppingBeanModel(CuppingBeanForm(name = "후일라", country = "콜롬비아", region = "우일라", subRegion = "피탈리토", altitude = "1700"))
        assertEquals(listOf("우일라, 피탈리토", "1700m"), listOf(cupping.region, cupping.altitude))
        val form = FormMapper.cuppingBeanForm(CuppingBean(name = "x", region = "Huila, Pitalito", altitude = "1,650 masl"))
        assertEquals(listOf("Huila", "Pitalito", "1,650"), listOf(form.region, form.subRegion, form.altitude))
        assertEquals("" to "", FormMapper.beanComponent(BeanForm()).let { it.region to it.altitude }, "an empty bean stays empty")
    }

    @Test fun autofill_bringsRegionAndSubRegionFromTheSameRecord() {
        val first = entryOf(FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "같은 원두", region = "시다모", subRegion = "벤사", altitude = "2000"))
        val empty = FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "같은 원두")
        val filled = FormMapper.autofill(empty, listOf(first))
        assertEquals(listOf("시다모", "벤사", "2000"), listOf(filled.region, filled.subRegion, filled.altitude))
        val typed = FormMapper.autofill(empty.copy(subRegion = "아로레사"), listOf(first))
        assertEquals("" to "아로레사", typed.region to typed.subRegion, "a started region is not mixed with another record's")
    }

    // ───────────── altitude ─────────────

    @Test fun altitude_takesTheNumber_andKeepsTheUnitOnSave() {
        assertEquals("1950m", Altitude.stored("1950"))
        assertEquals("1,800-2,000m", Altitude.stored(" 1,800-2,000 "))
        assertEquals("1800 ~ 2000m", Altitude.stored("1800 ~ 2000"))
        assertEquals("1950m", Altitude.stored("1950m"), "an m typed or pasted is not doubled")
        assertEquals("", Altitude.stored("  "))
        assertEquals("약 2000m", Altitude.stored("약 2000m"), "an older hand-written value stays as it is")
        listOf("1,950m" to "1,950", "1950 masl" to "1950", "1,950 m.a.s.l." to "1,950", "2000 meters" to "2000", "1800-2000M" to "1800-2000", "2000미터" to "2000")
            .forEach { (stored, field) -> assertEquals(field, Altitude.forField(stored), stored) }
        assertEquals("약 2000m", Altitude.forField("약 2000m"))
        assertEquals("1900-2100", Altitude.typing("1900-2100m"), "the field keeps digits and range signs only")
        assertEquals(" 2000", Altitude.typing("약 2000"))
    }

    // ───────────── the lists ─────────────

    @Test fun countriesList_everyProducingCountry_inKorean_withItsEnglishName() {
        assertEquals(CoffeeCountries.all.size, OriginOptions.countries.size)
        val et = OriginOptions.countries.single { it.value == "에티오피아" }
        assertEquals("Ethiopia", et.note)
        assertTrue(et.matches("ethi") && et.matches("에티"))
    }

    @Test fun regionsList_followsTheCountry_andSubRegionsFollowTheRegion() {
        val sidamo = OriginOptions.regions("에티오피아").single { it.value == "시다모" }
        assertEquals("Sidamo", sidamo.note)
        assertTrue(sidamo.matches("sidama") && sidamo.names("Sidama"), "another spelling finds it")
        assertEquals(OriginOptions.regions("에티오피아"), OriginOptions.regions("Ethiopia"), "Korean or English country")
        val subs = OriginOptions.subRegions("에티오피아", "시다모").map { it.value }
        assertTrue("벤사" in subs && "벤사 › 코코세" in subs, subs.toString())
        assertEquals(OriginOptions.subRegions("에티오피아", "시다모"), OriginOptions.subRegions("Ethiopia", "Sidama"))
        assertTrue(OriginOptions.subRegions("에티오피아", "없는 지역").isEmpty())
        assertTrue(OriginOptions.subRegions("", "").isEmpty())
        // a country not known: every compiled region, each with where it is
        val all = OriginOptions.regions("")
        assertTrue(all.any { it.value == "시다모" && "에티오피아" in it.note })
        assertNotNull(OriginOptions.placeOf("", "시다마"), "the region is found without its country")
        assertNull(OriginOptions.placeOf("케냐", "시다모"), "not in another country")
    }

    @Test fun theCompiledRegions_useThePlacesSeparatorsNowhere() {
        fun names(p: OriginRegions.Place): List<String> = listOf(p.ko, p.en) + p.aliases + p.subs.flatMap(::names)
        val all = OriginRegions.all.flatMap { o -> o.regions.flatMap(::names) }
        assertTrue(all.none { it.isBlank() || ',' in it || '›' in it || '>' in it }, all.filter { it.isBlank() || ',' in it || '›' in it || '>' in it }.toString())
    }

    // ───────────── 품종 and Heirloom numbers ─────────────

    @Test fun heirloomNumbers_areKeptInTheVarietyText_asHeirloomParenthesis() {
        assertEquals("Heirloom(74112, 74158), Mundo Novo", VarietyText.join("Heirloom, Mundo Novo", "74112 74158"))
        assertEquals("Heirloom(74112, 74158)", VarietyText.join("Heirloom", "74112,74158, 74112"), "each number once")
        assertEquals("Typica / Bourbon", VarietyText.join("Typica / Bourbon", ""), "without numbers the varieties stay as typed")
        assertEquals("Bourbon", VarietyText.join("Bourbon", "74110"), "numbers without a Heirloom are not kept")
        assertEquals("Heirloom (에티오피아 재래종)", VarietyText.join("Heirloom (에티오피아 재래종)", "74110"), "a Heirloom with its own parenthesis stays")
        assertEquals("Heirloom, Mundo Novo" to "74112, 74158", VarietyText.split("Heirloom(74112, 74158), Mundo Novo"))
        assertEquals("Ethiopian Heirloom" to "74110", VarietyText.split("Ethiopian Heirloom (74110)"))
        assertEquals("Heirloom (에티오피아 재래종)" to "", VarietyText.split("Heirloom (에티오피아 재래종)"))
        assertEquals("74158, Kurume" to "", VarietyText.split("74158, Kurume"))
        assertTrue(VarietyText.hasHeirloom("Mundo Novo, heirloom") && VarietyText.hasHeirloom("에티오피아 재래종"))
        assertTrue(!VarietyText.hasHeirloom("Bourbon, Typica"))
        assertEquals("74112, 74158 ", VarietyText.typingNumbers("74112, 74158 번"))
    }

    @Test fun heirloomNumbers_goWithTheirOwnHeirloom_andTheRestOfTheTextStaysAsTyped() {
        // a Heirloom with its own parenthesis before the plain one: the numbers go after the plain one
        assertEquals("Heirloom (에티오피아 재래종), Heirloom(74110)", VarietyText.join("Heirloom (에티오피아 재래종), Heirloom", "74110"))
        val kurume = "Heirloom (Kurume), Heirloom(74110)"
        assertEquals("Heirloom (Kurume), Heirloom" to "74110", VarietyText.split(kurume))
        assertEquals(kurume, VarietyText.split(kurume).let { (v, n) -> VarietyText.join(v, n) })
        // separators and spacing as typed
        assertEquals("Typica / Bourbon" to "", VarietyText.split("Typica / Bourbon"))
        assertEquals("Caturra & Castillo" to "", VarietyText.split("Caturra & Castillo"))
        assertEquals("Mundo Novo / Heirloom" to "74158", VarietyText.split("Mundo Novo / Heirloom(74158)"))
        assertEquals("Mundo Novo / Heirloom(74158)", VarietyText.join("Mundo Novo / Heirloom", "74158"))
        // the numbers field is asked for only when there is a Heirloom they can go with
        assertTrue(!VarietyText.hasHeirloom("Heirloom (Kurume)"))
        assertTrue(VarietyText.hasHeirloom("Heirloom (Kurume), heirloom"))
    }

    @Test fun aRecordOpenedAndSavedForAnotherField_keepsItsOriginTextsAsTyped() {
        val typed = listOf("Huila, Pitalito/Acevedo", "1,950 masl", "Typica / Bourbon")
        val e = entryOf(FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "x")).copy(region = typed[0], altitude = typed[1], variety = typed[2])
        val opened = FormMapper.fromEntry(e, FormMode.EXTRACT)
        val saved = entryOf(opened.copy(roastery = "커피 리브레"))
        assertEquals(typed, listOf(saved.region, saved.altitude, saved.variety))
        // a field changed is written the form's way; the others stay as they were
        val edited = entryOf(opened.copy(altitude = "2000"))
        assertEquals(listOf(typed[0], "2000m", typed[2]), listOf(edited.region, edited.altitude, edited.variety))
        // the same for a café blend's beans and a cupping's beans
        val component = FormMapper.beanComponent(FormMapper.beanForm(com.coffeejournal.domain.model.BlendComponent(region = typed[0], altitude = typed[1], variety = typed[2])))
        assertEquals(typed, listOf(component.region, component.altitude, component.variety))
        val cupping = FormMapper.cuppingBeanModel(FormMapper.cuppingBeanForm(CuppingBean(name = "x", region = typed[0], altitude = typed[1], variety = typed[2])))
        assertEquals(typed, listOf(cupping.region, cupping.altitude, cupping.variety))
    }

    @Test fun anOlderAltitudeInFreeText_isEditedAsText() {
        assertTrue(Altitude.isNumeric("") && Altitude.isNumeric("1,950") && Altitude.isNumeric("1800-") && Altitude.isNumeric("1800 ~ 2000"))
        assertTrue(!Altitude.isNumeric("5,000 ft") && !Altitude.isNumeric("약 2000m"))
        assertEquals("5,000 f", Altitude.stored("5,000 f"), "a free text stays as typed, never gets an m")
    }

    @Test fun theRecordForm_savesHeirloomNumbers_andOpensThemApart() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "에티오피아 구지", variety = "Heirloom, Wolisho", heirloomNumbers = "74112, 74158")
        val e = entryOf(s)
        assertEquals("Heirloom(74112, 74158), Wolisho", e.variety)
        val back = FormMapper.fromEntry(e, FormMode.EXTRACT)
        assertEquals("Heirloom, Wolisho" to "74112, 74158", back.variety to back.heirloomNumbers)
        val cupping = FormMapper.cuppingBeanModel(CuppingBeanForm(name = "x", variety = "Heirloom", heirloomNumbers = "74110"))
        assertEquals("Heirloom(74110)", cupping.variety)
        assertEquals("74110", FormMapper.cuppingBeanForm(cupping).heirloomNumbers)
        assertEquals("Heirloom(74165)", FormMapper.beanComponent(BeanForm(variety = "Heirloom", heirloomNumbers = "74165")).variety)
    }

    @Test fun varietyList_englishNamesWithTheirKoreanNames_heirloomNumbersApart() {
        val all = VarietyOptions.varieties
        assertEquals("티피카", all.single { it.value == "Typica" }.note)
        assertTrue(all.any { it.value == "Heirloom" && it.matches("에어룸") }, "Heirloom is found by its Korean name too")
        assertTrue(all.any { it.value == "SL28" } && all.any { it.value == "Sidra" })
        assertEquals(all.size, all.map { it.value.lowercase() }.toSet().size)
        assertTrue(all.none { ',' in it.value })
        assertTrue(VarietyOptions.heirloomNumbers.map { it.value }.containsAll(listOf("74110", "74112", "74158")))
    }

    // ───────────── English words ─────────────

    @Test fun englishWords_startWithACapital_restAsTyped() {
        assertEquals("Yellow Bourbon", EnglishCase.words("yellow bourbon"))
        assertEquals("Finca La Esmeralda", EnglishCase.words("finca la esmeralda"))
        assertEquals("Heirloom(74112, 74158), Mundo Novo", EnglishCase.words("heirloom(74112, 74158), mundo novo"))
        assertEquals("SL28, 74158 Kurume", EnglishCase.words("SL28, 74158 kurume"), "digits and capitals stay")
        assertEquals("에티오피아 Sidama 벤사", EnglishCase.words("에티오피아 sidama 벤사"))
        assertEquals("iPhone", EnglishCase.words("iPhone"), "a word with a capital already stays")
        assertEquals("  Two  Spaces ", EnglishCase.words("  two  spaces "))
        assertEquals("", EnglishCase.words(""))
    }

    @Test fun englishWords_thatCapitalsWouldChange_stayAsTyped() {
        assertEquals("Sul de Minas", EnglishCase.words("sul de minas"))
        assertEquals("Valle del Cauca", EnglishCase.words("valle del cauca"))
        assertEquals("Cup of Excellence", EnglishCase.words("cup of excellence"))
        assertEquals("De La Esperanza", EnglishCase.words("de la esperanza"), "the first word always")
        assertEquals("Comandante 24 clicks", EnglishCase.words("comandante 24 clicks"))
        assertEquals("200 ml, pH 7.1", EnglishCase.words("200 ml, pH 7.1"))
        assertEquals("youtube.com/@coffee", EnglishCase.words("youtube.com/@coffee"))
        assertEquals("me@mail.kr Fritz", EnglishCase.words("me@mail.kr fritz"))
    }

    @Test fun notesTypedIntoTheBox_getTheirCapitals_whenAddedOrSaved() {
        // the chip box's add and the form's save both go through addChips
        assertEquals(listOf("Jasmine", "Earl Grey", "자두"), com.coffeejournal.domain.rules.NoteCanon.addChips(listOf("Jasmine"), "jasmine, earl grey, 자두"))
        val pending = FormState(actualNotes = listOf("자스민"), actualInput = "honey, black tea")
        assertEquals(listOf("자스민", "Honey", "Black Tea"), FormMapper.commitPendingChips(pending).actualNotes)
    }
}
