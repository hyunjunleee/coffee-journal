package com.coffeejournal.data.backup

import com.coffeejournal.data.db.TransactionRunner
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.MyRecipeRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SaveEntryPipeline
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.rules.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class ImportMode { MERGE, REPLACE }

data class ExportResult(
    val json: String,
    val fileName: String,
    val rows: List<Pair<String, String>>,
    val photoCount: Int,
    val totalBytes: Long,
)

data class ImportLine(val label: String, val ok: Int = 0, val failed: Int = 0, val skipped: Boolean = false, val detail: String? = null) {
    val isOk: Boolean get() = !skipped && failed == 0

    /** Web wording: "entries: ✓ 3개 복원됨, ✗ 1개 실패" / "ddayStart: 건너뜀 (백업에 없음)". */
    fun text(): String = when {
        skipped -> "$label: 건너뜀 (${detail ?: "백업에 없음"})"
        label == PHOTOS -> "$label: ✓ ${ok}장 복원됨" + (if (failed > 0) ", ✗ ${failed}장 실패" else "")
        failed > 0 && ok == 0 -> "$label: ✗ 실패" + (detail?.let { " ($it)" } ?: "")
        failed > 0 -> "$label: ✓ ${ok}개 복원됨, ✗ ${failed}개 실패"
        detail != null -> "$label: ✓ 복원됨 ($detail)"
        else -> "$label: ✓ ${ok}개 복원됨"
    }

    companion object { const val PHOTOS = "사진" }
}

data class ImportResult(val lines: List<ImportLine>) {
    val allOk: Boolean get() = lines.all { it.failed == 0 }
}

