package com.coffeejournal.ui.extract.compare

import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.BrewMath
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Numbers
import com.coffeejournal.domain.rules.Packages
import com.coffeejournal.domain.rules.RecipeSteps
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.extract.ExtractGrouping
import kotlin.math.abs

/** Which record the others are compared with. */
enum class CompareReference(val label: String) {
    BEST("⭐ 베스트 레시피"),
    TOP_SCORE("최고 점수"),
    TOP_CVA("CVA 최고 점수"),
}

data class CompareColumn(val entryId: String, val dateText: String, val isReference: Boolean)

/** One value of one record; [differs] marks a value unlike the reference record's, [delta] its signed difference. */
data class CompareCell(val text: String, val differs: Boolean = false, val delta: String? = null)

data class CompareRow(val label: String, val cells: List<CompareCell>)

data class CompareTable(
    val beanName: String,
    val columns: List<CompareColumn>,
    val rows: List<CompareRow>,
    val reference: CompareReference?,
) {
    val isEmpty: Boolean get() = columns.isEmpty()
}

/**
 * 추출 비교 (feature-plan-v2 §2.2): the brew records of one bean side by side, the best recipe (else the top SCA 2004
 * score, else the top CVA score) first and every value that differs from it marked.
 */
object BrewCompare {
    /** The bean's brew records as the home group gathers them (custom blends count for each component), newest first. */
    fun records(entries: List<Entry>, beanKey: String): List<Entry> =
        entries.filter { Packages.isBrew(it) && beanKey in ExtractGrouping.groupKeys(it) }.sortedByDescending { it.createdAt }

    /** The reference record: the chosen best recipe, else the highest SCA 2004 total, else the highest CVA score. */
    fun reference(records: List<Entry>, bestId: String?): Pair<Entry, CompareReference>? {
        records.firstOrNull { it.id == bestId }?.let { return it to CompareReference.BEST }
        records.mapNotNull { e -> ScaScoring.effectiveTotal(e.attributes)?.let { e to it } }.maxByOrNull { it.second }
            ?.let { return it.first to CompareReference.TOP_SCORE }
        records.mapNotNull { e -> CvaScoring.scoreOf(e)?.let { e to it } }.maxByOrNull { it.second }
            ?.let { return it.first to CompareReference.TOP_CVA }
        return null
    }

    fun build(entries: List<Entry>, beanKey: String, bestId: String?): CompareTable {
        val records = records(entries, beanKey)
        val name = records.firstNotNullOfOrNull { e ->
            if (BeanNames.coreBeanName(e.name) == beanKey) e.name
            else e.blendComponents.firstOrNull { BeanNames.coreBeanName(it.name) == beanKey }?.name
        } ?: beanKey
        val ref = reference(records, bestId)
        val ordered = if (ref == null) records else listOf(ref.first) + records.filter { it.id != ref.first.id }
        val refEntry = ref?.first
        val columns = ordered.map { CompareColumn(it.id, Dates.mdHm(it.createdAt), isReference = it.id == refEntry?.id) }

        fun row(label: String, cell: (Entry) -> CompareCell, compare: ((Entry, Entry) -> CompareCell)? = null): CompareRow =
            CompareRow(label, ordered.map { e -> if (refEntry == null || e.id == refEntry.id || compare == null) cell(e) else compare(e, refEntry) })

        val rows = listOf(
            row("날짜", { CompareCell(Dates.ymdPadded(it.createdAt)) }),
            numberRow("원두량", "g", ordered, refEntry) { it.dose },
            numberRow("물량", "g", ordered, refEntry) { it.water },
            row("비율", { CompareCell(BrewMath.ratioText(it.dose, it.water) ?: "-") }) { e, r ->
                val a = BrewMath.ratio(Numbers.parse(e.dose), Numbers.parse(e.water))
                val b = BrewMath.ratio(Numbers.parse(r.dose), Numbers.parse(r.water))
                numberCell(a?.let { "1:" + BrewMath.fmt1(it) }, a?.let { BrewMath.fmt1(it).toDouble() }, b?.let { BrewMath.fmt1(it).toDouble() }, "")
            },
            numberRow("온도", "°C", ordered, refEntry) { it.temp },
            textRow("분쇄도", ordered, refEntry) { it.grind },
            row("총시간", { CompareCell(it.time.ifBlank { "-" }) }) { e, r ->
                val a = RecipeSteps.parseTimeToSec(e.time)?.toDouble()
                val b = RecipeSteps.parseTimeToSec(r.time)?.toDouble()
                val cell = numberCell(e.time.ifBlank { null }, a, b, "s")
                if (a == null && b == null && e.time.trim() != r.time.trim()) cell.copy(differs = true) else cell
            },
            textRow("드리퍼", ordered, refEntry) { it.dripper },
            row("점수", { scoreCell(it) }) { e, r -> scoreCompare(e, r) },
            row("노트", { CompareCell(it.actualNotes.trim().ifBlank { "-" }) }),
        )
        return CompareTable(BeanNames.displayName(name), columns, rows, ref?.second)
    }

