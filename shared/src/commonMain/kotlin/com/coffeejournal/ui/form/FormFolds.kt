package com.coffeejournal.ui.form

import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.RegionText
import com.coffeejournal.domain.rules.ScaScoring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The parts of the record form that fold ([FormState.category] decides which there are): each has a header that
 * folds and unfolds it and, folded, a line of what it holds. What is folded is remembered per kind of record on this
 * device ([FormFoldStore]); a folded part keeps its values and they are saved as ever.
 */
object FormFold {
    /** 원두 상세 정보: the green-coffee fields, a blend's beans, the bag's weight and arrival, expected notes, the shop's text. */
    const val ORIGIN = "origin"
    const val BAG_PHOTOS = "bagPhotos"
    /** A brew's recipe (a café's has its own "레시피 입력" fold, which also decides whether it is saved). */
    const val RECIPE = "recipe"
    /** SCA 커핑 평가: the SCA 2004 or CVA sheet. */
    const val SCORE = "score"
    /** The flavor wheel and 내가 느낀 노트. */
    const val NOTES = "notes"
    const val MEMO = "memo"
    /** A cupping bean's origin, farm, variety, price, rank, process and roast (every bean card together). */
    const val CUPPING_BEAN_INFO = "cuppingBeanInfo"
    /** A cupping bean's expected and tasted notes. */
    const val CUPPING_BEAN_NOTES = "cuppingBeanNotes"
    /** A cupping bean's 항목별 평가 (SCA 2004 or CVA); folded until unfolded once, as it always started. */
    const val CUPPING_EVALUATION = "cuppingEvaluation"

    const val FOLD_ALL = "모두 접기"
    const val UNFOLD_ALL = "모두 펼치기"

    /** The parts a record of [category] has. */
    fun parts(category: String): List<String> = when (category) {
        Category.CUPPING -> listOf(CUPPING_BEAN_INFO, CUPPING_BEAN_NOTES, CUPPING_EVALUATION)
        Category.CAFE -> listOf(ORIGIN, SCORE, NOTES, MEMO)
        else -> listOf(ORIGIN, BAG_PHOTOS, RECIPE, SCORE, NOTES, MEMO)
    }

    /** "brew.score": a part of one kind of record. */
    fun key(category: String, part: String): String = "${kind(category)}.$part"

    fun isFolded(folds: Map<String, Boolean>, category: String, part: String): Boolean =
        key(category, part).let { folds[it] ?: (it in FOLDED_AT_FIRST) }

    /** Folded until the user unfolds them: a cupping bean's evaluation sheet (every bean card would be very long). */
    private val FOLDED_AT_FIRST = setOf("cupping.$CUPPING_EVALUATION")

    /** Every part of [category] folded ([folded]) or unfolded, the other kinds' left as they are. */
    fun all(folds: Map<String, Boolean>, category: String, folded: Boolean): Map<String, Boolean> =
        folds + parts(category).associate { key(category, it) to folded }

    private fun kind(category: String): String = when (category) {
        Category.CAFE -> "cafe"
        Category.CUPPING -> "cupping"
        else -> "brew"
    }

    /** "brew.score=1,cafe.memo=0" in the settings table. */
    fun encode(folds: Map<String, Boolean>): String = folds.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${if (it.value) 1 else 0}" }

    fun decode(raw: String?): Map<String, Boolean> =
        raw.orEmpty().split(',').mapNotNull { pair ->
            val (k, v) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            if (k.isBlank() || v !in setOf("0", "1")) null else k.trim() to (v == "1")
        }.toMap()

    // ---------- what a folded part shows ----------

    private fun line(vararg parts: String?): String = parts.mapNotNull { it?.trim()?.takeIf { p -> p.isNotEmpty() } }.joinToString(" · ")

    fun originLine(s: FormState): String = line(
        s.country, RegionText.display(RegionText.join(s.region, s.subRegion)), s.farmProducer, s.variety, s.process,
        s.roast, s.roastery, if (s.blendBeans.isNotEmpty()) "블렌드 ${1 + s.blendBeans.size}종" else null,
    )

    fun bagPhotosLine(s: FormState): String = s.bagPhotos.count { it.hasImage }.takeIf { it > 0 }?.let { "사진 ${it}장" } ?: ""

    fun recipeLine(s: FormState): String = line(
        s.dripper, s.dose.takeIf { it.isNotBlank() }?.let { "${it}g" }, s.water.takeIf { it.isNotBlank() }?.let { "물 ${it}g" },
        s.temp.takeIf { it.isNotBlank() }?.let { "${it}°C" }, s.time, s.steps.size.takeIf { it > 0 }?.let { "단계 ${it}개" },
    )

    /** The score as the sheet shows it; nothing while the sheet is untouched. */
    fun scoreLine(s: FormState): String = if (s.scoreForm == ScoreForm.CVA) {
        CvaScoring.affectiveScore(s.cva)?.let { "CVA ${CvaScoring.format(it)}" } ?: ""
    } else {
        ScaScoring.effectiveTotal(s.attributes)?.let { "SCA 2004 ${ScaScoring.format2(it)}" } ?: ""
    }

    fun notesLine(s: FormState): String = s.actualNotes.joinToString(", ")

    fun memoLine(s: FormState): String = s.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    fun cuppingBeanInfoLine(b: CuppingBeanForm): String = line(
        b.country, RegionText.display(RegionText.join(b.region, b.subRegion)), b.farmProducer, b.variety, b.process, b.roast,
        b.rank.takeIf { it.isNotBlank() }?.let { "순위 $it" },
    )

    fun cuppingEvaluationLine(b: CuppingBeanForm): String = if (b.scoreForm == ScoreForm.CVA) {
        CvaScoring.affectiveScore(b.cva)?.let { "CVA ${CvaScoring.format(it)}" } ?: ""
    } else {
        b.evaluationScores.count { it.value > 0 }.takeIf { it > 0 }?.let { "SCA 2004 · ${it}개 항목" } ?: ""
    }

    fun cuppingBeanNotesLine(b: CuppingBeanForm): String =
        line(b.expectedNotes.joinToString(", ").takeIf { it.isNotEmpty() }?.let { "예상 $it" }, b.actualNotes.joinToString(", ").takeIf { it.isNotEmpty() }?.let { "마신 $it" })
}

/** The form's folds as a section reads them: whether a part ([FormFold]) is folded, and its toggle. */
internal class Folds(val isFolded: (String) -> Boolean, val toggle: (String) -> Unit) {
    companion object {
        val NONE = Folds({ false }, {})
    }
}

/**
 * What is folded ([FormFold]), under one device key ([SettingsRepository.DEVICE_PREFIX]): a backup neither carries nor
 * restores it. Writes run in order on [scope] (the app's), so the last fold still lands after the form closed.
 */
class FormFoldStore(private val settings: SettingsRepository, private val scope: CoroutineScope) {
    private val lock = Mutex()

    suspend fun load(): Map<String, Boolean> = lock.withLock { runCatching { FormFold.decode(settings.get(KEY)) }.getOrDefault(emptyMap()) }

    fun save(folds: Map<String, Boolean>): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        lock.withLock { runCatching { settings.put(KEY, FormFold.encode(folds)) } }
    }

    companion object {
        const val KEY = SettingsRepository.DEVICE_PREFIX + "form.folds"
    }
}
