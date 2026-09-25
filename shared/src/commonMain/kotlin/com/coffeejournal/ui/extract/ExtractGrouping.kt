package com.coffeejournal.ui.extract

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.domain.rules.CuppingTypes
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Packages
import com.coffeejournal.domain.rules.PantryRules
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.domain.rules.ScaScoring
import kotlinx.datetime.LocalDate

/** Web extractFilterMode. */
object ExtractFilter {
    const val ALL = "all"
    const val SMALLPACK = "smallpack"
}

/** Web smallPackFilterMode. */
object SmallPackFilter {
    const val ALL = "all"
    const val DRIPBAG = "dripbag"
    const val SAMPLE = "sample"
}

/** An entry shown in the home list; single-bean home cuppings are projected copies of the original. */
data class ListedEntry(val entry: Entry, val isHomeCuppingProjection: Boolean = false)

data class InfoLine(val label: String, val value: String)

/** One bean's records on the home list (web bean-group). */
data class BeanGroup(
    val key: String,
    val name: String,
    /** Newest first. */
    val entries: List<ListedEntry>,
    val firstAt: Long,
    val lastAt: Long,
    val dateRangeLabel: String,
    val highestScore: Double?,
    /** Photo file name of the oldest record that has a bag photo. */
    val thumbnailPhoto: String?,
    /** "직접 블렌드 · A 10g + B 8g" when the latest record is a custom blend. */
    val customBlendLine: String?,
    val infoLines: List<InfoLine>,
) {
    val displayName: String get() = BeanNames.displayName(name)
    val count: Int get() = entries.size
    /** Brew records only: candidates for the best recipe (projections are cuppings and excluded). */
    val recipeEntries: List<Entry> get() = entries.filter { !it.isHomeCuppingProjection && Packages.isBrew(it.entry) }.map { it.entry }
}

data class ListSelection(val brewEntries: List<Entry>, val openedSmallPacks: List<PantryItem>)

data class SmallPackCard(val item: PantryItem, val statsLine: String, val windowText: String)

data class SearchGroup(val key: String, val name: String, val records: List<BeanRecord>) {
    val displayName: String get() = BeanNames.displayName(name)
}

data class SearchResult(val query: String, val total: Int, val groups: List<SearchGroup>)

data class SearchRow(val badge: String, val cuppingType: String, val dateText: String, val place: String, val entryId: String)

sealed interface DrinkingState {
    data object None : DrinkingState
    data class OpenedBags(val cards: List<OpenedBagCard>) : DrinkingState
    data class RecentBean(val card: RecentBeanCard) : DrinkingState
}

data class OpenedBagCard(
    val item: PantryItem,
    val eyebrow: String,
    val metaLine: String,
    val remainingLine: String,
    val priceText: String,
    val windowText: String,
)

data class RecentBeanCard(
    val entry: Entry,
    val eyebrow: String,
    val infoLines: List<InfoLine>,
    val remainingLine: String,
    /** Farm memo from the misc list (the web's AI blurb is not implemented). */
    val description: String?,
)

data class EntryRow(
    val entryId: String,
    val category: String,
    val dateText: String,
    val categoryText: String,
    val packageBadge: String?,
    val isBest: Boolean,
    val scoreText: String?,
    val recipeLine: String,
    val notePreview: String,
)

/** Pure derivations behind the home tab (web render / renderWeeklyBean / renderAllBeanSearchResults). */
object ExtractGrouping {

    /** Web render(): which brew records and opened small packs the current filter shows. */
    fun selectEntries(entries: List<Entry>, pantry: List<PantryItem>, filterMode: String, smallMode: String): ListSelection {
        val brews = entries.filter { Packages.isBrew(it) }
        return if (filterMode == ExtractFilter.SMALLPACK) {
            val shown = brews.filter { en ->
                val type = Packages.entryPackageType(en)
                type != PackageType.STANDARD && (smallMode == SmallPackFilter.ALL || type == smallMode)
            }
            val packs = pantry.filter { item ->
                val type = Packages.pantryPackageType(item)
                item.isOpened && type != PackageType.STANDARD && (smallMode == SmallPackFilter.ALL || type == smallMode)
            }.sortedByDescending { it.openedAt ?: 0L }
            ListSelection(shown, packs)
        } else {
            ListSelection(brews.filter { Packages.entryPackageType(it) == PackageType.STANDARD }, emptyList())
        }
    }

    /** Core names a record contributes to: custom blends count for each component. */
    fun groupKeys(entry: Entry): List<String> {
        val names = componentNames(entry)
        return names.map { BeanNames.coreBeanName(it) }.filter { it.isNotBlank() }.distinct()
    }

