package com.coffeejournal.domain.reference

/**
 * The SCA Coffee Value Assessment (CVA) cupping forms the app records, checked item by item on 2026-09-26 against the
 * SCA's own material:
 *
 * - SCA Standard 103-2024 "Coffee Value Assessment: Descriptive Assessment" (form: SCA Version 2, June 2024),
 *   https://sca.coffee/s/AW_SCA-103_Descriptive-Assessment_Sept2024_Secured.pdf — §6.2 "The intensity of cupping
 *   sections shall be rated using 15-point scales" (0–15, "the integer number closest to the tick shall be recorded");
 *   §6.3.1 fragrance/aroma CATA "Up to five descriptors"; §6.3.2 flavor/aftertaste: the same olfactory list (up to five)
 *   plus "up to two main tastes"; §6.3.3 mouthfeel CATA "up to two options"; §6.3.4 acidity and sweetness have no CATA
 *   list, only freely elicited descriptors (notes). Form §8.2 gives the lists below.
 * - SCA Standard 104-2024 "Coffee Value Assessment: Affective Assessment" (form: SCA Version 2, June 2024),
 *   https://sca.coffee/s/AW_SCA-104_Affective-Assessment_Sept2024_Secured.pdf — §5.2 the 9-point impression-of-quality
 *   scale (① extremely low … ⑨ extremely high) for fragrance, aroma, flavor, aftertaste, acidity, sweetness, mouthfeel
 *   and overall; §5.4 five cups per sample, non-uniform and defective cups, defect types moldy / phenolic / potato;
 *   §5.5 the cupping score (see [com.coffeejournal.domain.rules.CvaScoring]).
 * - Korean labels: the SCA's own Korean CVA forms, https://sca.coffee/s/CVA-Cupping-Forms-Korean.pdf
 *   (묘사 평가 / 정동 평가, SCA 버전 2 (2024년 6월)).
 * - Score calculator: https://sca.coffee/cuppingscore (Calconic embed 64382a2047d53f001e16f7a7).
 */
object CvaForm {
    /** A cupping section. [intensity]: rated 0–15 on the descriptive form; every section is rated 1–9 on the affective form. */
    data class Section(val key: String, val en: String, val ko: String, val intensity: Boolean = true)

    val sections: List<Section> = listOf(
        Section("fragrance", "Fragrance", "프레그런스"),
        Section("aroma", "Aroma", "아로마"),
        Section("flavor", "Flavor", "플레이버"),
        Section("aftertaste", "Aftertaste", "애프터테이스트"),
        Section("acidity", "Acidity", "산미"),
        Section("sweetness", "Sweetness", "단맛"),
        Section("mouthfeel", "Mouthfeel", "마우스필"),
        Section("overall", "Overall", "오버롤(전체적 인상)", intensity = false),
    )

    val sectionKeys: List<String> = sections.map { it.key }
    val intensitySections: List<Section> = sections.filter { it.intensity }
    fun section(key: String): Section = sections.first { it.key == key }

    /** SCA-103 §6.2: 0 (low) to 15 (high), whole numbers. */
    const val INTENSITY_MIN = 0
    const val INTENSITY_MAX = 15

    /** SCA-104 §5.2: the 9-point impression-of-quality scale. */
    const val AFFECTIVE_MIN = 1
    const val AFFECTIVE_MAX = 9

    /** SCA-104 §5.4: five cups per sample. */
    const val CUPS = 5

    /** SCA-104 Figure 1 / the Korean form's 품질 인상 rubric, ① to ⑨. */
    val qualityLabels: List<String> = listOf(
        "극히 낮음", "매우 낮음", "적당히 낮음", "약간 낮음", "높지도 낮지도 않음", "약간 높음", "적당히 높음", "매우 높음", "극히 높음",
    )

    fun qualityLabel(score: Int): String? = qualityLabels.getOrNull(score - 1)

    /** A check-all-that-apply term; [children] are the narrower boxes printed after a category box on the form. */
    data class Descriptor(val id: String, val en: String, val ko: String, val children: List<Descriptor> = emptyList())

    private fun d(id: String, en: String, ko: String, vararg children: Descriptor) = Descriptor(id, en, ko, children.toList())

