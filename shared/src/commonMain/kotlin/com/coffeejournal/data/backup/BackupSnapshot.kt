package com.coffeejournal.data.backup

import com.coffeejournal.domain.model.BeanSummary
import com.coffeejournal.domain.model.BestRecipe
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.CoffeeClass
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.model.Video

/** Which slot a backed-up image belongs to: bag photos (up to two), the grounds photo, or an equipment photo. */
enum class PhotoKind { BAG, GROUNDS, MISC }

/** Raw image bytes attached to a record (`ownerId` = entry id) or an equipment item (`ownerId` = misc item id). */
class PhotoBlob(val ownerId: String, val kind: PhotoKind, val index: Int, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is PhotoBlob && other.ownerId == ownerId && other.kind == kind && other.index == index && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = ((ownerId.hashCode() * 31 + kind.hashCode()) * 31 + index) * 31 + bytes.contentHashCode()

    override fun toString(): String = "PhotoBlob($ownerId, $kind, #$index, ${bytes.size}B)"
}

/** Everything a backup file carries, as domain objects plus decoded image bytes. */
data class BackupSnapshot(
    val entries: List<Entry> = emptyList(),
    val photos: List<PhotoBlob> = emptyList(),
    val miscItems: List<MiscItem> = emptyList(),
    val miscPhotos: List<PhotoBlob> = emptyList(),
    val blends: List<Blend> = emptyList(),
    val classes: List<CoffeeClass> = emptyList(),
    val roadmap: List<RoadmapPhase> = emptyList(),
    val myRecipes: List<MyRecipe> = emptyList(),
    val books: List<Book> = emptyList(),
    val videos: List<Video> = emptyList(),
    val pantryItems: List<PantryItem> = emptyList(),
    val beanSummaries: List<BeanSummary> = emptyList(),
    val bestRecipes: List<BestRecipe> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val ddayStart: String? = null,
    val exportedAt: String? = null,
    /** Collections the source file actually contained (the web writes `null` for keys it never stored). */
    val present: Set<String> = BackupKeys.all,
) {
    fun photosFor(entryId: String, kind: PhotoKind): List<PhotoBlob> =
        photos.filter { it.ownerId == entryId && it.kind == kind }.sortedBy { it.index }

    fun miscPhotosFor(itemId: String): List<PhotoBlob> = miscPhotos.filter { it.ownerId == itemId }.sortedBy { it.index }

    /** Number of images (a record with two bag photos counts twice). */
    val photoCount: Int get() = photos.size + miscPhotos.size

    /** Label/value rows in the web result-panel order. */
    fun countRows(): List<Pair<String, String>> = listOf(
        BackupKeys.ENTRIES to entries.size.toString(),
        BackupKeys.MISC to miscItems.size.toString(),
        BackupKeys.BLENDS to blends.size.toString(),
        BackupKeys.CLASSES to classes.size.toString(),
        BackupKeys.ROADMAP to roadmap.size.toString(),
        BackupKeys.MY_RECIPES to myRecipes.size.toString(),
        BackupKeys.BOOKS to books.size.toString(),
        BackupKeys.VIDEOS to videos.size.toString(),
        BackupKeys.PANTRY to pantryItems.size.toString(),
        BackupKeys.SUMMARIES to beanSummaries.size.toString(),
        BackupKeys.BEST to bestRecipes.size.toString(),
        BackupKeys.DDAY to (ddayStart?.takeIf { it.isNotBlank() } ?: "없음"),
        BackupKeys.PHOTOS_LABEL to photoCount.toString(),
    )

    /** One-line summary for the restore confirmation (web: "entries: 3개, miscItems: 2개, …"). */
    fun summaryLine(): String = countRows().joinToString(", ") { (label, value) ->
        when (label) {
            BackupKeys.DDAY -> "$label: $value"
            BackupKeys.PHOTOS_LABEL -> "사진: ${value}장"
            else -> "$label: ${value}개"
        }
    }
}

object BackupKeys {
    const val ENTRIES = "entries"
    const val MISC = "miscItems"
    const val BLENDS = "blends"
    const val CLASSES = "classes"
    const val ROADMAP = "roadmapData"
    const val MY_RECIPES = "myRecipes"
    const val BOOKS = "books"
    const val SUMMARIES = "beanSummaries"
    const val BEST = "bestRecipes"
    const val VIDEOS = "videos"
    const val PANTRY = "pantryItems"
    const val SETTINGS = "settings"
    const val DDAY = "ddayStart"
    const val PHOTOS_LABEL = "photos (사진)"
    val all: Set<String> = setOf(ENTRIES, MISC, BLENDS, CLASSES, ROADMAP, MY_RECIPES, BOOKS, SUMMARIES, BEST, VIDEOS, PANTRY, SETTINGS, DDAY)
}