    private fun componentNames(entry: Entry): List<String> =
        if (entry.beanMode == BeanMode.CUSTOM_BLEND && entry.blendComponents.isNotEmpty()) entry.blendComponents.map { it.name }.filter { it.isNotBlank() }
        else listOf(entry.name)

    /** Web singleBeanHomeCuppings: a home cupping of one bean joins that bean's brew group (display only). */
    fun projectHomeCuppings(entries: List<Entry>, brewEntries: List<Entry>): List<ListedEntry> {
        val brewedKeys = brewEntries.flatMap { groupKeys(it) }.toSet()
        if (brewedKeys.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { it.isCupping && CuppingTypes.effective(it) == CuppingType.HOME && it.cuppingBeans.size == 1 }
            .map { en ->
                val bean = en.cuppingBeans.first()
                en.copy(
                    name = bean.name.ifBlank { en.name },
                    country = bean.country.ifBlank { en.country },
                    region = bean.region.ifBlank { en.region },
                    farmProducer = bean.farmProducer.ifBlank { en.farmProducer },
                    altitude = bean.altitude.ifBlank { en.altitude },
                    variety = bean.variety.ifBlank { en.variety },
                    process = bean.process.ifBlank { en.process },
                    roast = bean.roast.ifBlank { en.roast },
                    expectedNotes = bean.expectedNotes,
                    actualNotes = bean.actualNotes,
                    cuppingBeans = listOf(bean),
                )
            }
            .filter { BeanNames.coreBeanName(it.name) in brewedKeys }
            .map { ListedEntry(it, isHomeCuppingProjection = true) }
            .toList()
    }

    /** Web render() grouping: by core name, custom blends duplicated per component, newest first, groups by first record. */
    fun buildGroups(listed: List<ListedEntry>, allEntries: List<Entry>): List<BeanGroup> {
        val buckets = LinkedHashMap<String, Pair<String, MutableList<ListedEntry>>>()
        for (item in listed) {
            val names = componentNames(item.entry)
            for (key in groupKeys(item.entry)) {
                val displayName = (names.firstOrNull { BeanNames.coreBeanName(it) == key } ?: item.entry.name.ifBlank { "이름 없음" }).trim()
                buckets.getOrPut(key) { displayName to mutableListOf() }.second += item
            }
        }
        return buckets.map { (key, value) ->
            val (name, members) = value
            val sorted = members.sortedByDescending { it.entry.createdAt }
            val latest = sorted.first().entry
            val oldest = sorted.last().entry
            val scores = sorted.mapNotNull { ScaScoring.effectiveTotal(it.entry.attributes) }
            val thumb = sorted.asReversed().firstOrNull { it.entry.bagPhotos.isNotEmpty() }?.entry?.bagPhotos?.firstOrNull()
            val range = if (sorted.size > 1) "${Dates.md(oldest.createdAt)} ~ ${Dates.md(latest.createdAt)}" else Dates.md(latest.createdAt)
            val blendLine = if (latest.beanMode == BeanMode.CUSTOM_BLEND) {
                "직접 블렌드 · " + latest.blendComponents.joinToString(" + ") { "${it.name} ${it.grams}g" }
            } else null
            BeanGroup(
                key = key,
                name = name,
                entries = sorted,
                firstAt = oldest.createdAt,
                lastAt = latest.createdAt,
                dateRangeLabel = range,
                highestScore = scores.maxOrNull(),
                thumbnailPhoto = thumb,
                customBlendLine = blendLine,
                infoLines = if (blendLine == null) infoLines(latest, allEntries) else emptyList(),
            )
        }.sortedByDescending { it.firstAt }
    }

    /** Web beanInfoLinesHtml: roastery / importer / farm / washing station, filled from the name parens and siblings. */
    fun infoLines(entry: Entry, allEntries: List<Entry>): List<InfoLine> {
        fun parens(en: Entry) = if (en.roastery.isBlank() && en.selection.isBlank() && en.name.isNotBlank()) BeanNames.parseNameParens(en.name) else null
        val parsed = parens(entry)
        var roastery = entry.roastery.ifBlank { parsed?.roastery ?: "" }
        var selection = entry.selection.ifBlank { parsed?.source ?: "" }
        var farm = entry.farmProducer.ifBlank { parsed?.farm?.takeIf { it.isNotBlank() }?.let { BeanNames.formatFarmProducer(it, parsed.producer) } ?: "" }
        var washing = entry.washingStation
        if ((roastery.isBlank() || selection.isBlank() || farm.isBlank() || washing.isBlank()) && entry.name.isNotBlank()) {
            val key = BeanNames.coreBeanName(entry.name)
            for (sib in allEntries) {
                if (sib.id == entry.id || BeanNames.coreBeanName(sib.name) != key) continue
                val sp = parens(sib)
                if (roastery.isBlank()) roastery = sib.roastery.ifBlank { sp?.roastery ?: "" }
                if (selection.isBlank()) selection = sib.selection.ifBlank { sp?.source ?: "" }
                if (farm.isBlank()) farm = sib.farmProducer.ifBlank { sp?.farm?.takeIf { it.isNotBlank() }?.let { BeanNames.formatFarmProducer(it, sp.producer) } ?: "" }
                if (washing.isBlank()) washing = sib.washingStation
                if (roastery.isNotBlank() && selection.isNotBlank() && farm.isNotBlank() && washing.isNotBlank()) break
            }
        }
        return listOfNotNull(
            roastery.takeIf { it.isNotBlank() }?.let { InfoLine("로스터리", it) },
            selection.takeIf { it.isNotBlank() }?.let { InfoLine("생두 수입사", it) },
            farm.takeIf { it.isNotBlank() }?.let { InfoLine("농장", it) },
            washing.takeIf { it.isNotBlank() }?.let { InfoLine("워싱 스테이션", it) },
        )
    }

    /** Web recipeSummaryLine: "15g · 240g · 92°C · V60". */
    fun recipeSummaryLine(entry: Entry): String = listOf(
        entry.dose.trim().takeIf { it.isNotBlank() }?.let { it + "g" },
        entry.water.trim().takeIf { it.isNotBlank() }?.let { it + "g" },
        entry.temp.trim().takeIf { it.isNotBlank() }?.let { it + "°C" },
        entry.dripper.trim().takeIf { it.isNotBlank() },
    ).filterNotNull().joinToString(" · ")

    fun entryRow(entry: Entry, isBest: Boolean): EntryRow {
        val category = entry.category.ifBlank { Category.BEAN }
        val type = Packages.entryPackageType(entry)
        return EntryRow(
            entryId = entry.id,
            category = category,
            dateText = Dates.ymdPadded(entry.createdAt),
            categoryText = if (category == Category.CUPPING) "$category · ${CuppingTypes.effective(entry)}" else category,
            packageBadge = when (type) { PackageType.DRIPBAG -> "드립백"; PackageType.SAMPLE -> "소량"; else -> null },
            isBest = isBest,
            scoreText = ScaScoring.effectiveTotal(entry.attributes)?.let { ScaScoring.format2(it) + " / 100" },
            recipeLine = recipeSummaryLine(entry),
            notePreview = entry.actualNotes.trim().take(60),
        )
    }

    fun emptyText(filterMode: String, smallMode: String): String =
        if (filterMode == ExtractFilter.SMALLPACK) {
            val what = when (smallMode) { SmallPackFilter.DRIPBAG -> "드립백"; SmallPackFilter.SAMPLE -> "소량"; else -> "드립백이나 소량" }
            "아직 ${what} 기록이 없습니다."
        } else "아직 추출 기록이 없습니다. 오늘 내린 커피부터 남겨보세요."

    /** Web openedSmallPackHtml card. */
    fun smallPackCard(item: PantryItem): SmallPackCard {
        val stats = listOf(
            item.roastery,
            PantryRules.priceText(item.weight, item.price).ifBlank { if (item.weight.isNotBlank()) "${item.weight}g" else "" },
            if (item.roastDate.isNotBlank()) "로스팅 ${item.roastDate}" else "",
        ).filter { it.isNotBlank() }.joinToString(" · ")
        return SmallPackCard(item, stats, PantryRules.drinkWindowText(item))
    }

    // ---- search -------------------------------------------------------------------------------

    /** Web renderAllBeanSearchResults; null when the query is blank. */
    fun search(entries: List<Entry>, rawQuery: String): SearchResult? {
        val query = normalize(rawQuery)
        if (query.isEmpty()) return null
        val seen = HashSet<String>()
        val matching = BeanRecords.flatten(entries).filter { record ->
            val searchable = normalize(listOf(record.name, record.roastery, record.selection).filter { it.isNotBlank() }.joinToString(" "))
            if (!searchable.contains(query)) return@filter false
            seen.add("${record.parentEntryId ?: record.entryId}:${BeanNames.coreBeanName(record.name)}")
        }
        val buckets = LinkedHashMap<String, Pair<String, MutableList<BeanRecord>>>()
        for (record in matching) {
            val key = BeanNames.coreBeanName(record.name).ifBlank { record.name.ifBlank { "이름없음" } }
            buckets.getOrPut(key) { record.name.ifBlank { "이름 없음" } to mutableListOf() }.second += record
        }
        val groups = buckets.map { (key, v) -> SearchGroup(key, v.first, v.second.sortedByDescending { it.createdAt }) }
            .sortedByDescending { g -> g.records.minOf { it.createdAt } }
        return SearchResult(rawQuery.trim(), matching.size, groups)
    }

    fun searchRow(record: BeanRecord): SearchRow {
        val cat = record.category.ifBlank { Category.BEAN }
        return SearchRow(
            badge = if (cat == Category.BEAN) "직접 내림" else cat,
            cuppingType = if (cat == Category.CUPPING) record.cuppingType else "",
            dateText = Dates.ymdCompact(record.createdAt),
            place = when (cat) { Category.CAFE -> record.cafeName; Category.CUPPING -> record.cuppingPlace; else -> "집 추출" },
            entryId = record.parentEntryId ?: record.entryId,
        )
    }

    private fun normalize(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), "")