    private fun numberRow(label: String, unit: String, ordered: List<Entry>, ref: Entry?, pick: (Entry) -> String): CompareRow =
        CompareRow(label, ordered.map { e ->
            val text = pick(e).trim().takeIf { it.isNotEmpty() }?.let { it + unit }
            if (ref == null || e.id == ref.id) CompareCell(text ?: "-")
            else numberCell(text, Numbers.parse(pick(e)), Numbers.parse(pick(ref)), unit).let { c ->
                // free text that is not a number ("약 15") still counts as different when it is not the same text
                if (Numbers.parse(pick(e)) == null && Numbers.parse(pick(ref)) == null && norm(pick(e)) != norm(pick(ref))) c.copy(differs = true) else c
            }
        })

    private fun textRow(label: String, ordered: List<Entry>, ref: Entry?, pick: (Entry) -> String): CompareRow =
        CompareRow(label, ordered.map { e ->
            val text = pick(e).trim().ifBlank { "-" }
            CompareCell(text, differs = ref != null && e.id != ref.id && norm(pick(e)) != norm(pick(ref)))
        })

    private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    /** A number against the reference's: different when only one has a value or the values differ (delta "+1g"). */
    internal fun numberCell(text: String?, value: Double?, ref: Double?, unit: String): CompareCell = when {
        value == null && ref == null -> CompareCell(text ?: "-")
        value == null || ref == null -> CompareCell(text ?: "-", differs = true)
        abs(value - ref) < 1e-9 -> CompareCell(text ?: "-")
        else -> CompareCell(text ?: "-", differs = true, delta = signed(value - ref) + unit)
    }

    private fun signed(v: Double): String = (if (v > 0) "+" else "−") + BrewMath.fmt2(abs(v))

    private fun scoreCell(e: Entry): CompareCell {
        ScaScoring.effectiveTotal(e.attributes)?.let { return CompareCell(ScaScoring.format2(it)) }
        CvaScoring.scoreOf(e)?.let { return CompareCell("CVA " + CvaScoring.format(it)) }
        return CompareCell("-")
    }

    /** Scores compare only within one form: an SCA 2004 total is never set against a CVA score. */
    private fun scoreCompare(e: Entry, r: Entry): CompareCell {
        val base = scoreCell(e)
        val a2004 = ScaScoring.effectiveTotal(e.attributes)
        val r2004 = ScaScoring.effectiveTotal(r.attributes)
        if (a2004 != null && r2004 != null) return numberCell(base.text, a2004, r2004, "")
        val aCva = CvaScoring.scoreOf(e)
        val rCva = CvaScoring.scoreOf(r)
        if (aCva != null && rCva != null) return numberCell(base.text, aCva, rCva, "")
        return base.copy(differs = base.text != scoreCell(r).text)
    }
}
