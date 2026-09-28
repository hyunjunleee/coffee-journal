package com.coffeejournal.data.repo

import com.coffeejournal.data.db.BeanMetaDao
import com.coffeejournal.data.db.BeanSummaryEntity
import com.coffeejournal.data.db.BestRecipeEntity
import com.coffeejournal.data.db.BlendDao
import com.coffeejournal.data.db.BookDao
import com.coffeejournal.data.db.CafePlaceDao
import com.coffeejournal.data.db.ClassDao
import com.coffeejournal.data.db.EntryDao
import com.coffeejournal.data.db.MiscDao
import com.coffeejournal.data.db.MyRecipeDao
import com.coffeejournal.data.db.PantryDao
import com.coffeejournal.data.db.RoadmapDao
import com.coffeejournal.data.db.SettingEntity
import com.coffeejournal.data.db.SettingsDao
import com.coffeejournal.data.db.VideoDao
import com.coffeejournal.data.db.toBeanEntities
import com.coffeejournal.data.db.toDomain
import com.coffeejournal.data.db.toEntity
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.domain.model.BeanSummary
import com.coffeejournal.domain.model.BestRecipe
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.CafePlace
import com.coffeejournal.domain.model.CoffeeClass
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.model.Video
import com.coffeejournal.domain.reference.RoadmapDefaults
import com.coffeejournal.domain.rules.Dates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class EntryRepository(private val dao: EntryDao, private val photos: PhotoStore) {
    fun observeAll(): Flow<List<Entry>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    fun observeCount(): Flow<Int> = dao.observeCount()
    suspend fun getAll(): List<Entry> = dao.getAll().map { it.toDomain() }
    suspend fun getById(id: String): Entry? = dao.getById(id)?.toDomain()

    suspend fun upsert(entry: Entry) = dao.upsertWithBeans(entry.toEntity(), entry.toBeanEntities())

    suspend fun upsertAll(entries: List<Entry>) { entries.forEach { upsert(it) } }

    /** Deletes the record and every photo file it owned. */
    suspend fun delete(id: String) {
        val existing = getById(id)
        dao.deleteById(id)
        existing?.let { en ->
            en.bagPhotos.forEach { photos.delete(it) }
            en.groundsPhoto?.let { photos.delete(it) }
        }
    }
}

class PantryRepository(private val dao: PantryDao) {
    fun observeAll(): Flow<List<PantryItem>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getAll(): List<PantryItem> = dao.getAll().map { it.toDomain() }
    suspend fun getById(id: String): PantryItem? = dao.getById(id)?.toDomain()
    suspend fun upsert(item: PantryItem) = dao.upsert(item.toEntity())
    suspend fun upsertAll(items: List<PantryItem>) = dao.upsertAll(items.map { it.toEntity() })
    suspend fun delete(id: String) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
}

class MiscRepository(private val dao: MiscDao, private val photos: PhotoStore) {
    fun observeAll(): Flow<List<MiscItem>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getAll(): List<MiscItem> = dao.getAll().map { it.toDomain() }
    suspend fun getById(id: String): MiscItem? = dao.getById(id)?.toDomain()
    suspend fun upsert(item: MiscItem) = dao.upsert(item.toEntity())
    suspend fun upsertAll(items: List<MiscItem>) = dao.upsertAll(items.map { it.toEntity() })
    suspend fun delete(id: String) {
        val existing = getById(id)
        dao.deleteById(id)
        existing?.photos?.forEach { photos.delete(it) }
    }

    /** Deletes every row but leaves the photo files to the caller (a restore deletes them only after its commit). */
    suspend fun deleteAllRows() = dao.deleteAll()
}

/** Positions of visited cafés by name (cafe_places); names match trimmed and case-insensitively. */
class CafePlaceRepository(private val dao: CafePlaceDao) {
    fun observeAll(): Flow<List<CafePlace>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getAll(): List<CafePlace> = dao.getAll().map { it.toDomain() }