    // ---- 마시는 중 ----------------------------------------------------------------------------

    /** Web isKnownBlendName: the name says blend, or a blend record shares the core name. */
    fun isKnownBlendName(name: String, entries: List<Entry>): Boolean {
        if (BeanNames.nameSaysBlend(name)) return true
        val key = BeanNames.coreBeanName(name)
        return entries.any { BeanNames.coreBeanName(it.name) == key && Packages.isBlend(it) }
    }

    /** Web renderWeeklyBean: opened standard bags first, otherwise the most recent standard brew. */
    fun drinking(entries: List<Entry>, pantry: List<PantryItem>, blends: List<Blend>, misc: List<MiscItem>, today: LocalDate = Dates.today()): DrinkingState {
        val opened = pantry.filter { item ->
            item.isOpened && Packages.pantryPackageType(item) == PackageType.STANDARD &&
                !isKnownBlendName(item.name, entries) && !BeanNames.isDecaf(item.name, "", "")
        }.sortedWith(compareBy<PantryItem> { PantryRules.peakStartMillis(it) }.thenByDescending { it.createdAt })
        if (opened.isNotEmpty()) return DrinkingState.OpenedBags(opened.map { openedBagCard(it, entries, blends) })

        val brews = entries.filter { Packages.isBrew(it) && Packages.entryPackageType(it) == PackageType.STANDARD && !Packages.isBlend(it) && !Packages.isDecaf(it) }
        val latest = brews.maxByOrNull { it.createdAt } ?: return DrinkingState.None
        val key = BeanNames.coreBeanName(latest.name)
        if (key.isBlank()) return DrinkingState.None
        val sameBean = brews.filter { BeanNames.coreBeanName(it.name) == key }
        val startAt = sameBean.minOf { it.createdAt }
        val start = Dates.toLocalDate(startAt)
        val daysIn = Dates.daysBetween(start, today) + 1
        val bagWeight = sameBean.sortedByDescending { it.createdAt }.firstOrNull { it.bagWeight.isNotBlank() }?.bagWeight?.trim()?.toDoubleOrNull()
        val (remaining, bag) = PantryRules.remainingGrams(key, bagWeight, entries, blends)
        val lines = infoLines(latest, entries)
        // The farm shown on the card (own field, else filled from sibling records) decides which farm memo to show.
        val farmKey = latest.farmProducer.ifBlank { lines.firstOrNull { it.label == "농장" }?.value ?: "" }.trim().lowercase()
        val description = if (farmKey.isEmpty()) null else misc.firstOrNull { it.type == MiscType.FARM && it.name.trim().lowercase() == farmKey && it.notes.isNotBlank() }?.notes?.trim()
        return DrinkingState.RecentBean(
            RecentBeanCard(
                entry = latest,
                eyebrow = "마시는 중 · ${daysIn}일째(${Dates.ymdWithWeekday(start)}~)",
                infoLines = lines,
                remainingLine = remainingLine(remaining, bag),
                description = description,
            )
        )
    }

    private fun openedBagCard(item: PantryItem, entries: List<Entry>, blends: List<Blend>): OpenedBagCard {
        val key = BeanNames.coreBeanName(item.name)
        val (remaining, bag) = PantryRules.remainingGrams(key, item.weight.trim().toDoubleOrNull(), entries, blends)
        val openedAt = item.openedAt ?: item.createdAt
        val meta = listOf(
            item.roastery,
            if (item.roastDate.isNotBlank()) "로스팅 ${item.roastDate}" else "",
            if (item.weight.isNotBlank()) "${item.weight}g" else "",
        ).filter { it.isNotBlank() }.joinToString(" · ")
        return OpenedBagCard(
            item = item,
            eyebrow = "마시는 중 · ${Dates.ymdCompact(openedAt)}.~",
            metaLine = meta,
            remainingLine = remainingLine(remaining, bag),
            priceText = PantryRules.priceText(item.weight, item.price),
            windowText = PantryRules.drinkWindowText(item),
        )
    }

    fun remainingLine(remaining: Double, bag: Double): String = "잔여량 ${Prices.trimNumber(remaining)}g/${Prices.trimNumber(bag)}g"

}
