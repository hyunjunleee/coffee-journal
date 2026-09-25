package com.coffeejournal.data.backup

import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.MyRecipeRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.rules.Dates
import kotlinx.coroutines.Dispatchers
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

    suspend fun import(s: BackupSnapshot, mode: ImportMode): ImportResult {
        val replace = mode == ImportMode.REPLACE
        val lines = mutableListOf<ImportLine>()
        var photoOk = 0
        var photoFail = 0

        suspend fun savePhoto(blob: PhotoBlob): String? =
            runCatching { photos.save(blob.bytes) }.onSuccess { photoOk++ }.onFailure { photoFail++ }.getOrNull()

        // ── entries (id-based upsert; photos attached as files) ──
        if (BackupKeys.ENTRIES in s.present) {
            var ok = 0
            var fail = 0
            var detail: String? = null
            try {
                val existingById = entries.getAll().associateBy { it.id }
                if (replace) existingById.values.forEach { entries.delete(it.id) }
                for (en in s.entries) {
                    try {
                        val existing = if (replace) null else existingById[en.id]
                        val bagBlobs = s.photosFor(en.id, PhotoKind.BAG)
                        val groundsBlob = s.photosFor(en.id, PhotoKind.GROUNDS).firstOrNull()
                        val savedBag = bagBlobs.mapNotNull { savePhoto(it) }
                        val savedGrounds = groundsBlob?.let { savePhoto(it) }
                        val bag = if (bagBlobs.isNotEmpty()) savedBag else existing?.bagPhotos ?: emptyList()
                        val grounds = if (groundsBlob != null) savedGrounds else existing?.groundsPhoto
                        entries.upsert(en.copy(bagPhotos = bag, groundsPhoto = grounds))
                        existing?.bagPhotos?.filter { it !in bag }?.forEach { photos.delete(it) }
                        existing?.groundsPhoto?.takeIf { it != grounds }?.let { photos.delete(it) }
                        ok++
                    } catch (e: Exception) {
                        fail++
                        detail = e.message
                    }
                }
            } catch (e: Exception) {
                fail += s.entries.size - ok - fail
                detail = e.message
            }
            if (s.entries.isNotEmpty() || replace) lines += ImportLine(BackupKeys.ENTRIES, ok, fail, detail = detail?.takeIf { fail > 0 })
        } else lines += ImportLine(BackupKeys.ENTRIES, skipped = true)

        // ── misc items (photos are inline in the web format, so absent photos mean none) ──
        lines += restore(BackupKeys.MISC, s.miscItems, BackupKeys.MISC in s.present, replace,
            clear = { misc.getAll().forEach { misc.delete(it.id) } },
            put = { items ->
                val existingById = if (replace) emptyMap() else misc.getAll().associateBy { it.id }
                val prepared = items.map { m -> attachMiscPhotos(m, s, existingById[m.id], ::savePhoto) }
                misc.upsertAll(prepared)
            })
        lines += restore(BackupKeys.BLENDS, s.blends, BackupKeys.BLENDS in s.present, replace,
            clear = { blends.getAll().forEach { blends.delete(it.id) } }, put = { blends.upsertAll(it) })
        lines += restore(BackupKeys.CLASSES, s.classes, BackupKeys.CLASSES in s.present, replace,
            clear = { study.getClasses().forEach { study.deleteClass(it.id) } }, put = { study.upsertClasses(it) })
        lines += when {
            BackupKeys.ROADMAP !in s.present -> ImportLine(BackupKeys.ROADMAP, skipped = true)
            s.roadmap.isEmpty() -> ImportLine(BackupKeys.ROADMAP, skipped = true, detail = "비어 있음")
            else -> runCatching { roadmap.replaceAll(s.roadmap) }
                .fold({ ImportLine(BackupKeys.ROADMAP, ok = s.roadmap.size) }, { ImportLine(BackupKeys.ROADMAP, failed = s.roadmap.size, detail = it.message) })
        }
        lines += restore(BackupKeys.MY_RECIPES, s.myRecipes, BackupKeys.MY_RECIPES in s.present, replace,
            clear = { myRecipes.getAll().forEach { myRecipes.delete(it.id) } }, put = { myRecipes.upsertAll(it) })
        lines += restore(BackupKeys.BOOKS, s.books, BackupKeys.BOOKS in s.present, replace,
            clear = { study.getBooks().forEach { study.deleteBook(it.id) } }, put = { study.upsertBooks(it) })
        lines += restore(BackupKeys.SUMMARIES, s.beanSummaries, BackupKeys.SUMMARIES in s.present, replace,
            clear = { beanMeta.getSummaries().forEach { beanMeta.putSummary(it.beanKey, "") } }, put = { beanMeta.upsertSummaries(it) })
        lines += restore(BackupKeys.BEST, s.bestRecipes, BackupKeys.BEST in s.present, replace,
            clear = { beanMeta.getBest().forEach { beanMeta.setBest(it.beanKey, null) } }, put = { beanMeta.upsertBestAll(it) })
        lines += restore(BackupKeys.VIDEOS, s.videos, BackupKeys.VIDEOS in s.present, replace,
            clear = { study.getVideos().forEach { study.deleteVideo(it.id) } }, put = { study.upsertVideos(it) })
        lines += restore(BackupKeys.PANTRY, s.pantryItems, BackupKeys.PANTRY in s.present, replace,
            clear = { pantry.getAll().forEach { pantry.delete(it.id) } }, put = { pantry.upsertAll(it) })
        if (BackupKeys.SETTINGS in s.present && s.settings.isNotEmpty()) {
            lines += runCatching { s.settings.forEach { (k, v) -> settings.put(k, v) } }
                .fold({ ImportLine(BackupKeys.SETTINGS, ok = s.settings.size) }, { ImportLine(BackupKeys.SETTINGS, failed = s.settings.size, detail = it.message) })
        }
        // ── D-day start (web rawData) ──
        val dday = s.ddayStart?.let { Dates.parseIsoDate(it) }
        lines += when {
            dday != null -> runCatching { settings.setDdayStart(dday) }
                .fold({ ImportLine(BackupKeys.DDAY, ok = 1, detail = Dates.isoDate(dday)) }, { ImportLine(BackupKeys.DDAY, failed = 1, detail = it.message) })
            BackupKeys.DDAY in s.present && !s.ddayStart.isNullOrBlank() -> ImportLine(BackupKeys.DDAY, failed = 1, detail = "날짜 형식이 아니에요: ${s.ddayStart}")
            else -> ImportLine(BackupKeys.DDAY, skipped = true)
        }
        if (s.photoCount > 0) lines += ImportLine(ImportLine.PHOTOS, ok = photoOk, failed = photoFail + (s.photoCount - photoOk - photoFail).coerceAtLeast(0))
        return ImportResult(lines)
    }

    private suspend fun attachMiscPhotos(m: MiscItem, s: BackupSnapshot, existing: MiscItem?, save: suspend (PhotoBlob) -> String?): MiscItem {
        val names = s.miscPhotosFor(m.id).mapNotNull { save(it) }
        existing?.photos?.filter { it !in names }?.forEach { photos.delete(it) }
        return m.copy(photos = names)
    }

    private suspend fun <T> restore(
        label: String,
        items: List<T>,
        present: Boolean,
        replace: Boolean,
        clear: suspend () -> Unit,
        put: suspend (List<T>) -> Unit,
    ): ImportLine {
        if (!present) return ImportLine(label, skipped = true)
        return try {
            if (replace) clear()
            put(items)
            ImportLine(label, ok = items.size)
        } catch (e: Exception) {
            ImportLine(label, failed = items.size, detail = e.message)
        }
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