    suspend fun get(name: String): CafePlace? {
        val key = CafePlace.key(name)
        return if (key.isEmpty()) null else getAll().firstOrNull { CafePlace.key(it.name) == key }
    }

    /** Sets the café's position; an existing row (any spelling of the same name) keeps its name and creation time. */
    suspend fun set(name: String, point: GeoPoint) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val existing = get(trimmed)
        dao.upsert(CafePlace(existing?.name ?: trimmed, point.lat, point.lng, existing?.createdAt ?: Dates.nowMillis()).toEntity())
    }

    suspend fun clear(name: String) {
        get(name)?.let { dao.delete(it.name) }
    }

    /** 병합 restore: each backup row replaces the local row of the same name (matched like [get]). */
    suspend fun mergeAll(items: List<CafePlace>) {
        val local = getAll().associateBy { CafePlace.key(it.name) }
        items.forEach { p ->
            val mine = local[CafePlace.key(p.name)]
            if (mine != null && mine.name != p.name) dao.delete(mine.name)
            dao.upsert(p.copy(name = p.name.trim()).toEntity())
        }
    }

    suspend fun deleteAll() = dao.deleteAll()
}

class StudyRepository(private val books: BookDao, private val videos: VideoDao, private val classes: ClassDao) {
    fun observeBooks(): Flow<List<Book>> = books.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getBooks(): List<Book> = books.getAll().map { it.toDomain() }
    suspend fun getBook(id: String): Book? = books.getById(id)?.toDomain()
    suspend fun upsertBook(item: Book) = books.upsert(item.toEntity())
    suspend fun upsertBooks(items: List<Book>) = books.upsertAll(items.map { it.toEntity() })
    suspend fun deleteBook(id: String) = books.deleteById(id)
    suspend fun deleteAllBooks() = books.deleteAll()

    fun observeVideos(): Flow<List<Video>> = videos.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getVideos(): List<Video> = videos.getAll().map { it.toDomain() }
    suspend fun getVideo(id: String): Video? = videos.getById(id)?.toDomain()
    suspend fun upsertVideo(item: Video) = videos.upsert(item.toEntity())
    suspend fun upsertVideos(items: List<Video>) = videos.upsertAll(items.map { it.toEntity() })
    suspend fun deleteVideo(id: String) = videos.deleteById(id)
    suspend fun deleteAllVideos() = videos.deleteAll()

    fun observeClasses(): Flow<List<CoffeeClass>> = classes.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getClasses(): List<CoffeeClass> = classes.getAll().map { it.toDomain() }
    suspend fun getClass(id: String): CoffeeClass? = classes.getById(id)?.toDomain()
    suspend fun upsertClass(item: CoffeeClass) = classes.upsert(item.toEntity())
    suspend fun upsertClasses(items: List<CoffeeClass>) = classes.upsertAll(items.map { it.toEntity() })
    suspend fun deleteClass(id: String) = classes.deleteById(id)
    suspend fun deleteAllClasses() = classes.deleteAll()
}

class BlendRepository(private val dao: BlendDao) {
    fun observeAll(): Flow<List<Blend>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getAll(): List<Blend> = dao.getAll().map { it.toDomain() }
    suspend fun getById(id: String): Blend? = dao.getById(id)?.toDomain()
    suspend fun upsert(item: Blend) = dao.upsert(item.toEntity())
    suspend fun upsertAll(items: List<Blend>) = dao.upsertAll(items.map { it.toEntity() })
    suspend fun delete(id: String) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
}

class MyRecipeRepository(private val dao: MyRecipeDao) {
    fun observeAll(): Flow<List<MyRecipe>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getAll(): List<MyRecipe> = dao.getAll().map { it.toDomain() }
    suspend fun getById(id: String): MyRecipe? = dao.getById(id)?.toDomain()
    suspend fun upsert(item: MyRecipe) = dao.upsert(item.toEntity())
    suspend fun upsertAll(items: List<MyRecipe>) = dao.upsertAll(items.map { it.toEntity() })
    suspend fun delete(id: String) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
}

