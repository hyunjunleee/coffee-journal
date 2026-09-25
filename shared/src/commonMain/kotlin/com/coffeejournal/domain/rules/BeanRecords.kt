package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry

/** Web getAllBeanRecords: every bean actually tasted, with cupping sessions expanded per bean. */
object BeanRecords {
    fun flatten(entries: List<Entry>): List<BeanRecord> = entries.flatMap { en ->
        if (en.isCupping) {
            en.cuppingBeans.filter { it.name.isNotBlank() }.map { bean ->
                BeanRecord(
                    entryId = en.id,
                    parentEntryId = en.id,
                    category = Category.CUPPING,
                    createdAt = en.createdAt,
                    name = bean.name,
                    country = bean.country,
                    region = bean.region,
                    farmProducer = bean.farmProducer,
                    roastery = bean.roastery,
                    selection = "",
                    altitude = bean.altitude,
                    variety = bean.variety,
                    process = bean.process,
                    processOther = "",
                    roast = bean.roast,
                    expectedNotes = bean.expectedNotes,
                    actualNotes = bean.actualNotes,
                    notes = bean.memo,
                    beanMode = bean.beanMode,
                    blendComponents = emptyList(),
                    blendComponentsText = bean.blendComponentsText,
                    score = "",
                    cafeName = "",
                    cuppingPlace = en.cuppingPlace,
                    cuppingType = CuppingTypes.effective(en),
                    roasterDesc = "",
                    packageType = en.packageType,
                )
            }
        } else {
            listOf(
                BeanRecord(
                    entryId = en.id,
                    parentEntryId = null,
                    category = en.category.ifBlank { Category.BEAN },
                    createdAt = en.createdAt,
                    name = en.name,
                    country = en.country,
                    region = en.region,
                    farmProducer = en.farmProducer,
                    roastery = en.roastery,
                    selection = BeanNames.entrySelection(en),
                    altitude = en.altitude,
                    variety = en.variety,
                    process = en.process,
                    processOther = en.processOther,
                    roast = en.roast,
                    expectedNotes = en.expectedNotes,
                    actualNotes = en.actualNotes,
                    notes = en.notes,
                    beanMode = en.beanMode.ifBlank { BeanMode.SINGLE },
                    blendComponents = en.blendComponents,
                    blendComponentsText = "",
                    score = en.score,
                    cafeName = en.cafeName,
                    cuppingPlace = "",
                    cuppingType = "",
                    roasterDesc = en.roasterDesc,
                    packageType = en.packageType,
                )
            )
        }
    }

    fun isBlend(record: BeanRecord): Boolean =
        record.beanMode == BeanMode.BLEND || record.beanMode == BeanMode.COMMERCIAL_BLEND ||
            record.beanMode == BeanMode.CUSTOM_BLEND || record.blendComponents.isNotEmpty()

    /** Web categoryBreakdownLine: "직접 내림 N종 · 카페 N번 · 커핑 N번". */
    fun categoryBreakdown(records: List<BeanRecord>): String {
        val brews = records.filter { it.category == Category.BEAN }.map { BeanNames.coreBeanName(it.name) }.toSet().size
        val cafes = records.count { it.category == Category.CAFE }
        val cuppings = records.count { it.category == Category.CUPPING }
        return listOfNotNull(
            brews.takeIf { it > 0 }?.let { "직접 내림 ${it}종" },
            cafes.takeIf { it > 0 }?.let { "카페 ${it}번" },
            cuppings.takeIf { it > 0 }?.let { "커핑 ${it}번" },
        ).joinToString(" · ")
    }
}

object CuppingTypes {
    /** Web effectiveCuppingType: stored type or a guess from the text. */
    fun effective(entry: Entry): String {
        if (entry.cuppingType in listOf("퍼블릭", "홈커핑", "수업")) return entry.cuppingType
        val text = listOf(entry.name, entry.cuppingPlace, entry.notes).filter { it.isNotBlank() }.joinToString(" ")
        if (Regex("홈커핑|집\\s*커핑|집에서\\s*커핑").containsMatchIn(text)) return "홈커핑"
        if (Regex("수업|클래스|센서리\\s*(?:레벨|수업)|로스팅\\s*수업").containsMatchIn(text)) return "수업"
        return "퍼블릭"
    }
}
