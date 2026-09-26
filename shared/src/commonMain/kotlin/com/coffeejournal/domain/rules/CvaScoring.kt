package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.reference.CvaForm
import kotlinx.serialization.Serializable
import kotlin.math.floor

/**
 * One SCA CVA assessment of a coffee: the descriptive part (SCA-103: section intensities 0–15 and check-all-that-apply
 * descriptors) and the affective part (SCA-104: impression of quality 1–9 per section, non-uniform and defective cups,
 * defect types). Either part may be left out, as the standards allow.
 */
@Serializable
data class CvaAssessment(
    /** Descriptive intensity 0–15 by section key (overall has none). */
    val intensity: Map<String, Int> = emptyMap(),
    /** Affective impression of quality 1–9 by section key. */
    val affective: Map<String, Int> = emptyMap(),
    /** Fragrance/aroma CATA ids (up to five). */
    val aromaDescriptors: List<String> = emptyList(),
    /** Flavor/aftertaste retronasal CATA ids (up to five). */
    val flavorDescriptors: List<String> = emptyList(),
    /** Flavor/aftertaste main tastes (up to two). */
    val mainTastes: List<String> = emptyList(),
    /** Mouthfeel CATA ids (up to two). */
    val mouthfeel: List<String> = emptyList(),
    /** Notes by [CvaForm.noteBoxes] key. */
    val notes: Map<String, String> = emptyMap(),
    val nonUniformCups: Int = 0,
    val defectiveCups: Int = 0,
    val defects: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = intensity.isEmpty() && affective.isEmpty() && aromaDescriptors.isEmpty() && flavorDescriptors.isEmpty() &&
            mainTastes.isEmpty() && mouthfeel.isEmpty() && notes.values.all { it.isBlank() } &&
            nonUniformCups == 0 && defectiveCups == 0 && defects.isEmpty()
}

/**
 * CVA affective score and the storage of a [CvaAssessment] inside the record's existing JSON maps.
 *
 * Score — SCA Standard 104-2024 §5.5 "The Cupping Score" (https://sca.coffee/s/AW_SCA-104_Affective-Assessment_Sept2024_Secured.pdf):
 *   "It is calculated using the following equation, rounded to the nearest 0.25 points:
 *    S = 0.65625 · Σ(i=1..8) h_i + 52.75 − 2u − 4d
 *    Where: S is the cupping score prior to rounding; h_i is the 9-point score of each affective section, from i = 1
 *    (fragrance) to i = 8 (overall); u is the number of non-uniform cups; d is the number of defective cups."
 * The SCA's calculator (https://sca.coffee/cuppingscore, Calconic 64382a2047d53f001e16f7a7) computes the same:
 *   "Unrounded Affective Score" = (#1+#2+#3+#4+#5+#6+#7+#11)*.65625+52.75-(#8*2)-(#9*4), "Affective Score" = round(#10/.25)*.25,
 * with the eight sections 1–9 and both cup counts 0–5. The standard's §7.1 two-way table (Σ 8 → 58.00, 12 → 60.75,
 * 40 → 79.00, 44 → 81.75, 72 → 100.00) rounds halves up, which [affectiveScore] does too.
 * §5.4.1: "Whenever a defect is found … two fields shall be filled out: the defective cups and the defect type (potato,
 * moldy, or phenolic). If any of these two fields are not properly filled out, the coffee shall not be counted as
 * defective." — so defective cups only count once a defect type is chosen.
 *
 * Storage (no schema change, web backups stay importable): numbers go into the numeric map the record already has
 * (entries.attributes, a cupping bean's evaluationScores) and text into its string map (entries.attributeNotes, a
 * cupping bean's evaluation), all under keys starting with [PREFIX]. The web sums only its ten 2004 attribute keys and
 * reads its own evaluation keys, so it ignores these; the backup codec round-trips unknown keys unchanged.
 */
