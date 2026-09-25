package com.coffeejournal.ui.bean.a

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.reference.ScoreTiers
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.domain.rules.RoastFamily

/** Pure filtering behind the 배전도 view (web renderRoastFamilyView). */
internal object RoastStats {
    const val FILTER_ALL = "all"
    const val FILTER_BLEND = "blend"
    const val SORT_LATEST = "latest"
    const val SORT_OLDEST = "oldest"

    val families: List<String> = listOf(RoastFamily.LIGHT, RoastFamily.MEDIUM, RoastFamily.DARK)

    data class Counts(val light: Int, val medium: Int, val dark: Int, val darkBlend: Int) {
        fun of(family: String): Int = when (family) {
            RoastFamily.LIGHT -> light; RoastFamily.MEDIUM -> medium; RoastFamily.DARK -> dark; else -> 0
        }
    }

    /** Records that carry a recognisable roast level. */
    fun withFamily(records: List<BeanRecord>): List<BeanRecord> = records.filter { RoastFamily.of(it.roast).isNotEmpty() }

    fun counts(records: List<BeanRecord>): Counts {
        val all = withFamily(records)
        val dark = all.filter { RoastFamily.of(it.roast) == RoastFamily.DARK }
        return Counts(
            light = all.count { RoastFamily.of(it.roast) == RoastFamily.LIGHT },
            medium = all.count { RoastFamily.of(it.roast) == RoastFamily.MEDIUM },
            dark = dark.size,
            darkBlend = dark.count { BeanRecords.isBlend(it) },
        )
    }

    /** Segment label with the count appended when non-zero (web "라이트계 · 3"). */
    fun label(base: String, count: Int): String = if (count > 0) "$base · $count" else base

    fun dedupeKey(record: BeanRecord): String = "${record.parentEntryId ?: record.entryId}|${record.name}|${record.roast}"

    /** Family + optional 블렌드 filter, deduplicated per session/name/roast, sorted by date. */
    fun list(records: List<BeanRecord>, family: String, darkFilter: String, sort: String): List<BeanRecord> {
        val filtered = withFamily(records).filter { r ->
            RoastFamily.of(r.roast) == family && (family != RoastFamily.DARK || darkFilter == FILTER_ALL || BeanRecords.isBlend(r))
        }
        val unique = LinkedHashMap<String, BeanRecord>()
        filtered.forEach { unique[dedupeKey(it)] = it }
        val list = unique.values.toList()
        return if (sort == SORT_OLDEST) list.sortedBy { it.createdAt } else list.sortedByDescending { it.createdAt }
    }
}

/** Pure filtering behind the ✦ Competition Lots view (web renderSpecialtyList). */
internal object CompetitionStats {
    const val MIN_SCORE = 80.0

    data class Lot(val record: BeanRecord, val score: Double) {
        val tier: String get() = ScoreTiers.label(score) ?: ""
    }

    fun score(record: BeanRecord): Double? = record.score.trim().toDoubleOrNull()

    /** Records scoring 80+, one per bean (the oldest record wins), highest score first. */
    fun lots(records: List<BeanRecord>): List<Lot> {
        val byBean = LinkedHashMap<String, Lot>()
        records.forEach { r ->
            val s = score(r) ?: return@forEach
            if (s < MIN_SCORE) return@forEach
            val key = BeanFormat.beanKey(r)
            val existing = byBean[key]
            if (existing == null || r.createdAt < existing.record.createdAt) byBean[key] = Lot(r, s)
        }
        return byBean.values.sortedByDescending { it.score }
    }

    /** "88점 · 상당히 뛰어난 마이크로랏급" using the score exactly as typed. */
    fun badge(lot: Lot): String = "${lot.record.score.trim()}점 · ${lot.tier}"
}
