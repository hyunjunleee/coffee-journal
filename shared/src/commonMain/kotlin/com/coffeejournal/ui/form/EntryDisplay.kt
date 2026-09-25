package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.reference.ScaForm
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.CuppingTypes
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.number

/** Pure presentation rules of the entry card (web entryHtml helpers). */
internal object EntryDisplay {
    data class InfoLine(val label: String, val value: String)
    data class NoteSections(val hasFinal: Boolean, val before: String, val final: String)

    private val finalMarker = Regex("(?:^|\\n)\\s*(?:#{1,6}\\s*)?(Final\\s+Evaluation|최종\\s*평가)\\s*:?[ \\t]*(?:\\n|$)", RegexOption.IGNORE_CASE)

    /** Web splitFinalEvaluation: notes may end with a "Final Evaluation" / "최종 평가" section. */
    fun splitFinalEvaluation(notes: String): NoteSections {
        val m = finalMarker.find(notes) ?: return NoteSections(false, notes.trim(), "")
        return NoteSections(true, notes.substring(0, m.range.first).trim(), notes.substring(m.range.last + 1).trim())
    }

    /** Web getEffectiveRegion: own region, else the first sibling that has one. */
    fun effectiveRegion(en: Entry, siblings: List<Entry>): String =
        en.region.ifBlank { siblings.firstOrNull { it.region.isNotBlank() }?.region ?: "" }

    /** Web beanInfoLinesHtml: roastery / importer / farm / washing station, completed from name parens and siblings. */
    fun beanInfoLines(en: Entry, siblings: List<Entry>): List<InfoLine> {
        fun parens(e: Entry) = if (e.roastery.isBlank() && e.selection.isBlank() && e.name.isNotBlank()) BeanNames.parseNameParens(e.name) else null
        val own = parens(en)
        var roastery = en.roastery.ifBlank { own?.roastery ?: "" }
        var selection = en.selection.ifBlank { own?.source ?: "" }
        var farm = en.farmProducer.ifBlank { own?.takeIf { it.farm.isNotBlank() }?.let { BeanNames.formatFarmProducer(it.farm, it.producer) } ?: "" }
        var washing = en.washingStation
        for (sib in siblings) {
            if (roastery.isNotBlank() && selection.isNotBlank() && farm.isNotBlank() && washing.isNotBlank()) break
            val sp = parens(sib)
            if (roastery.isBlank()) roastery = sib.roastery.ifBlank { sp?.roastery ?: "" }
            if (selection.isBlank()) selection = sib.selection.ifBlank { sp?.source ?: "" }
            if (farm.isBlank()) farm = sib.farmProducer.ifBlank { sp?.takeIf { it.farm.isNotBlank() }?.let { BeanNames.formatFarmProducer(it.farm, it.producer) } ?: "" }
            if (washing.isBlank()) washing = sib.washingStation
        }
        return listOfNotNull(
            roastery.takeIf { it.isNotBlank() }?.let { InfoLine("로스터리", it) },
            selection.takeIf { it.isNotBlank() }?.let { InfoLine("생두 수입사", it) },
            farm.takeIf { it.isNotBlank() }?.let { InfoLine("농장", it) },
            washing.takeIf { it.isNotBlank() }?.let { InfoLine("워싱 스테이션", it) },
        )
    }

    /** "YYYY.MM.DD · 카테고리 · 커핑유형 · 카페명 · 지역 · 드리퍼". */
    fun subtitle(en: Entry, siblings: List<Entry>): String {
        val parts = mutableListOf(Dates.ymdPadded(en.createdAt))
        if (en.category.isNotBlank() && en.category != Category.BEAN) parts += en.category
        if (en.isCupping) parts += CuppingTypes.effective(en)
        if (en.isCafe && en.cafeName.isNotBlank()) parts += en.cafeName
        effectiveRegion(en, siblings).takeIf { it.isNotBlank() }?.let { parts += it }
        if (en.dripper.isNotBlank()) parts += en.dripper
        return parts.joinToString(" · ")
    }