object CvaScoring {
    const val PREFIX = "cva."
    private const val INTENSITY = "cva.intensity."
    private const val AFFECTIVE = "cva.affective."
    const val NON_UNIFORM = "cva.cups.nonUniform"
    const val DEFECTIVE = "cva.cups.defective"
    private const val NOTE = "cva.note."
    const val AROMA_CATA = "cva.cata.aroma"
    const val FLAVOR_CATA = "cva.cata.flavor"
    const val MAIN_TASTES = "cva.cata.mainTastes"
    const val MOUTHFEEL_CATA = "cva.cata.mouthfeel"
    const val DEFECTS = "cva.defects"

    const val WEIGHT = 0.65625
    const val OFFSET = 52.75
    const val NON_UNIFORM_PENALTY = 2.0
    const val DEFECTIVE_PENALTY = 4.0

    fun isCvaKey(key: String): Boolean = key.startsWith(PREFIX)

    /** The number of affective sections rated (0–8). */
    fun affectiveCount(a: CvaAssessment): Int = CvaForm.sectionKeys.count { a.affective[it] in CvaForm.AFFECTIVE_MIN..CvaForm.AFFECTIVE_MAX }

    /** Defective cups that count: none unless a defect type was chosen (SCA-104 §5.4.1). */
    fun countedDefectiveCups(a: CvaAssessment): Int = if (a.defects.isEmpty()) 0 else a.defectiveCups.coerceIn(0, CvaForm.CUPS)

    /** Σ h_i of the eight sections, or null unless all eight were rated. */
    fun affectiveSum(a: CvaAssessment): Int? {
        if (affectiveCount(a) < CvaForm.sectionKeys.size) return null
        return CvaForm.sectionKeys.sumOf { a.affective.getValue(it) }
    }

    /** SCA-104 §5.5: the score before rounding. */
    fun unroundedScore(sum: Int, nonUniform: Int, defective: Int): Double =
        WEIGHT * sum + OFFSET - NON_UNIFORM_PENALTY * nonUniform - DEFECTIVE_PENALTY * defective

    /** Rounded to the nearest 0.25, halves up (the §7.1 table). */
    fun roundQuarter(s: Double): Double = floor(s / 0.25 + 0.5) * 0.25

    /** The CVA affective (cupping) score, or null unless all eight sections were rated. */
    fun affectiveScore(a: CvaAssessment): Double? {
        val sum = affectiveSum(a) ?: return null
        return roundQuarter(unroundedScore(sum, a.nonUniformCups.coerceIn(0, CvaForm.CUPS), countedDefectiveCups(a)))
    }

    // ---------- storage in the record's maps ----------

    /** Whether the maps hold a CVA assessment (any `cva.` key). */
    fun present(scores: Map<String, Double>, texts: Map<String, String>): Boolean =
        scores.keys.any(::isCvaKey) || texts.any { (k, v) -> isCvaKey(k) && v.isNotBlank() }