/** Collects every repository into a [BackupSnapshot] and writes one back (merge or replace). */
class BackupService(
    private val entries: EntryRepository,
    private val misc: MiscRepository,
    private val blends: BlendRepository,
    private val study: StudyRepository,
    private val myRecipes: MyRecipeRepository,
    private val roadmap: RoadmapRepository,
    private val pantry: PantryRepository,
    private val beanMeta: BeanMetaRepository,
    private val settings: SettingsRepository,
    private val photos: PhotoStore,
    private val codec: BackupCodec,
    private val tx: TransactionRunner,
    private val pipeline: SaveEntryPipeline,
) {
    fun fileName(): String = "커피일지-백업-${Dates.isoDate(Dates.today())}.json"

    suspend fun snapshot(): BackupSnapshot {
        val allEntries = entries.getAll()
        val entryPhotos = mutableListOf<PhotoBlob>()
        for (en in allEntries) {
            en.bagPhotos.forEachIndexed { i, name -> photos.readBytes(name)?.let { entryPhotos += PhotoBlob(en.id, PhotoKind.BAG, i, it) } }
            en.groundsPhoto?.let { name -> photos.readBytes(name)?.let { entryPhotos += PhotoBlob(en.id, PhotoKind.GROUNDS, 0, it) } }
        }
        val miscItems = misc.getAll()
        val miscPhotos = mutableListOf<PhotoBlob>()
        for (m in miscItems) m.photos.forEachIndexed { i, name -> photos.readBytes(name)?.let { miscPhotos += PhotoBlob(m.id, PhotoKind.MISC, i, it) } }
        val allSettings = settings.getAll()
        return BackupSnapshot(
            entries = allEntries,
            photos = entryPhotos,
            miscItems = miscItems,
            miscPhotos = miscPhotos,
            blends = blends.getAll(),
            classes = study.getClasses(),
            roadmap = roadmap.getAll(),
            myRecipes = myRecipes.getAll(),
            books = study.getBooks(),
            videos = study.getVideos(),
            pantryItems = pantry.getAll(),
            beanSummaries = beanMeta.getSummaries(),
            bestRecipes = beanMeta.getBest(),
            settings = allSettings,
            ddayStart = allSettings[SettingsRepository.KEY_DDAY_START],
            exportedAt = BackupDates.iso8601(Dates.nowMillis()),
        )
    }

    suspend fun export(): ExportResult {
        val snap = snapshot()
        val json = withContext(Dispatchers.Default) { codec.encode(snap) }
        val bytes = withContext(Dispatchers.Default) { json.encodeToByteArray().size.toLong() }
        return ExportResult(json = json, fileName = fileName(), rows = snap.countRows(), photoCount = snap.photoCount, totalBytes = bytes)
    }

    /**
     * Restores [s] all-or-nothing (user decision): every photo file is written first, then every database change runs
     * in one transaction, and files that no row refers to any more are deleted only after the commit. If anything
     * fails, the transaction rolls back, the photo files written for this restore are deleted and the error is thrown,
     * so the app keeps exactly the data it had.
     *
     * Records are always merged by id, whichever [mode] is picked: 교체 never deletes a record, it only replaces the
     * other collections. The work is not cancellable (a cancel arriving just after the commit must not delete the
     * files the new rows point to); callers run it in an app-wide scope. [onProgress] reports stored photos (done, total).
     */
    suspend fun import(s: BackupSnapshot, mode: ImportMode, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult =
        withContext(NonCancellable) {
            val written = mutableListOf<String>()
            try {
                val stored = storePhotos(s, written, onProgress)
                val (lines, obsolete) = tx.write { applyAll(s, mode == ImportMode.REPLACE, stored) }
                obsolete.forEach { runCatching { photos.delete(it) } }
                ImportResult(lines)
            } catch (e: Throwable) {
                written.forEach { runCatching { photos.delete(it) } }
                throw e
            }
        }

    /** Photo files stored for a restore, by owner id. */
    private class StoredPhotos(
        val bag: Map<String, List<String>>,
        val grounds: Map<String, String>,
        val misc: Map<String, List<String>>,
        val count: Int,
    )

    /** Writes every photo the restore will reference before any row changes; [written] collects the file names. */
    private suspend fun storePhotos(s: BackupSnapshot, written: MutableList<String>, onProgress: (Int, Int) -> Unit): StoredPhotos {
        val entryIds = if (BackupKeys.ENTRIES in s.present) s.entries.mapTo(LinkedHashSet()) { it.id } else emptySet()
        val miscIds = if (BackupKeys.MISC in s.present) s.miscItems.mapTo(LinkedHashSet()) { it.id } else emptySet()
        val total = entryIds.sumOf { s.photosFor(it, PhotoKind.BAG).size + s.photosFor(it, PhotoKind.GROUNDS).take(1).size } +
            miscIds.sumOf { s.miscPhotosFor(it).size }
        onProgress(0, total)
        suspend fun save(blob: PhotoBlob): String = photos.save(blob.bytes).also { written += it; onProgress(written.size, total) }
        val bag = HashMap<String, List<String>>()
        val grounds = HashMap<String, String>()
        val misc = HashMap<String, List<String>>()
        for (id in entryIds) {
            s.photosFor(id, PhotoKind.BAG).takeIf { it.isNotEmpty() }?.let { blobs -> bag[id] = blobs.map { save(it) } }
            s.photosFor(id, PhotoKind.GROUNDS).firstOrNull()?.let { grounds[id] = save(it) }
        }
        for (id in miscIds) s.miscPhotosFor(id).takeIf { it.isNotEmpty() }?.let { blobs -> misc[id] = blobs.map { save(it) } }
        return StoredPhotos(bag, grounds, misc, written.size)
    }

    /** Every database change of a restore; runs inside one transaction. Returns the result lines and the photo files to delete after commit. */
    private suspend fun applyAll(s: BackupSnapshot, replace: Boolean, stored: StoredPhotos): Pair<List<ImportLine>, List<String>> {
        val lines = mutableListOf<ImportLine>()
        val obsolete = mutableListOf<String>()

        // ── entries: always an id-based upsert (design §4.1); local-only records are kept even for 교체 ──
        if (BackupKeys.ENTRIES in s.present) {
            val existingById = entries.getAll().associateBy { it.id }
            val incoming = s.entries.associateBy { it.id }.values
            for (en in incoming) {
                val existing = existingById[en.id]
                val bag = stored.bag[en.id] ?: existing?.bagPhotos ?: emptyList()
                val grounds = if (en.id in stored.grounds) stored.grounds[en.id] else existing?.groundsPhoto
                entries.upsert(en.copy(bagPhotos = bag, groundsPhoto = grounds))
                existing?.bagPhotos?.filter { it !in bag }?.let { obsolete += it }
                existing?.groundsPhoto?.takeIf { it != grounds }?.let { obsolete += it }
            }
            if (incoming.isNotEmpty()) lines += ImportLine(BackupKeys.ENTRIES, ok = incoming.size)
        } else lines += ImportLine(BackupKeys.ENTRIES, skipped = true)

        // ── misc items: 교체 swaps the whole list; 병합 matches by id, then by type + name ──
        lines += collection(BackupKeys.MISC, s.miscItems, BackupKeys.MISC in s.present) { items ->
            val local = misc.getAll()
            if (replace) {
                misc.deleteAllRows()
                obsolete += local.flatMap { it.photos }
                misc.upsertAll(items.associateBy { it.id }.values.map { it.copy(photos = stored.misc[it.id].orEmpty()) })
            } else {
                val merged = ImportMerge.misc(local, items, stored.misc)
                obsolete += merged.obsoletePhotos
                misc.upsertAll(merged.rows)
            }
        }
        lines += collection(BackupKeys.BLENDS, s.blends, BackupKeys.BLENDS in s.present) { if (replace) blends.deleteAll(); blends.upsertAll(it) }
        lines += collection(BackupKeys.CLASSES, s.classes, BackupKeys.CLASSES in s.present) { if (replace) study.deleteAllClasses(); study.upsertClasses(it) }
        lines += when {
            BackupKeys.ROADMAP !in s.present -> ImportLine(BackupKeys.ROADMAP, skipped = true)
            s.roadmap.isEmpty() -> ImportLine(BackupKeys.ROADMAP, skipped = true, detail = "비어 있음")
            else -> {
                roadmap.replaceAll(if (replace) s.roadmap else ImportMerge.roadmap(roadmap.getAll(), s.roadmap))
                ImportLine(BackupKeys.ROADMAP, ok = s.roadmap.size)
            }
        }
        lines += collection(BackupKeys.MY_RECIPES, s.myRecipes, BackupKeys.MY_RECIPES in s.present) { if (replace) myRecipes.deleteAll(); myRecipes.upsertAll(it) }
        lines += collection(BackupKeys.BOOKS, s.books, BackupKeys.BOOKS in s.present) { if (replace) study.deleteAllBooks(); study.upsertBooks(it) }
        lines += collection(BackupKeys.SUMMARIES, s.beanSummaries, BackupKeys.SUMMARIES in s.present) { if (replace) beanMeta.deleteAllSummaries(); beanMeta.upsertSummaries(it) }
        lines += collection(BackupKeys.BEST, s.bestRecipes, BackupKeys.BEST in s.present) { if (replace) beanMeta.deleteAllBest(); beanMeta.upsertBestAll(it) }
        lines += collection(BackupKeys.VIDEOS, s.videos, BackupKeys.VIDEOS in s.present) { if (replace) study.deleteAllVideos(); study.upsertVideos(it) }
        lines += collection(BackupKeys.PANTRY, s.pantryItems, BackupKeys.PANTRY in s.present) { if (replace) pantry.deleteAll(); pantry.upsertAll(it) }
        if (BackupKeys.SETTINGS in s.present && s.settings.isNotEmpty()) {
            if (replace) settings.deleteAll()
            s.settings.forEach { (k, v) -> settings.put(k, v) }
            lines += ImportLine(BackupKeys.SETTINGS, ok = s.settings.size)
        }
        // ── D-day start (web rawData) ──
        val dday = s.ddayStart?.let { Dates.parseIsoDate(it) }
        lines += when {
            dday != null -> { settings.setDdayStart(dday); ImportLine(BackupKeys.DDAY, ok = 1, detail = Dates.isoDate(dday)) }
            BackupKeys.DDAY in s.present && !s.ddayStart.isNullOrBlank() -> ImportLine(BackupKeys.DDAY, failed = 1, detail = "날짜 형식이 아니에요: ${s.ddayStart}")
            else -> ImportLine(BackupKeys.DDAY, skipped = true)
        }
        // photos whose record / item is not in the file have nothing to attach to
        if (s.photoCount > 0) lines += ImportLine(ImportLine.PHOTOS, ok = stored.count, failed = (s.photoCount - stored.count).coerceAtLeast(0))

        // ── web load-time backfill: register the roasteries, importers, farms and varieties the records use ──
        if (BackupKeys.ENTRIES in s.present || BackupKeys.MISC in s.present) pipeline.backfill(entries.getAll())
        return lines to obsolete
    }

    private suspend fun <T> collection(label: String, items: List<T>, present: Boolean, apply: suspend (List<T>) -> Unit): ImportLine {
        if (!present) return ImportLine(label, skipped = true)
        apply(items)
        return ImportLine(label, ok = items.size)
    }

    /** Formats bytes like the web panel: "1.25MB" / "312KB". */
    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes >= 1024L * 1024L) {
                val hundredths = (bytes * 100L + 524_288L) / (1024L * 1024L)
                return "${hundredths / 100}.${Dates.pad2((hundredths % 100).toInt())}MB"
            }
            return "${(bytes + 512L) / 1024L}KB"
        }
    }
}
