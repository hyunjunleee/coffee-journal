package com.coffeejournal.data.backup

import com.coffeejournal.data.repo.miscNameKey
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.RoadmapPhase

/** Pure merge rules for a 병합 restore, kept apart from the database code so they can be unit-tested. */
internal object ImportMerge {
    /** Types the web keeps unique by name (addMiscValueIfMissing); equipment may legitimately repeat a name. */
    private val registryTypes = setOf(MiscType.SOURCE, MiscType.SELECTION, MiscType.PROCESS, MiscType.VARIETY, MiscType.FARM)

    class MiscResult(val rows: List<MiscItem>, val obsoletePhotos: List<String>)

    /**
     * 병합 for misc items. A backup item replaces the local item with the same id (the web restore overwrites the
     * whole key, so its photos are the backup's). Otherwise it is matched by type + normalised name, the same key
     * SaveEntryPipeline.registerMany uses, so "커피 리브레" registered in the app and the web backup's "커피 리브레"
     * stay one row: the local row keeps its id and takes the backup's non-blank fields and photos. Registry types are
     * also de-duplicated within the backup itself. [savedPhotos] holds the stored file names per backup item id.
     * A map position (app only) comes from the backup when it has one; otherwise the local row's is kept, so merging
     * a web backup does not take the roasteries off the map.
     */
    fun misc(local: List<MiscItem>, incoming: List<MiscItem>, savedPhotos: Map<String, List<String>>): MiscResult {
        val rows = LinkedHashMap<String, MiscItem>()
        local.forEach { rows[it.id] = it }
        val byName = HashMap<String, String>()
        local.forEach { m -> nameKey(m)?.let { byName.getOrPut(it) { m.id } } }
        val touched = LinkedHashSet<String>()
        val obsolete = mutableListOf<String>()
        for (m in incoming) {
            val photos = savedPhotos[m.id].orEmpty()
            val key = nameKey(m)
            val sameId = rows[m.id]
            val target = sameId ?: key?.let { byName[it] }?.let { rows[it] }
            val row = when {
                // a web backup never carries the app's map position: the local one stays
                sameId != null -> m.copy(photos = photos).withPointOf(sameId)
                target != null -> absorb(target, m, photos)
                else -> m.copy(photos = photos)
            }
            target?.photos?.filter { it !in row.photos }?.let { obsolete += it }
            rows[row.id] = row
            touched += row.id
            if (key != null) {
                if (m.type in registryTypes) byName.getOrPut(key) { row.id }
                else if (byName[key] == row.id) byName.remove(key) // an equipment row is claimed by one backup item only
            }
        }
        return MiscResult(touched.map { rows.getValue(it) }, obsolete)
    }

    private fun nameKey(m: MiscItem): String? = if (m.name.isBlank()) null else miscNameKey(m.type, m.name)

    private fun absorb(target: MiscItem, m: MiscItem, photos: List<String>): MiscItem = target.copy(
        name = m.name.trim().ifBlank { target.name },
        notes = m.notes.ifBlank { target.notes },
        since = m.since.ifBlank { target.since },
        status = m.status.ifBlank { target.status },
        scope = m.scope.ifBlank { target.scope },
        location = m.location.ifBlank { target.location },
        favorite = m.favorite || target.favorite,
        photos = photos.ifEmpty { target.photos },
    ).let { merged -> if (m.point != null) merged.copy(lat = m.lat, lng = m.lng) else merged }

    /** The backup item's position, or [local]'s when the backup has none. */
    private fun MiscItem.withPointOf(local: MiscItem): MiscItem = if (point != null) this else copy(lat = local.lat, lng = local.lng)

    /**
     * 병합 for the roadmap: phases are matched by id and take the backup's title/range/days; their checklist items are
     * the union by id (the backup wins on an id clash, local-only items stay). Local-only phases are kept and new
     * backup phases are appended after the existing ones.
     */
    fun roadmap(local: List<RoadmapPhase>, incoming: List<RoadmapPhase>): List<RoadmapPhase> {
        val out = local.sortedBy { it.position }.toMutableList()
        var next = (out.maxOfOrNull { it.position } ?: -1) + 1
        for (p in incoming.sortedBy { it.position }) {
            val i = out.indexOfFirst { it.id == p.id }
            if (i < 0) {
                out += p.copy(position = next++)
                continue
            }
            val mine = out[i]
            val backupItems = p.items.associateBy { it.id }
            val localIds = mine.items.mapTo(HashSet()) { it.id }
            val items = mine.items.map { backupItems[it.id] ?: it } + p.items.filter { it.id !in localIds }
            out[i] = p.copy(position = mine.position, items = items)
        }
        return out
    }
}