    /** The numbers of [a] as `cva.` score keys. Ranges are enforced; missing ratings are left out. */
    fun toScores(a: CvaAssessment): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        CvaForm.intensitySections.forEach { s ->
            a.intensity[s.key]?.takeIf { it in CvaForm.INTENSITY_MIN..CvaForm.INTENSITY_MAX }?.let { out[INTENSITY + s.key] = it.toDouble() }
        }
        CvaForm.sections.forEach { s ->
            a.affective[s.key]?.takeIf { it in CvaForm.AFFECTIVE_MIN..CvaForm.AFFECTIVE_MAX }?.let { out[AFFECTIVE + s.key] = it.toDouble() }
        }
        a.nonUniformCups.coerceIn(0, CvaForm.CUPS).takeIf { it > 0 }?.let { out[NON_UNIFORM] = it.toDouble() }
        a.defectiveCups.coerceIn(0, CvaForm.CUPS).takeIf { it > 0 }?.let { out[DEFECTIVE] = it.toDouble() }
        return out
    }

    /** The descriptors and notes of [a] as `cva.` text keys (descriptor ids joined with commas). */
    fun toTexts(a: CvaAssessment): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        fun ids(key: String, list: List<String>) { if (list.isNotEmpty()) out[key] = list.joinToString(",") }
        ids(AROMA_CATA, a.aromaDescriptors)
        ids(FLAVOR_CATA, a.flavorDescriptors)
        ids(MAIN_TASTES, a.mainTastes)
        ids(MOUTHFEEL_CATA, a.mouthfeel)
        ids(DEFECTS, a.defects)
        CvaForm.noteBoxes.forEach { box -> a.notes[box.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { out[NOTE + box.key] = it } }
        return out
    }

    /** Reads an assessment back from the maps; values out of range or of unknown ids are dropped. */
    fun fromMaps(scores: Map<String, Double>, texts: Map<String, String>): CvaAssessment {
        fun int(key: String, range: IntRange): Int? {
            val v = scores[key]?.takeIf { it.isFinite() } ?: return null
            val i = v.toInt()
            return if (i.toDouble() == v && i in range) i else null
        }
        fun ids(key: String, allowed: Collection<String>, max: Int): List<String> =
            (texts[key] ?: "").split(',').map { it.trim() }.filter { it in allowed }.distinct().take(max)
        return CvaAssessment(
            intensity = CvaForm.intensitySections.mapNotNull { s -> int(INTENSITY + s.key, CvaForm.INTENSITY_MIN..CvaForm.INTENSITY_MAX)?.let { s.key to it } }.toMap(),
            affective = CvaForm.sections.mapNotNull { s -> int(AFFECTIVE + s.key, CvaForm.AFFECTIVE_MIN..CvaForm.AFFECTIVE_MAX)?.let { s.key to it } }.toMap(),
            aromaDescriptors = ids(AROMA_CATA, CvaForm.olfactoryFlat.map { it.id }, CvaForm.MAX_OLFACTORY),
            flavorDescriptors = ids(FLAVOR_CATA, CvaForm.olfactoryFlat.map { it.id }, CvaForm.MAX_OLFACTORY),
            mainTastes = ids(MAIN_TASTES, CvaForm.mainTastes.map { it.id }, CvaForm.MAX_MAIN_TASTES),
            mouthfeel = ids(MOUTHFEEL_CATA, CvaForm.mouthfeel.map { it.id }, CvaForm.MAX_MOUTHFEEL),
            notes = CvaForm.noteBoxes.mapNotNull { box -> texts[NOTE + box.key]?.takeIf { it.isNotBlank() }?.let { box.key to it } }.toMap(),
            nonUniformCups = int(NON_UNIFORM, 0..CvaForm.CUPS) ?: 0,
            defectiveCups = int(DEFECTIVE, 0..CvaForm.CUPS) ?: 0,
            defects = ids(DEFECTS, CvaForm.defects.map { it.id }, CvaForm.defects.size),
        )
    }

    /**
     * The CVA score stored in a record's maps, or null (not CVA, or not all eight sections rated). The text map is
     * needed too: defective cups only count with a defect type, which is stored there.
     */
    fun scoreOf(scores: Map<String, Double>, texts: Map<String, String>): Double? =
        if (scores.keys.none(::isCvaKey)) null else affectiveScore(fromMaps(scores, texts))

    /** [scoreOf] a record's tasting (entries.attributes / attributeNotes). */
    fun scoreOf(entry: Entry): Double? = scoreOf(entry.attributes, entry.attributeNotes)

    /** "84.25" style (two decimals, like the SCA calculator). */
    fun format(score: Double): String = ScaScoring.format2(score)

    /**
     * The score line of a record wherever the 2004 total used to be the only one: "83.50 / 100" for an SCA 2004 score,
     * "CVA 84.25 / 100" for a CVA affective score, null for neither. A record keeps one form, so at most one applies.
     */
    fun scoreText(scores: Map<String, Double>, texts: Map<String, String>): String? {
        val finite = scores.filterValues { it.isFinite() }
        ScaScoring.effectiveTotal(finite)?.let { return "${ScaScoring.format2(it)} / 100" }
        return scoreOf(finite, texts)?.let { "CVA ${format(it)} / 100" }
    }

    /** [scoreText] of a record's tasting. */
    fun scoreText(entry: Entry): String? = scoreText(entry.attributes, entry.attributeNotes)
}
