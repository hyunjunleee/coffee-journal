package com.coffeejournal.ui.bean.b

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.domain.rules.MapXY
import com.coffeejournal.ui.bean.KoreanOrder
import com.coffeejournal.ui.map.WorldPlaces
import com.coffeejournal.ui.map.WorldProjection

/** Web sourceBeansInfo row: one bean bought at a roastery with its first and last date. */
data class BeanSpan(val name: String, val start: Long, val end: Long, val category: String)

/**
 * A roastery on the map: [at] is in Korea-map units (국내) or world viewBox units (해외); [exact] when the roastery has
 * its own position, otherwise the centre of the 시·군·구 / 시·도 / country its location text names. [place] is the
 * area found ("서울특별시 성동구", "일본"); [area] the 시·도 code or map country it lies in (tinted on the map).
 */
data class RoasteryPin(val item: MiscItem, val at: MapXY, val exact: Boolean, val count: Int, val place: String, val area: String?)

data class RoasteryMapModel(
    val items: List<MiscItem>,
    val pins: List<RoasteryPin>,
    /** No location text and no position. */
    val unlocated: List<MiscItem>,
    /** A location text the map could not place, and no position. */
    val unmatched: List<MiscItem> = emptyList(),
    val cups: Int,
)

/** Labels, placeholders and record matching for the flat misc lists (roastery / importer / farm / process). */
object FlatItemLogic {
    data class Spec(val type: String, val label: String, val namePlaceholder: String, val notesPlaceholder: String, val hasStatus: Boolean, val hasScope: Boolean)

    val specs: Map<String, Spec> = listOf(
        Spec(MiscType.SOURCE, "로스터리", "예: 영천카페 듀잇", "주로 사는 원두, 링크 등", hasStatus = true, hasScope = true),
        Spec(MiscType.SELECTION, "생두 수입사", "예: Nordic Approach", "특징, 어떤 원두에서 봤는지 등", hasStatus = true, hasScope = false),
        Spec(MiscType.FARM, "농장(생산자)", "예: 라 에스메랄다(페드로 가족)", "특징, 어떤 원두에서 봤는지 등", hasStatus = true, hasScope = false),
        Spec(MiscType.PROCESS, "가공 방식", "예: 내추럴", "특징, 어떤 원두에서 봤는지 등", hasStatus = false, hasScope = false),
    ).associateBy { it.type }

    fun spec(type: String): Spec = specs[type] ?: Spec(type, type, "", "", hasStatus = true, hasScope = false)

    private fun key(s: String) = s.trim().lowercase()

    /** Web roasteryRecords: the record's roastery field or the roastery inside the name's trailing parenthesis. */
    fun roasteryRecords(records: List<BeanRecord>, name: String): List<BeanRecord> {
        val k = key(name)
        if (k.isEmpty()) return emptyList()
        return records.filter { key(it.roastery) == k || key(BeanNames.parseNameRoastery(it.name)) == k }
    }

    fun farmRecords(records: List<BeanRecord>, name: String): List<BeanRecord> {
        val k = key(name)
        return if (k.isEmpty()) emptyList() else records.filter { key(it.farmProducer) == k }
    }

    /** Web selectionCountryInfo: BeanRecord.selection already holds BeanNames.entrySelection. */
    fun selectionRecords(records: List<BeanRecord>, name: String): List<BeanRecord> {
        val k = key(name)
        return if (k.isEmpty()) emptyList() else records.filter { key(it.selection) == k }
    }

    fun beanSpans(records: List<BeanRecord>): List<BeanSpan> {
        val byBean = LinkedHashMap<String, BeanSpan>()
        records.forEach { r ->
            val k = BeanNames.coreBeanName(r.name).ifEmpty { "이름없음" }
            val prev = byBean[k]
            byBean[k] = if (prev == null) BeanSpan(r.name.trim().ifEmpty { "이름 없음" }, r.createdAt, r.createdAt, r.category.ifBlank { Category.BEAN })
            else prev.copy(start = minOf(prev.start, r.createdAt), end = maxOf(prev.end, r.createdAt))
        }
        return byBean.values.sortedByDescending { it.end }
    }

    /** "국가 → 횟수", most first; blank countries become 국가 미상. */
    fun countryCounts(records: List<BeanRecord>): List<Pair<String, Int>> {
        val out = LinkedHashMap<String, Int>()
        records.forEach { r -> val c = r.country.trim().ifEmpty { "국가 미상" }; out[c] = (out[c] ?: 0) + 1 }
        return out.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** Distinct bean names with the category they were last seen in (web selectionCountryInfo beans map). */
    fun distinctBeans(records: List<BeanRecord>): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()
        records.forEach { r -> if (r.name.isNotBlank()) out[r.name.trim()] = r.category.ifBlank { Category.BEAN } }
        return out.map { it.key to it.value }
    }