    /** Web scaScoreHtml with the app rule: only when one of the 7 scored attributes was set. */
    fun scaTotalText(en: Entry): String? {
        if (en.isCupping) return null
        return ScaScoring.effectiveTotal(FormNumbers.finiteAttributes(en.attributes))?.let { "${ScaScoring.format2(it)} / 100" }
    }

    /**
     * Whether the detail shows its SCA block: when the record was scored (design §2.3.3), or when an intensity or an
     * attribute memo was entered without scoring. The three default 10s alone do not count.
     */
    fun showsScaBlock(attributes: Map<String, Double>, attributeNotes: Map<String, String>): Boolean =
        ScaScoring.isScored(attributes) ||
            ScaForm.intensities.any { (attributes[it.key] ?: 0.0) > 0 } ||
            ScaForm.attrs.any { !attributeNotes[it.key].isNullOrBlank() }

    /** Web confirmEntryDeletion, plus the app's note that the bag photos go too (design §2.3.4). */
    fun deleteConfirmText(en: Entry): String {
        val name = en.name.ifBlank { en.cafeName.ifBlank { en.cuppingPlace } }.trim()
        return (if (name.isNotEmpty()) "“$name” " else "") + "기록을 정말 삭제할까요?\n\n삭제한 기록은 복구할 수 없어요. 봉투 사진도 함께 지워져요."
    }

    /** Web saveAsMyRecipe alert; the recipe comes back through the record form's "⭐ 내 레시피" button. */
    fun recipeSavedText(name: String): String =
        "\"$name\" 이름으로 내 레시피에 저장했어요. 새 기록의 \"⭐ 내 레시피\" 버튼에서 다시 꺼내 쓰실 수 있어요."

    fun priceText(price: String): String? = Prices.normalize(price).takeIf { it.isNotEmpty() }?.let { Prices.format(it.toDouble()) + "원" }

    /** Info grid rows in the web's order (the cupping bean accordion is rendered separately). */
    fun infoRows(en: Entry): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        fun add(label: String, value: String?) { if (!value.isNullOrBlank()) rows += label to value }
        if (en.isCafe) add("카페", en.cafeName)
        add(if (en.isCafe) "한 잔 가격" else "원두 가격", priceText(en.price))
        if (en.isCupping) add("장소", en.cuppingPlace)
        add("가공", en.process.takeIf { it.isNotBlank() }?.let { p -> if (p == FormMapper.PROCESS_OTHER && en.processOther.isNotBlank()) "$p (${en.processOther})" else p })
        add("고도", en.altitude)
        add("품종", en.variety)
        add("로스팅", en.roast)
        add("수분율", en.moisture.takeIf { it.isNotBlank() }?.let { "$it%" })
        add("밀도", en.density.takeIf { it.isNotBlank() }?.let { "${it}g/L" })
        add("CoE 컵 점수", en.score)
        add("입고 시기", en.arrival)
        add("로스팅 날짜", Dates.roastDateWithYear(en.roastDate, en.createdAt))
        add("비율", en.dose.takeIf { it.isNotBlank() }?.let { "${it}g : ${en.water.ifBlank { "?" }}g" })
        add("물 온도", en.temp.takeIf { it.isNotBlank() }?.let { "$it°C" })
        add("분쇄도", en.grind)
        add("사용한 물", en.waterType)
        // the cupping form has no 총 추출시간; older cupping records (and web ones) carry the example steps' 2:10
        if (!en.isCupping) add("총 시간", en.time)
        add("필터", en.filter)
        add("예상 노트", en.expectedNotes)
        add("내가 느낀 노트", en.actualNotes)
        return rows
    }

    fun formModeFor(category: String): String = when (category) {
        Category.CAFE -> FormMode.CAFE
        Category.CUPPING -> FormMode.CUPPING
        else -> FormMode.EXTRACT
    }

    /** Web saveAsMyRecipe default name: "{이름} (M/D)". */
    fun defaultRecipeName(en: Entry): String {
        val d = Dates.toLocalDate(en.createdAt)
        return "${en.name.ifBlank { "레시피" }} (${d.month.number}/${d.day})"
    }
}