class RoadmapRepository(private val dao: RoadmapDao) {
    fun observeAll(): Flow<List<RoadmapPhase>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getAll(): List<RoadmapPhase> = dao.getAll().map { it.toDomain() }
    suspend fun upsert(phase: RoadmapPhase) = dao.upsert(phase.toEntity())
    suspend fun replaceAll(phases: List<RoadmapPhase>) { dao.deleteAll(); dao.upsertAll(phases.map { it.toEntity() }) }

    /** Seeds the single starter phase the web app ships with. */
    suspend fun ensureSeeded() {
        if (dao.count() > 0) return
        dao.upsert(
            RoadmapPhase(
                id = RoadmapDefaults.STARTER_ID, position = 0, title = RoadmapDefaults.STARTER_TITLE,
                range = RoadmapDefaults.STARTER_RANGE, dayStart = RoadmapDefaults.STARTER_DAY_START,
                dayEnd = RoadmapDefaults.STARTER_DAY_END,
            ).toEntity()
        )
    }
}

class BeanMetaRepository(private val dao: BeanMetaDao) {
    fun observeSummaries(): Flow<Map<String, BeanSummary>> = dao.observeSummaries().map { l -> l.associate { it.beanKey to it.toDomain() } }
    suspend fun getSummaries(): List<BeanSummary> = dao.getSummaries().map { it.toDomain() }
    suspend fun putSummary(beanKey: String, text: String) {
        if (text.isBlank()) dao.deleteSummary(beanKey) else dao.upsertSummary(BeanSummaryEntity(beanKey, text.trim(), Dates.nowMillis()))
    }
    suspend fun upsertSummaries(items: List<BeanSummary>) = dao.upsertSummaries(items.map { it.toEntity() })
    suspend fun deleteAllSummaries() = dao.deleteAllSummaries()

    fun observeBest(): Flow<Map<String, String>> = dao.observeBest().map { l -> l.associate { it.beanKey to it.entryId } }
    suspend fun getBest(): List<BestRecipe> = dao.getBest().map { it.toDomain() }
    suspend fun setBest(beanKey: String, entryId: String?) {
        if (entryId == null) dao.deleteBest(beanKey) else dao.upsertBest(BestRecipeEntity(beanKey, entryId))
    }
    suspend fun upsertBestAll(items: List<BestRecipe>) = dao.upsertBestAll(items.map { it.toEntity() })
    suspend fun deleteAllBest() = dao.deleteAllBest()
}

class SettingsRepository(private val dao: SettingsDao) {
    fun observe(key: String): Flow<String?> = dao.observe(key)
    suspend fun get(key: String): String? = dao.get(key)
    suspend fun put(key: String, value: String) = dao.put(SettingEntity(key, value))
    suspend fun delete(key: String) = dao.delete(key)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun getAll(): Map<String, String> = dao.getAll().associate { it.key to it.value }

    fun observeDdayStart(): Flow<LocalDate?> = observe(KEY_DDAY_START).map { Dates.parseIsoDate(it) }
    suspend fun setDdayStart(date: LocalDate) = put(KEY_DDAY_START, Dates.isoDate(date))

    /** Deletes every setting a backup carries; this device's own settings ([isDeviceKey]) stay. */
    suspend fun deleteAllBackedUp() {
        dao.getAll().filterNot { isDeviceKey(it.key) }.forEach { dao.delete(it.key) }
    }

    companion object {
        const val KEY_DDAY_START = "brew-start-date"

        /**
         * Settings that belong to this phone rather than to the journal (notification switches, the reminder time,
         * the reminders already sent, the record form's unsaved draft): a backup neither writes nor restores them.
         */
        const val DEVICE_PREFIX = "device."

        fun isDeviceKey(key: String): Boolean = key.startsWith(DEVICE_PREFIX)
    }
}