    fun sortItems(items: List<MiscItem>, favoritable: Boolean): List<MiscItem> =
        if (favoritable) items.sortedWith(compareByDescending<MiscItem> { it.favorite }.thenByDescending { it.createdAt })
        else items.sortedByDescending { it.createdAt }

    /** tried (status != 궁금함) vs curious, each sorted. */
    fun splitByStatus(items: List<MiscItem>, favoritable: Boolean): Pair<List<MiscItem>, List<MiscItem>> =
        sortItems(items.filter { it.status != MiscStatus.CURIOUS }, favoritable) to sortItems(items.filter { it.status == MiscStatus.CURIOUS }, favoritable)

    fun matchesSearch(item: MiscItem, query: String): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || "${item.name} ${item.notes}".lowercase().contains(q)
    }

    /** The roastery map's TalkBack label: how many roasteries are pinned, and where the rest are. */
    fun roasteryMapDescription(domestic: Boolean, model: RoasteryMapModel): String {
        val title = if (domestic) "한국 로스터리 지도" else "해외 로스터리 지도"
        val pinned = if (model.pins.isEmpty()) "표시된 로스터리가 없어요" else "로스터리 ${model.pins.size}곳 표시"
        val unlocated = if (model.unlocated.isEmpty()) "" else ", 위치 미입력 ${model.unlocated.size}곳"
        val unmatched = if (model.unmatched.isEmpty()) "" else ", 지도에서 찾지 못한 곳 ${model.unmatched.size}곳"
        val how = if (domestic) " 시·도를 누르면 시·군·구 지도로 확대돼요." else ""
        return "$title. $pinned$unlocated$unmatched.$how 전체 목록은 지도 아래에 있어요."
    }

    /**
     * Web renderRoasteryMap: scoped roasteries sorted by record count. A roastery with a position is pinned there;
     * otherwise its location text is looked up (국내: 시·군·구, else 시·도, on the SGIS map; 해외: the country) and the
     * pin goes to that area's centre. Pins at the same spot are fanned out by the map, not here.
     */
    fun roasteryMap(items: List<MiscItem>, records: List<BeanRecord>, scope: String): RoasteryMapModel {
        val scoped = items.filter { it.type == MiscType.SOURCE && it.scope == scope }
            .sortedWith(compareByDescending<MiscItem> { roasteryRecords(records, it.name).size }.thenBy(KoreanOrder) { it.name })
        val domestic = scope == Scope.DOMESTIC
        val pins = ArrayList<RoasteryPin>()
        val unmatched = ArrayList<MiscItem>()
        val unlocated = ArrayList<MiscItem>()
        scoped.forEach { m ->
            val count = roasteryRecords(records, m.name).size
            val pin = if (domestic) domesticPin(m, count) else overseasPin(m, count)
            when {
                pin != null -> pins += pin
                m.location.isBlank() -> unlocated += m
                else -> unmatched += m
            }
        }
        return RoasteryMapModel(scoped, pins, unlocated, unmatched, scoped.sumOf { roasteryRecords(records, it.name).size })
    }

    private fun domesticPin(m: MiscItem, count: Int): RoasteryPin? {
        val p = m.point?.takeIf { KoreaProjection.inKoreaBox(it) }
        if (p != null) {
            val region = KoreaRegions.locate(p.lat, p.lng, snapKm = 3.0)
            return RoasteryPin(m, KoreaProjection.toMap(p), exact = true, count, region?.label ?: m.location.trim(), region?.provinceCode)
        }
        val region = m.location.takeIf { it.isNotBlank() }?.let { KoreaRegions.matchText(it) } ?: return null
        return RoasteryPin(m, region.center, exact = false, count, region.label, region.provinceCode)
    }

    private fun overseasPin(m: MiscItem, count: Int): RoasteryPin? {
        val p = m.point
        if (p != null) {
            val at = WorldProjection.toView(p)
            val country = WorldPlaces.countryAt(at)
            return RoasteryPin(m, at, exact = true, count, country?.let { WorldPlaces.displayName(it) } ?: m.location.trim(), country)
        }
        val place = m.location.takeIf { it.isNotBlank() }?.let { WorldPlaces.match(it) } ?: return null
        return RoasteryPin(m, place.center, exact = false, count, place.name, place.mapName)
    }
}