    /** The olfactory CATA list (fragrance/aroma box and the retronasal list of the flavor/aftertaste box), SCA-103 §8.2. */
    val olfactory: List<Descriptor> = listOf(
        d("floral", "Floral", "꽃"),
        d("fruity", "Fruity", "과일", d("berry", "Berry", "베리"), d("driedFruit", "Dried Fruit", "말린 과일"), d("citrusFruit", "Citrus Fruit", "감귤류")),
        d("sourFermented", "Sour/Fermented", "신맛/발효된", d("sour", "Sour", "신맛"), d("fermented", "Fermented", "발효된")),
        d("greenVegetative", "Green/Vegetative", "녹색채소/식물성"),
        d("other", "Other", "기타", d("chemical", "Chemical", "화학적인"), d("mustyEarthy", "Musty/Earthy", "퀴퀴한/흙냄새"), d("woody", "Woody", "나무 같은")),
        d("roasted", "Roasted", "구운", d("cereal", "Cereal", "곡류"), d("burnt", "Burnt", "탄"), d("tobacco", "Tobacco", "담배")),
        d("nuttyCocoa", "Nutty/Cocoa", "견과류/코코아", d("nutty", "Nutty", "견과류"), d("cocoa", "Cocoa", "코코아")),
        d("spice", "Spice", "향신료"),
        d("sweet", "Sweet", "달콤한", d("vanilla", "Vanilla/Vanillin", "바닐라/바닐린"), d("brownSugar", "Brown Sugar", "브라운슈가")),
    )

    /** "Main Tastes (2)" of the flavor/aftertaste box, SCA-103 §6.3.2. */
    val mainTastes: List<Descriptor> = listOf(
        d("salty", "Salty", "짠맛"), d("sour", "Sour", "신맛"), d("sweet", "Sweet", "단맛"), d("bitter", "Bitter", "쓴맛"), d("umami", "Umami", "감칠맛"),
    )

    /** The mouthfeel CATA list, SCA-103 §5.3 / §8.2. */
    val mouthfeel: List<Descriptor> = listOf(
        d("rough", "Rough (Gritty, Chalky, Sandy)", "거친 (껄끄러운, 가루같은, 모래같은)"),
        d("oily", "Oily", "기름진"),
        d("smooth", "Smooth (Velvety, Silky, Syrupy)", "부드러운 (벨벳같은, 매끄러운, 시럽같은)"),
        d("mouthDrying", "Mouth-Drying", "입안이 마르는"),
        d("metallic", "Metallic", "금속성"),
    )

    /** Sensory defect types on the affective form, SCA-104 §5.4.1. */
    val defects: List<Descriptor> = listOf(d("moldy", "Moldy", "곰팡이"), d("phenolic", "Phenolic", "페놀"), d("potato", "Potato", "감자"))

    /** Selection limits, SCA-103 §6.3.1–6.3.3. */
    const val MAX_OLFACTORY = 5
    const val MAX_MAIN_TASTES = 2
    const val MAX_MOUTHFEEL = 2

    /** Every olfactory term, categories and their narrower boxes, in form order. */
    val olfactoryFlat: List<Descriptor> = olfactory.flatMap { listOf(it) + it.children }

    fun label(list: List<Descriptor>, id: String): String = list.firstOrNull { it.id == id }?.ko ?: id

    fun olfactoryLabel(id: String): String = label(olfactoryFlat, id)

    /** The note boxes: one per box of the forms (fragrance and aroma share one, as do flavor and aftertaste). */
    data class NoteBox(val key: String, val ko: String, val sections: List<String>)

    val noteBoxes: List<NoteBox> = listOf(
        NoteBox("aroma", "프레그런스 · 아로마", listOf("fragrance", "aroma")),
        NoteBox("flavor", "플레이버 · 애프터테이스트", listOf("flavor", "aftertaste")),
        NoteBox("acidity", "산미", listOf("acidity")),
        NoteBox("sweetness", "단맛", listOf("sweetness")),
        NoteBox("mouthfeel", "마우스필", listOf("mouthfeel")),
        NoteBox("overall", "오버롤(전체적 인상)", listOf("overall")),
    )
}
